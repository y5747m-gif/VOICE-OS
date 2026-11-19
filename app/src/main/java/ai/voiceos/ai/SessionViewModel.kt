package ai.voiceos.ai

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ai.voiceos.VoiceOsApplication
import ai.voiceos.actions.AndroidActions
import ai.voiceos.core.ActionResult
import ai.voiceos.core.CommandEngine
import ai.voiceos.core.CommandResult
import ai.voiceos.core.DeviceAction
import ai.voiceos.core.LocalInterpreter
import ai.voiceos.data.InputSource
import ai.voiceos.data.StoredAction
import ai.voiceos.memory.NicknameStore
import ai.voiceos.permissions.MicrophonePermission
import ai.voiceos.settings.RecognitionMode
import ai.voiceos.settings.UserSettings
import ai.voiceos.settings.VoiceLanguage
import ai.voiceos.settings.localized
import ai.voiceos.voice.AndroidRecognizer
import ai.voiceos.voice.AndroidSpeaker
import ai.voiceos.voice.Recognition
import ai.voiceos.voice.SpeechUpdate
import ai.voiceos.voice.VoiceIssue
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield

/** Owns a single foreground operation. Raw utterances live only in this ViewModel. */
internal class SessionViewModel(private val app: VoiceOsApplication) : ViewModel() {
    val settings = app.settings.state
    private val actions = AndroidActions()
    private val engine = CommandEngine(LocalInterpreter(), actions)
    private val recognizer = AndroidRecognizer(app)
    private val speaker = AndroidSpeaker(app, settings.value.language)
    val audio = speaker.state

    private val mutableSession = MutableStateFlow(Session())
    val session = mutableSession.asStateFlow()
    private val mutableMicrophone = MutableStateFlow(MicrophonePermission.granted(app))
    val microphone = mutableMicrophone.asStateFlow()
    private val mutableVoiceAvailability = MutableStateFlow(recognizer.availability(settings.value.recognition))
    val voiceAvailability = mutableVoiceAvailability.asStateFlow()
    private val mutableMemory = MutableStateFlow(MemoryState())
    val memory = mutableMemory.asStateFlow()

    private var isForeground = false
    private var ticket = 0L
    private var active: Job? = null

    private val recent = MutableStateFlow<List<HistoryEvent>>(emptyList())
    private val persisted = MutableStateFlow<List<HistoryEvent>>(emptyList())
    private val historyProblem = MutableStateFlow(false)
    private val historyClearing = MutableStateFlow(false)
    private val historyMutex = Mutex()
    private var historyEpoch = 0L
    private var historyObserver: Job? = null
    val history = combine(recent, persisted, historyProblem, historyClearing) { runtime, disk, problem, clearing ->
        HistoryState(
            (runtime + disk).distinctBy { it.id }.sortedWith(compareByDescending<HistoryEvent> { it.at }.thenByDescending { it.id })
                .take(StoredAction.LIMIT),
            problem, clearing,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HistoryState())
    val conversation = recent.asStateFlow()

    init {
        observeHistory()
        viewModelScope.launch {
            mutableMemory.value = app.memory.read().fold(
                onSuccess = { MemoryState(name = it, busy = false) },
                onFailure = { MemoryState(busy = false, problem = true) },
            )
        }
    }

    fun enterForeground(activity: Activity) {
        actions.attach(activity)
        isForeground = true
        refreshPermission()
        refreshVoice()
        speaker.refresh()
    }

    fun leaveForeground(activity: Activity) {
        if (!actions.detach(activity)) return
        isForeground = false
        if (session.value.busy) cancelWork()
        // A completed, bounded TTS reply may finish after a handoff; a recognizer may not.
    }

    fun refreshPermission() { mutableMicrophone.value = MicrophonePermission.granted(app) }
    private fun refreshVoice() { mutableVoiceAvailability.value = recognizer.availability(settings.value.recognition) }

    fun permissionDenied() {
        refreshPermission()
        if (!session.value.busy) finishVoiceError(VoiceIssue.PERMISSION)
    }

    fun startVoice() {
        if (!canStart()) return
        val config = settings.value
        refreshVoice()
        val issue = voiceAvailability.value ?: if (MicrophonePermission.granted(app)) null else VoiceIssue.PERMISSION
        if (issue != null) { finishVoiceError(issue); return }
        begin(InputSource.VOICE, Phase.LISTENING) { id ->
            when (val final = recognizer.listen(config.language, config.recognition) { event ->
                if (id == ticket) when (event) {
                    SpeechUpdate.Ready -> if (session.value.capture == Capture.CONNECTING) {
                        mutableSession.update { it.copy(capture = Capture.WAITING) }
                    } else Unit
                    SpeechUpdate.Beginning -> if (session.value.phase == Phase.LISTENING) {
                        mutableSession.update { it.copy(capture = Capture.SPEECH) }
                    } else Unit
                    SpeechUpdate.EndOfSpeech -> mutableSession.update { it.copy(phase = Phase.THINKING, level = 0f, capture = null) }
                    is SpeechUpdate.Level -> if (session.value.phase == Phase.LISTENING) {
                        mutableSession.update { it.copy(level = event.value) }
                    } else Unit
                    is SpeechUpdate.Partial -> mutableSession.update { it.copy(transcript = display(event.text)) }
                }
            }) {
                is Recognition.Failed -> if (id == ticket) finishVoiceError(final.cause)
                is Recognition.Final -> {
                    if (id != ticket) return@begin
                    mutableSession.update { it.copy(phase = Phase.THINKING, transcript = display(final.text), level = 0f, transcriptFinal = true, capture = null) }
                    process(id) { plan -> engine.submit(final.text, plan) }
                }
            }
        }
    }

    fun finishSpeech() {
        if (session.value.phase != Phase.LISTENING) return
        mutableSession.update { it.copy(phase = Phase.THINKING, level = 0f, capture = null) }
        recognizer.finishSpeech()
    }

    fun submit(text: String): Boolean {
        if (!canStart() || text.isBlank()) return false
        begin(InputSource.TEXT, Phase.THINKING, display(text)) { id -> process(id) { plan -> engine.submit(text, plan) } }
        return true
    }

    fun runShortcut(action: DeviceAction) {
        if (!canStart()) return
        begin(InputSource.SHORTCUT, Phase.THINKING) { id -> process(id) { plan -> engine.run(action, plan) } }
    }

    fun stop() {
        if (session.value.busy) cancelWork()
        speaker.stop()
    }

    fun replay(reply: Reply) {
        if (isForeground && !session.value.busy) speaker.speak(app.localized(settings.value.language).describe(reply))
    }

    fun updateSettings(change: (UserSettings) -> UserSettings) {
        val old = settings.value
        app.settings.update(change)
        val new = settings.value
        if (new.language != old.language || new.recognition != old.recognition) {
            if (session.value.busy) cancelWork()
            speaker.setLanguage(new.language)
            refreshVoice()
        }
        if (!new.speakReplies && old.speakReplies) speaker.stop()
        if (!new.keepHistory && old.keepHistory) historyEpoch++ // drop any writes queued before disabling
    }

    fun togglePin(action: DeviceAction) = updateSettings { prefs ->
        when {
            action in prefs.pins -> prefs.copy(pins = prefs.pins - action)
            prefs.pins.size < UserSettings.MAX_PINS -> prefs.copy(pins = prefs.pins + action)
            else -> prefs
        }
    }

    fun saveName(draft: String) {
        val name = draft.trim()
        if (memory.value.busy || !NicknameStore.isValidName(name)) return
        mutableMemory.update { it.copy(busy = true, problem = false) }
        viewModelScope.launch {
            app.memory.save(name).fold(
                onSuccess = { mutableMemory.value = MemoryState(name, busy = false) },
                onFailure = { mutableMemory.update { it.copy(busy = false, problem = true) } },
            )
        }
    }

    fun removeName() {
        if (memory.value.busy) return
        mutableMemory.update { it.copy(busy = true, problem = false) }
        viewModelScope.launch {
            app.memory.remove().fold(
                onSuccess = { mutableMemory.value = MemoryState(busy = false) },
                onFailure = { mutableMemory.update { it.copy(busy = false, problem = true) } },
            )
        }
    }

    fun clearHistory() {
        if (historyClearing.value) return
        historyEpoch++
        historyClearing.value = true
        recent.value = emptyList()
        stop()
        mutableSession.value = Session()
        viewModelScope.launch {
            try {
                historyMutex.withLock { app.history.clear() }
                persisted.value = emptyList()
                historyProblem.value = false
                observeHistory()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: RuntimeException) {
                historyProblem.value = true
            } finally {
                historyClearing.value = false
            }
        }
    }

    override fun onCleared() {
        recognizer.cancel()
        speaker.close()
        super.onCleared()
    }

    private fun canStart() = isForeground && !session.value.busy && !historyClearing.value

    private fun begin(source: InputSource, phase: Phase, transcript: String? = null, work: suspend (Long) -> Unit) {
        speaker.stop()
        val id = ++ticket
        mutableSession.value = Session(phase, source, transcript, transcriptFinal = source == InputSource.TEXT, sequence = id,
            capture = if (source == InputSource.VOICE) Capture.CONNECTING else null)
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try { work(id) } finally { if (id == ticket) active = null }
        }
        active = job
        job.start()
    }

    private suspend fun process(id: Long, execute: suspend ((DeviceAction) -> Unit) -> CommandResult) {
        yield() // cancellation point before any handoff, not a simulated processing delay
        if (id != ticket || !isForeground) return
        val report = execute { action -> mutableSession.update { it.copy(phase = Phase.EXECUTING, action = action) } }
        if (id != ticket) return
        val reply = Reply.Command(report)
        val phase = if (report is CommandResult.Executed && report.result !is ActionResult.Failed) Phase.COMPLETED else Phase.ERROR
        mutableSession.update { it.copy(phase = phase, reply = reply, level = 0f) }
        session.value.source?.let { record(it, report) }
        if (settings.value.speakReplies) speaker.speak(app.localized(settings.value.language).describe(reply))
    }

    private fun finishVoiceError(issue: VoiceIssue) {
        speaker.stop()
        mutableSession.value = Session(Phase.ERROR, InputSource.VOICE, reply = Reply.VoiceError(issue), sequence = ticket)
    }

    private fun cancelWork() {
        ticket++
        mutableSession.value = Session(Phase.STOPPED, reply = Reply.Stopped)
        active?.cancel()
        active = null
        recognizer.cancel()
    }

    private fun record(source: InputSource, report: CommandResult) {
        val event = HistoryEvent(UUID.randomUUID().toString(), System.currentTimeMillis(), source, report, session.value.transcript)
        recent.update { (listOf(event) + it).take(StoredAction.LIMIT) }
        if (report !is CommandResult.Executed || !settings.value.keepHistory) return
        val epoch = historyEpoch
        viewModelScope.launch {
            try {
                historyMutex.withLock {
                    if (epoch == historyEpoch && settings.value.keepHistory) {
                        app.history.record(StoredAction.from(event.id, event.at, source, report))
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: RuntimeException) {
                historyProblem.value = true
            }
        }
    }

    private fun observeHistory() {
        historyObserver?.cancel()
        historyObserver = viewModelScope.launch {
            try {
                app.history.observe().collect { rows ->
                    persisted.value = rows.mapNotNull { row ->
                        val source = InputSource.entries.find { it.key == row.sourceKey } ?: return@mapNotNull null
                        row.toResult()?.let { HistoryEvent(row.id, row.occurredAt, source, it) }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: RuntimeException) {
                historyProblem.value = true
            }
        }
    }

    private fun display(text: String) = if (text.length <= LocalInterpreter.MAX_COMMAND_LENGTH) text
        else text.take(LocalInterpreter.MAX_COMMAND_LENGTH) + "…"

    class Factory(private val app: VoiceOsApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == SessionViewModel::class.java)
            return SessionViewModel(app) as T
        }
    }
}

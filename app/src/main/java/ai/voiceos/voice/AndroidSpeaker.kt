package ai.voiceos.voice

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import ai.voiceos.settings.VoiceLanguage
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class SpeakerAvailability { LOADING, EXACT, SAME_LANGUAGE, MISSING_VOICE, ENGINE_ERROR }
internal data class SpeakerState(val availability: SpeakerAvailability = SpeakerAvailability.LOADING, val playing: Boolean = false)

/** Short local spoken replies only. Refuses voices that require network or aren't installed. */
internal class AndroidSpeaker(context: Context, initial: VoiceLanguage) {
    private val main = Handler(Looper.getMainLooper())
    private val mutable = MutableStateFlow(SpeakerState())
    val state = mutable.asStateFlow()
    private var language = initial
    private var initialized = false
    private var closed = false
    private var pending: String? = null
    private var activeId: String? = null
    private var counter = 0L
    private val timeout = Runnable { stop() }
    private val initTimeout = Runnable {
        if (!initialized && !closed) { pending = null; mutable.value = SpeakerState(SpeakerAvailability.ENGINE_ERROR) }
    }
    private val engine: TextToSpeech? = try {
        TextToSpeech(context) { status -> main.post { onInitialized(status) } }
    } catch (_: RuntimeException) {
        mutable.value = SpeakerState(SpeakerAvailability.ENGINE_ERROR)
        null
    }

    init { if (engine != null) main.postDelayed(initTimeout, 8_000L) }

    fun setLanguage(value: VoiceLanguage) {
        if (language != value) stop()
        language = value
        refresh()
    }

    fun refresh() {
        if (!initialized || closed || engine == null) return
        val voice = findVoice(language.locale)
        val availability = when {
            voice == null -> SpeakerAvailability.MISSING_VOICE
            matchesRegion(voice, language.locale) -> SpeakerAvailability.EXACT
            else -> SpeakerAvailability.SAME_LANGUAGE
        }
        mutable.value = mutable.value.copy(availability = availability)
    }

    fun speak(text: String) {
        if (closed || engine == null) return
        stop()
        if (mutable.value.availability == SpeakerAvailability.LOADING) {
            pending = text
            mutable.value = mutable.value.copy(playing = true)
            return
        }
        if (!initialized) return
        val voice = findVoice(language.locale)
        if (voice == null) {
            mutable.value = SpeakerState(SpeakerAvailability.MISSING_VOICE)
            return
        }
        val id = "voice-os-${++counter}"
        activeId = id
        val success = try {
            engine.setVoice(voice) == TextToSpeech.SUCCESS &&
                engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.SUCCESS
        } catch (_: RuntimeException) {
            false
        }
        if (success) {
            mutable.value = mutable.value.copy(playing = true)
            main.postDelayed(timeout, 15_000L)
        } else {
            activeId = null
            mutable.value = SpeakerState(SpeakerAvailability.ENGINE_ERROR)
        }
    }

    fun stop() {
        pending = null
        val wasActive = activeId != null
        activeId = null
        main.removeCallbacks(timeout)
        if (wasActive) try { engine?.stop() } catch (_: RuntimeException) { /* stale engine */ }
        if (mutable.value.playing) mutable.value = mutable.value.copy(playing = false)
    }

    fun close() {
        closed = true
        stop()
        main.removeCallbacks(initTimeout)
        try { engine?.shutdown() } catch (_: RuntimeException) { /* stale engine */ }
    }

    private fun onInitialized(status: Int) {
        if (closed || engine == null) return
        main.removeCallbacks(initTimeout)
        if (status != TextToSpeech.SUCCESS) {
            pending = null
            mutable.value = SpeakerState(SpeakerAvailability.ENGINE_ERROR)
            return
        }
        initialized = try {
            engine.setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build())
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = done(utteranceId, false)
                @Deprecated("Android's legacy callback")
                override fun onError(utteranceId: String?) = done(utteranceId, true)
                override fun onError(utteranceId: String?, errorCode: Int) = done(utteranceId, true)
                override fun onStop(utteranceId: String?, interrupted: Boolean) = done(utteranceId, false)
            }) == TextToSpeech.SUCCESS
        } catch (_: RuntimeException) { false }
        if (!initialized) {
            pending = null
            mutable.value = SpeakerState(SpeakerAvailability.ENGINE_ERROR)
            return
        }
        refresh()
        pending?.let { speak(it) }
    }

    private fun done(id: String?, failed: Boolean) {
        main.post {
            if (id == null || id != activeId || closed) return@post
            activeId = null
            main.removeCallbacks(timeout)
            mutable.value = if (failed) SpeakerState(SpeakerAvailability.ENGINE_ERROR) else mutable.value.copy(playing = false)
        }
    }

    private fun findVoice(locale: Locale): Voice? = try {
        engine?.voices.orEmpty().asSequence()
            .filter { !it.isNetworkConnectionRequired && it.locale.language == locale.language &&
                TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty() }
            .sortedWith(compareByDescending<Voice> { matchesRegion(it, locale) }.thenByDescending { it.quality }.thenBy { it.name })
            .firstOrNull()
    } catch (_: RuntimeException) {
        null
    }

    private fun matchesRegion(voice: Voice, locale: Locale) = voice.locale.country == locale.country
}

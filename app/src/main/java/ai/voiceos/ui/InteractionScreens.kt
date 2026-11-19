package ai.voiceos.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ai.voiceos.R
import ai.voiceos.actions.ActionGroup
import ai.voiceos.actions.copyText
import ai.voiceos.ai.Capture
import ai.voiceos.ai.HistoryEvent
import ai.voiceos.ai.Phase
import ai.voiceos.ai.Reply
import ai.voiceos.ai.Session
import ai.voiceos.ai.describe
import ai.voiceos.ai.messageId
import ai.voiceos.core.ActionResult
import ai.voiceos.core.CommandResult
import ai.voiceos.core.DeviceAction
import ai.voiceos.core.LocalInterpreter
import ai.voiceos.data.InputSource
import ai.voiceos.settings.RecognitionMode
import ai.voiceos.settings.UserSettings
import ai.voiceos.voice.SpeakerAvailability
import ai.voiceos.voice.SpeakerState
import ai.voiceos.voice.VoiceIssue
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import kotlinx.coroutines.delay

@Composable
internal fun HomeScreen(
    session: Session,
    audio: SpeakerState,
    config: UserSettings,
    nickname: String?,
    history: List<HistoryEvent>,
    voiceIssue: VoiceIssue?,
    onMic: () -> Unit,
    onStop: () -> Unit,
    replay: (Reply) -> Unit,
    run: (DeviceAction) -> Unit,
    navigate: (Page) -> Unit,
) = ScrollPage {
    var hour by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
    LaunchedEffect(Unit) {
        while (true) {
            hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            delay(30_000)
        }
    }
    val greeting = stringResource(when (hour) {
        in 5..11 -> R.string.greeting_day
        in 12..16 -> R.string.greeting_afternoon
        else -> R.string.greeting_evening
    })
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Kicker(stringResource(R.string.home_kicker))
        Text(if (nickname == null) greeting else stringResource(R.string.greeting_named, greeting, nickname),
            style = MaterialTheme.typography.displaySmall, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.home_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    VoiceStage(session, audio.playing, config.reduceMotion,
        caption = stringResource(when {
            session.phase == Phase.LISTENING -> session.listeningHint()
            config.recognition == RecognitionMode.SYSTEM -> R.string.home_system_provider
            else -> R.string.home_device_provider
        }), onMic, onStop)
    if (session.visible) SessionReadout(session, audio, replay)
    if (voiceIssue != null) VoiceFallback(voiceIssue, navigate, showAssistant = true, showIssue = session.reply != Reply.VoiceError(voiceIssue))
    else if (session.reply == Reply.VoiceError(VoiceIssue.PERMISSION)) {
        TextButton(onClick = { navigate(Page.PERMISSIONS) }) { Text(stringResource(R.string.nav_permissions)) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        SectionBar(stringResource(R.string.home_shortcuts), stringResource(R.string.edit)) { navigate(Page.AUTOMATIONS) }
        if (config.pins.isEmpty()) {
            EmptyPanel(Glyph.PIN, stringResource(R.string.no_pins), stringResource(R.string.no_pins_detail))
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                config.pins.forEach { action ->
                    Surface(onClick = { run(action) }, modifier = Modifier.weight(1f), enabled = !session.busy, shape = InsetShape,
                        color = LocalVoiceColors.current.panel, border = BorderStroke(1.dp, LocalVoiceColors.current.line)) {
                        Column(Modifier.heightIn(min = 95.dp).padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            TileIcon(action.glyph(), actionColor(action), boxSize = 34.dp)
                            Text(stringResource(action.copyText().title), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionBar(stringResource(R.string.home_activity), stringResource(R.string.view_all)) { navigate(Page.HISTORY) }
        if (history.isEmpty()) EmptyPanel(Glyph.HISTORY, stringResource(R.string.history_empty), stringResource(R.string.history_empty_detail))
        else history.take(2).forEach { HistoryCard(it, compact = true) }
    }
}

@Composable
private fun SectionBar(title: String, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
        TextButton(onClick = onClick) { Text(label) }
    }
}

@Composable
internal fun AssistantScreen(
    session: Session,
    audio: SpeakerState,
    entries: List<HistoryEvent>,
    voiceIssue: VoiceIssue?,
    draft: String,
    edit: (String) -> Unit,
    onSubmit: () -> Boolean,
    onMic: () -> Unit,
    replay: (Reply) -> Unit,
    navigate: (Page) -> Unit,
) {
    val list = rememberLazyListState()
    LaunchedEffect(entries.firstOrNull()?.id, session.sequence) {
        if (session.visible) list.scrollToItem(0)
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
            LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                item("title") {
                    PageHeading(stringResource(R.string.assistant_kicker), stringResource(R.string.assistant_title), stringResource(R.string.assistant_intro))
                }
                item("active") {
                    if (session.visible) SessionReadout(session, audio, replay, showStatus = true)
                    else Examples(edit)
                }
                if (voiceIssue != null) item("voice-help") { VoiceFallback(voiceIssue, navigate, showAssistant = false, showIssue = session.reply != Reply.VoiceError(voiceIssue)) }
                else if (session.reply == Reply.VoiceError(VoiceIssue.PERMISSION)) item("permission-help") {
                    TextButton(onClick = { navigate(Page.PERMISSIONS) }) { Text(stringResource(R.string.nav_permissions)) }
                }
                val prior = entries.drop(if (session.reply is Reply.Command) 1 else 0).take(10)
                if (prior.isNotEmpty()) item("earlier") { SectionTitle(stringResource(R.string.assistant_earlier)) }
                items(prior, key = { it.id }) {
                    ConversationCard(it, audio, if (session.busy) null else replay)
                }
            }
            Composer(session, draft, edit, onSubmit, onMic)
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun Examples(edit: (String) -> Unit) {
    Panel {
        TileIcon(Glyph.WAVE)
        Text(stringResource(R.string.assistant_try), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.assistant_try_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(DeviceAction.BATTERY, DeviceAction.WIFI, DeviceAction.CAMERA).forEach { action ->
                val phrase = stringResource(action.copyText().phrase)
                SuggestionChip(onClick = { edit(phrase) }, label = { Text(phrase) })
            }
        }
    }
}

@Composable
private fun Composer(session: Session, draft: String, edit: (String) -> Unit, onSubmit: () -> Boolean, onMic: () -> Unit) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val listening = session.phase == Phase.LISTENING
    val tooLong = draft.length > LocalInterpreter.MAX_COMMAND_LENGTH
    val valid = draft.isNotBlank() && !tooLong && !session.busy
    fun send() {
        if (valid && onSubmit()) { focus.clearFocus(); keyboard?.hide() }
    }
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 5.dp, top = 4.dp)) {
        if (tooLong) Text(stringResource(R.string.reply_long), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedIconButton(onClick = { focus.clearFocus(); keyboard?.hide(); onMic() }, enabled = !session.busy || listening) {
                Mark(if (listening) Glyph.CHECK else Glyph.MIC, description = stringResource(if (listening) R.string.mic_finish else R.string.mic_press))
            }
            OutlinedTextField(value = draft, onValueChange = edit, enabled = !session.busy, isError = tooLong,
                modifier = Modifier.weight(1f).testTag("assistant_input"), singleLine = true, shape = InsetShape,
                label = { Text(stringResource(R.string.command_input)) },
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }))
            FilledIconButton(onClick = ::send, enabled = valid, modifier = Modifier.testTag("assistant_submit")) {
                Mark(Glyph.SEND, description = stringResource(R.string.command_send))
            }
        }
    }
}

@Composable
internal fun SessionReadout(session: Session, audio: SpeakerState, replay: (Reply) -> Unit, showStatus: Boolean = false) {
    val context = LocalContext.current
    Panel(Modifier.testTag("session_readout")) {
        if (showStatus) StatusBadge(session.phase, Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite })
        session.transcript?.let {
            Kicker(stringResource(if (session.transcriptFinal) R.string.transcript_final else R.string.transcript_partial))
            Text(it, modifier = Modifier.testTag("session_transcript"), style = MaterialTheme.typography.bodyLarge)
        }
        session.action?.let { action ->
            val color = MaterialTheme.colorScheme.primary
            Row(Modifier.fillMaxWidth().background(color.copy(alpha = .075f), InsetShape).padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                TileIcon(action.glyph(), color, 34.dp)
                Column(Modifier.weight(1f)) {
                    Kicker(stringResource(R.string.single_step))
                    Text(stringResource(action.copyText().title), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        val reply = session.reply
        if (reply != null) {
            Kicker(stringResource(R.string.reply_label))
            Text(context.describe(reply), modifier = Modifier.testTag("session_reply").semantics { liveRegion = LiveRegionMode.Polite },
                color = if (session.phase == Phase.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge)
            if (reply is Reply.Command && audio.canReplay()) Playback { replay(reply) }
        } else {
            val hint = when (session.phase) {
                Phase.LISTENING -> session.listeningHint()
                Phase.THINKING -> R.string.checking_command
                Phase.EXECUTING -> R.string.checking_platform
                else -> null
            }
            hint?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
internal fun VoiceFallback(issue: VoiceIssue, navigate: (Page) -> Unit, showAssistant: Boolean, showIssue: Boolean) {
    Panel {
        if (showIssue) Notice(Glyph.INFO, stringResource(issue.messageId()))
        Column {
            if (showAssistant) TextButton(onClick = { navigate(Page.ASSISTANT) }) { Text(stringResource(R.string.type_instead)) }
            TextButton(onClick = { navigate(Page.SETTINGS) }) { Text(stringResource(R.string.voice_settings_link)) }
        }
    }
}

@Composable
private fun ConversationCard(item: HistoryEvent, audio: SpeakerState, replay: ((Reply) -> Unit)?) {
    val context = LocalContext.current
    Panel {
        Kicker(stringResource(R.string.transcript_final))
        Text(item.transcript ?: item.action?.let { stringResource(it.copyText().phrase) } ?: stringResource(R.string.unmatched_command))
        HorizontalDivider(color = LocalVoiceColors.current.line)
        Kicker(stringResource(R.string.reply_label))
        Text(context.describe(item.result), style = MaterialTheme.typography.bodyMedium)
        if (audio.canReplay() && replay != null) Playback { replay(Reply.Command(item.result)) }
    }
}

@Composable
private fun Playback(play: () -> Unit) {
    TextButton(onClick = play) {
        Mark(Glyph.SOUND, modifier = Modifier.size(18.dp).padding(end = 3.dp))
        Text(stringResource(R.string.replay))
    }
}

@Composable
internal fun ActionsScreen(session: Session, audio: SpeakerState, run: (DeviceAction) -> Unit, replay: (Reply) -> Unit) {
    val scroll = rememberScrollState()
    LaunchedEffect(session.sequence) {
        if (session.source == InputSource.SHORTCUT) scroll.scrollTo(0)
    }
    ScrollPage(scroll) {
        PageHeading(stringResource(R.string.actions_kicker), stringResource(R.string.actions_title), stringResource(R.string.actions_intro))
        if (session.source == InputSource.SHORTCUT && session.visible) SessionReadout(session, audio, replay, showStatus = true)
        ActionGroup.entries.forEach { group ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle(stringResource(group.title), stringResource(group.detail))
                DeviceAction.entries.filter { it.copyText().group == group }.forEach { action ->
                    val copy = action.copyText()
                    RowTile(action.glyph(), stringResource(copy.title), stringResource(copy.phrase),
                        modifier = Modifier.testTag("action_${action.key}"), enabled = !session.busy, color = actionColor(action)) { run(action) }
                }
            }
        }
    }
}

@Composable
internal fun HistoryCard(item: HistoryEvent, compact: Boolean = false) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val format = remember(locale) { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale) }
    val title = item.action?.let { stringResource(it.copyText().title) } ?: stringResource(R.string.unmatched_command)
    val report = item.result as? CommandResult.Executed
    val ok = report != null && report.result !is ActionResult.Failed
    val tint = if (ok) LocalVoiceColors.current.green else MaterialTheme.colorScheme.error
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TileIcon(if (ok) Glyph.CHECK else Glyph.ALERT, tint, 36.dp)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(format.format(Date(item.at)), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (!compact) {
            val source = stringResource(when (item.source) {
                InputSource.VOICE -> R.string.source_voice
                InputSource.TEXT -> R.string.source_text
                InputSource.SHORTCUT -> R.string.source_shortcut
            })
            Kicker(source)
            item.transcript?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
        Text(context.describe(item.result), maxLines = if (compact) 2 else Int.MAX_VALUE,
            overflow = if (compact) TextOverflow.Ellipsis else TextOverflow.Clip, color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun actionColor(action: DeviceAction): Color = when (action) {
    DeviceAction.CAMERA, DeviceAction.WIFI, DeviceAction.DISPLAY, DeviceAction.MAPS -> LocalVoiceColors.current.ice
    DeviceAction.WHATSAPP, DeviceAction.SPOTIFY, DeviceAction.BATTERY -> LocalVoiceColors.current.green
    else -> MaterialTheme.colorScheme.primary
}

private fun Session.listeningHint(): Int = when (capture) {
    Capture.CONNECTING -> R.string.mic_connecting
    Capture.SPEECH -> R.string.mic_speaking
    else -> R.string.mic_listening_hint
}

internal fun SpeakerState.canReplay() = availability == SpeakerAvailability.EXACT || availability == SpeakerAvailability.SAME_LANGUAGE

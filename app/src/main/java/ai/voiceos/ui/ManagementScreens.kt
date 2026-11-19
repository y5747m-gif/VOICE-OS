package ai.voiceos.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ai.voiceos.BuildConfig
import ai.voiceos.R
import ai.voiceos.actions.copyText
import ai.voiceos.ai.HistoryState
import ai.voiceos.ai.MemoryState
import ai.voiceos.ai.messageId
import ai.voiceos.core.DeviceAction
import ai.voiceos.data.StoredAction
import ai.voiceos.memory.NicknameStore
import ai.voiceos.settings.RecognitionMode
import ai.voiceos.settings.UserSettings
import ai.voiceos.voice.VoiceIssue

@Composable
internal fun MoreScreen(navigate: (Page) -> Unit) = ScrollPage {
    PageHeading(stringResource(R.string.more_kicker), stringResource(R.string.more_title), stringResource(R.string.more_intro))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            Page.SITE to R.string.site_intro, Page.MEMORY to R.string.more_memory, Page.HISTORY to R.string.more_history,
            Page.AUTOMATIONS to R.string.more_automations, Page.SETTINGS to R.string.more_settings,
            Page.PERMISSIONS to R.string.more_permissions, Page.ABOUT to R.string.more_about,
        ).forEach { (page, detail) ->
            RowTile(page.glyph, stringResource(page.title), stringResource(detail), onClick = { navigate(page) })
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun MemoryScreen(memory: MemoryState, save: (String) -> Unit, remove: () -> Unit, navigate: (Page) -> Unit) = ScrollPage {
    var draft by remember(memory.name) { mutableStateOf(memory.name.orEmpty()) }
    var confirm by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val name = draft.trim()
    val invalid = name.isNotEmpty() && !NicknameStore.isValidName(name)
    PageHeading(stringResource(R.string.memory_kicker), stringResource(R.string.memory_title), stringResource(R.string.memory_intro))
    Panel {
        TileIcon(Glyph.MEMORY)
        SectionTitle(stringResource(R.string.memory_name_title), stringResource(R.string.memory_name_intro))
        OutlinedTextField(draft, { draft = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.memory_name)) },
            isError = invalid, enabled = !memory.busy, singleLine = true, shape = InsetShape,
            supportingText = { Text(stringResource(if (invalid) R.string.memory_invalid else R.string.memory_limit, NicknameStore.MAX_NAME_LENGTH)) },
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false))
        if (memory.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (memory.problem) Notice(Glyph.ALERT, stringResource(R.string.memory_error), emphasized = true)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !memory.busy && name.isNotEmpty() && name != memory.name && !invalid,
                onClick = { focus.clearFocus(); save(name) }) { Text(stringResource(R.string.save_name)) }
            if (memory.name != null || memory.problem) {
                OutlinedButton(enabled = !memory.busy, onClick = { focus.clearFocus(); confirm = true }) {
                    Text(stringResource(R.string.remove_name))
                }
            }
        }
        if (!memory.problem && memory.name != null) Notice(Glyph.SHIELD, stringResource(R.string.memory_saved))
    }
    Notice(Glyph.INFO, stringResource(R.string.memory_scope))
    RowTile(Glyph.HISTORY, stringResource(R.string.nav_history), stringResource(R.string.more_history)) { navigate(Page.HISTORY) }
    if (confirm) ConfirmRemoval(R.string.remove_name, R.string.memory_confirm, onClose = { confirm = false }) {
        confirm = false
        draft = ""
        remove()
    }
}

@Composable
internal fun HistoryScreen(history: HistoryState, config: UserSettings, clear: () -> Unit, navigate: (Page) -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 640.dp).fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item("heading") {
                PageHeading(stringResource(R.string.history_kicker), stringResource(R.string.history_title),
                    stringResource(R.string.history_intro, StoredAction.LIMIT))
            }
            item("retention") {
                Panel {
                    Notice(Glyph.SHIELD, stringResource(if (config.keepHistory) R.string.history_mode_on else R.string.history_mode_off))
                    if (history.problem) Notice(Glyph.ALERT, stringResource(R.string.history_error), emphasized = true)
                    if (history.clearing) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Column {
                        TextButton(onClick = { navigate(Page.SETTINGS) }) { Text(stringResource(R.string.history_settings)) }
                        if (history.entries.isNotEmpty() || history.problem) {
                            TextButton(onClick = { confirm = true }, enabled = !history.clearing) {
                                Mark(Glyph.TRASH, tint = MaterialTheme.colorScheme.error)
                                Text("  " + stringResource(R.string.history_clear), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            if (history.entries.isEmpty() && !history.clearing) item("empty") {
                EmptyPanel(Glyph.HISTORY, stringResource(R.string.history_empty), stringResource(R.string.history_empty_detail))
            }
            items(history.entries, key = { it.id }) { HistoryCard(it) }
        }
    }
    if (confirm) ConfirmRemoval(R.string.history_clear, R.string.history_confirm, onClose = { confirm = false }) {
        confirm = false
        clear()
    }
}

@Composable
internal fun AutomationsScreen(config: UserSettings, toggle: (DeviceAction) -> Unit, navigate: (Page) -> Unit) = ScrollPage {
    PageHeading(stringResource(R.string.pins_kicker), stringResource(R.string.pins_title), stringResource(R.string.pins_intro))
    Panel {
        SectionTitle(stringResource(R.string.pins_count, config.pins.size, UserSettings.MAX_PINS))
        Text(stringResource(R.string.pins_manual), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        if (config.pins.isNotEmpty()) Text(config.pins.map { stringResource(it.copyText().title) }.joinToString("  /  "), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { navigate(Page.HOME) }) { Text(stringResource(R.string.pins_to_home)) }
    }
    if (config.pins.size == UserSettings.MAX_PINS) Notice(Glyph.INFO, stringResource(R.string.pins_limit))
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        DeviceAction.entries.forEach { action ->
            val title = stringResource(action.copyText().title)
            val selected = action in config.pins
            val enabled = selected || config.pins.size < UserSettings.MAX_PINS
            val ink = LocalVoiceColors.current
            Surface(Modifier.fillMaxWidth(), shape = InsetShape, color = ink.panel, border = BorderStroke(1.dp, ink.line)) {
                Box(Modifier.fillMaxWidth().toggleable(value = selected, enabled = enabled, role = Role.Checkbox,
                    onValueChange = { toggle(action) })) {
                    TileContent(action.glyph(), title, stringResource(action.copyText().phrase), actionColor(action)) {
                        Checkbox(checked = selected, enabled = enabled, onCheckedChange = null)
                    }
                }
            }
        }
    }
}

@Composable
internal fun PermissionsScreen(
    micGranted: Boolean,
    issue: VoiceIssue?,
    config: UserSettings,
    request: () -> Unit,
    openSettings: () -> Boolean,
    navigate: (Page) -> Unit,
) = ScrollPage {
    var failedLink by remember { mutableStateOf(false) }
    PageHeading(stringResource(R.string.permissions_kicker), stringResource(R.string.permissions_title), stringResource(R.string.permissions_intro))
    Panel {
        TileIcon(Glyph.MIC)
        SectionTitle(stringResource(R.string.permissions_mic), stringResource(R.string.permissions_mic_detail))
        Kicker(stringResource(if (micGranted) R.string.permission_granted else R.string.permission_not_granted),
            if (micGranted) LocalVoiceColors.current.green else LocalVoiceColors.current.amber)
        if (!micGranted) {
            Button(onClick = request) { Text(stringResource(R.string.permission_request)) }
            Text(stringResource(R.string.permission_retry_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = { failedLink = !openSettings() }) { Text(stringResource(R.string.permission_android_settings)) }
        if (failedLink) Notice(Glyph.ALERT, stringResource(R.string.permission_settings_failed), emphasized = true)
    }
    Panel {
        SectionTitle(stringResource(R.string.provider_title), stringResource(
            if (config.recognition == RecognitionMode.ON_DEVICE) R.string.provider_device_detail else R.string.provider_system_detail))
        if (issue != null) Notice(Glyph.INFO, stringResource(issue.messageId()))
        TextButton(onClick = { navigate(Page.SETTINGS) }) { Text(stringResource(R.string.voice_settings_link)) }
    }
    Notice(Glyph.SHIELD, stringResource(R.string.permissions_limits))
    RowTile(Glyph.KEYBOARD, stringResource(R.string.type_instead), stringResource(R.string.assistant_intro)) { navigate(Page.ASSISTANT) }
}

@Composable
internal fun AboutScreen(navigate: (Page) -> Unit) = ScrollPage {
    PageHeading(stringResource(R.string.about_kicker), stringResource(R.string.nav_about), stringResource(R.string.about_intro))
    Panel {
        TileIcon(Glyph.WAVE, boxSize = 58.dp)
        Kicker(stringResource(R.string.about_version, BuildConfig.VERSION_NAME))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall)
        Text(stringResource(R.string.tagline), style = MaterialTheme.typography.titleMedium)
        HorizontalDivider(color = LocalVoiceColors.current.line)
        Text(stringResource(R.string.about_product), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Panel {
        SectionTitle(stringResource(R.string.about_scope))
        Notice(Glyph.BOLT, stringResource(R.string.about_dispatch))
        Notice(Glyph.SHIELD, stringResource(R.string.about_privacy))
        Notice(Glyph.INFO, stringResource(R.string.about_limitations))
    }
    RowTile(Glyph.WIFI, stringResource(R.string.nav_site), stringResource(R.string.site_intro)) { navigate(Page.SITE) }
    RowTile(Glyph.BOLT, stringResource(R.string.nav_actions), stringResource(R.string.actions_intro)) { navigate(Page.ACTIONS) }
    RowTile(Glyph.SHIELD, stringResource(R.string.nav_permissions), stringResource(R.string.permissions_intro)) { navigate(Page.PERMISSIONS) }
}

@Composable
private fun ConfirmRemoval(title: Int, detail: Int, onClose: () -> Unit, yes: () -> Unit) {
    AlertDialog(onDismissRequest = onClose, icon = { Mark(Glyph.TRASH, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(title)) }, text = { Text(stringResource(detail)) },
        confirmButton = { TextButton(onClick = yes) { Text(stringResource(R.string.confirm_delete), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) } })
}

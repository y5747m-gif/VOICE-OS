package ai.voiceos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ai.voiceos.R
import ai.voiceos.ai.messageId
import ai.voiceos.settings.Appearance
import ai.voiceos.settings.RecognitionMode
import ai.voiceos.settings.UserSettings
import ai.voiceos.settings.VoiceLanguage
import ai.voiceos.voice.SpeakerAvailability
import ai.voiceos.voice.SpeakerState
import ai.voiceos.voice.VoiceIssue

@Composable
internal fun SettingsScreen(
    config: UserSettings,
    audio: SpeakerState,
    issue: VoiceIssue?,
    update: ((UserSettings) -> UserSettings) -> Unit,
    navigate: (Page) -> Unit,
) = ScrollPage {
    var consent by remember { mutableStateOf(false) }
    PageHeading(stringResource(R.string.settings_kicker), stringResource(R.string.settings_title), stringResource(R.string.settings_intro))
    Panel {
        SectionTitle(stringResource(R.string.settings_language), stringResource(R.string.settings_language_detail))
        Column(Modifier.selectableGroup()) {
            Choice(stringResource(R.string.language_ar), config.language == VoiceLanguage.ARABIC) {
                update { it.copy(language = VoiceLanguage.ARABIC) }
            }
            Choice(stringResource(R.string.language_en), config.language == VoiceLanguage.ENGLISH) {
                update { it.copy(language = VoiceLanguage.ENGLISH) }
            }
        }
    }
    Panel {
        SectionTitle(stringResource(R.string.settings_voice))
        SettingToggle(stringResource(R.string.provider_system_toggle), stringResource(R.string.provider_system_toggle_detail),
            config.recognition == RecognitionMode.SYSTEM) { enable ->
            if (enable) consent = true
            else update { it.copy(recognition = RecognitionMode.ON_DEVICE) }
        }
        if (issue != null) Notice(Glyph.INFO, stringResource(issue.messageId()))
        HorizontalDivider(color = LocalVoiceColors.current.line)
        SettingToggle(stringResource(R.string.settings_speak), stringResource(R.string.settings_speak_detail), config.speakReplies) { on ->
            update { it.copy(speakReplies = on) }
        }
        Notice(Glyph.SOUND, stringResource(when (audio.availability) {
            SpeakerAvailability.LOADING -> R.string.tts_loading
            SpeakerAvailability.EXACT -> R.string.tts_exact
            SpeakerAvailability.SAME_LANGUAGE -> R.string.tts_alternate
            SpeakerAvailability.MISSING_VOICE -> R.string.tts_missing
            SpeakerAvailability.ENGINE_ERROR -> R.string.tts_error
        }))
    }
    Panel {
        SectionTitle(stringResource(R.string.settings_appearance))
        Column(Modifier.selectableGroup()) {
            Appearance.entries.forEach { appearance ->
                val title = when (appearance) {
                    Appearance.DARK -> R.string.theme_dark
                    Appearance.LIGHT -> R.string.theme_light
                    Appearance.SYSTEM -> R.string.theme_system
                }
                Choice(stringResource(title), config.appearance == appearance) { update { it.copy(appearance = appearance) } }
            }
        }
        HorizontalDivider(color = LocalVoiceColors.current.line)
        SettingToggle(stringResource(R.string.settings_reduce_motion), stringResource(R.string.settings_reduce_motion_detail), config.reduceMotion) { value ->
            update { it.copy(reduceMotion = value) }
        }
    }
    Panel {
        SectionTitle(stringResource(R.string.settings_privacy))
        SettingToggle(stringResource(R.string.settings_history), stringResource(R.string.settings_history_detail), config.keepHistory) { on ->
            update { it.copy(keepHistory = on) }
        }
        Notice(Glyph.SHIELD, stringResource(R.string.settings_privacy_detail))
        TextButton(onClick = { navigate(Page.HISTORY) }) { Text(stringResource(R.string.nav_history)) }
    }
    RowTile(Glyph.WIFI, stringResource(R.string.nav_site), stringResource(R.string.site_intro)) { navigate(Page.SITE) }
    RowTile(Glyph.SHIELD, stringResource(R.string.nav_permissions), stringResource(R.string.more_permissions)) { navigate(Page.PERMISSIONS) }
    if (consent) {
        AlertDialog(onDismissRequest = { consent = false }, icon = { Mark(Glyph.SHIELD) },
            title = { Text(stringResource(R.string.provider_consent_title)) }, text = { Text(stringResource(R.string.provider_consent)) },
            confirmButton = {
                TextButton(onClick = { consent = false; update { it.copy(recognition = RecognitionMode.SYSTEM) } }) {
                    Text(stringResource(R.string.provider_use_system))
                }
            }, dismissButton = { TextButton(onClick = { consent = false }) { Text(stringResource(R.string.cancel)) } })
    }
}

@Composable
private fun Choice(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected, role = Role.RadioButton, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = null)
        Text(title, style = MaterialTheme.typography.bodyLarge)
    }
}

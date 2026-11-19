package ai.voiceos.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ai.voiceos.BuildConfig
import ai.voiceos.R
import ai.voiceos.core.SiteOrigin
import ai.voiceos.site.SiteState
import ai.voiceos.site.SiteStatus
import java.net.URI

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SiteScreen(activity: Activity, state: SiteState, save: (SiteOrigin?) -> Unit, check: () -> Unit, cancel: () -> Unit) = ScrollPage {
    val saved = state.origin
    var draft by remember(saved) { mutableStateOf(saved?.home?.toASCIIString().orEmpty()) }
    var browserError by remember(saved) { mutableStateOf(false) }
    val parsed = remember(draft) { SiteOrigin.parse(draft) }
    val invalid = draft.isNotBlank() && parsed == null
    val focus = LocalFocusManager.current
    val checking = state.status == SiteStatus.CHECKING
    val release = state.release
    val status = when (state.status) {
        SiteStatus.IDLE -> R.string.site_idle
        SiteStatus.CHECKING -> R.string.site_checking
        SiteStatus.NO_RELEASE -> R.string.site_no_release
        SiteStatus.CURRENT -> R.string.site_current
        SiteStatus.DOWNLOAD -> R.string.site_available
        SiteStatus.INCOMPATIBLE -> R.string.site_incompatible
        SiteStatus.DIFFERENT_SIGNATURE -> R.string.site_signer_mismatch
        SiteStatus.ERROR -> R.string.site_error
    }
    fun open(url: URI) { browserError = !openInBrowser(activity, url) }

    PageHeading(stringResource(R.string.site_kicker), stringResource(R.string.nav_site), stringResource(R.string.site_intro))
    Panel {
        TileIcon(Glyph.WIFI)
        SectionTitle(stringResource(R.string.site_address), stringResource(R.string.site_address_detail))
        OutlinedTextField(draft, { if (it.length <= 300) draft = it }, shape = InsetShape, singleLine = true,
            modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.site_url)) },
            isError = invalid, supportingText = { if (invalid) Text(stringResource(R.string.site_invalid)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Button(onClick = { focus.clearFocus(); save(parsed) }, enabled = parsed != null && parsed != saved) {
                Text(stringResource(R.string.site_save))
            }
            if (saved != null) TextButton(onClick = { focus.clearFocus(); save(null) }) { Text(stringResource(R.string.site_disconnect)) }
        }
        if (saved != null) {
            Notice(Glyph.WIFI, stringResource(R.string.site_saved, saved.home.host))
            OutlinedButton(onClick = { open(saved.home) }) { Text(stringResource(R.string.site_open)) }
        }
    }
    Panel {
        SectionTitle(stringResource(R.string.site_updates_title), stringResource(R.string.site_installed, BuildConfig.VERSION_NAME))
        if (saved != null) {
            if (checking) LinearProgressIndicator(Modifier.fillMaxWidth())
            Notice(if (state.status == SiteStatus.ERROR) Glyph.ALERT else Glyph.INFO,
                stringResource(status), emphasized = state.status == SiteStatus.ERROR)
            if (release != null) Notice(Glyph.INFO, stringResource(R.string.site_release_name, release.versionName))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                if (checking) OutlinedButton(onClick = cancel) { Text(stringResource(R.string.cancel)) }
                else OutlinedButton(onClick = check) { Text(stringResource(R.string.site_check)) }
                if (release != null && state.status == SiteStatus.DOWNLOAD) {
                    Button(onClick = { open(saved.download(release)) }) { Text(stringResource(R.string.site_download)) }
                }
            }
        } else Notice(Glyph.INFO, stringResource(R.string.site_connect_first))
        if (browserError) Notice(Glyph.ALERT, stringResource(R.string.site_browser_error), emphasized = true)
        Notice(Glyph.SHIELD, stringResource(R.string.site_install_note))
    }
}

private fun openInBrowser(activity: Activity, uri: URI): Boolean = try {
    if (activity.isFinishing || activity.isDestroyed) false else {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri.toASCIIString())).addCategory(Intent.CATEGORY_BROWSABLE)
        activity.startActivity(intent)
        true
    }
} catch (_: ActivityNotFoundException) { false }
  catch (_: SecurityException) { false }

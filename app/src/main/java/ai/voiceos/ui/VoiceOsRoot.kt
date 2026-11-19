package ai.voiceos.ui

import android.app.Activity
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.voiceos.R
import ai.voiceos.ai.SessionViewModel
import ai.voiceos.settings.Appearance
import ai.voiceos.settings.VoiceLanguage
import ai.voiceos.settings.localized
import ai.voiceos.site.SiteViewModel

internal enum class Page(val title: Int, val glyph: Glyph) {
    HOME(R.string.nav_home, Glyph.HOME), ASSISTANT(R.string.nav_assistant, Glyph.CHAT),
    ACTIONS(R.string.nav_actions, Glyph.BOLT), MORE(R.string.nav_more, Glyph.GRID),
    MEMORY(R.string.nav_memory, Glyph.MEMORY), HISTORY(R.string.nav_history, Glyph.HISTORY),
    AUTOMATIONS(R.string.nav_automations, Glyph.PIN), SETTINGS(R.string.nav_settings, Glyph.SETTINGS),
    SITE(R.string.nav_site, Glyph.WIFI), PERMISSIONS(R.string.nav_permissions, Glyph.SHIELD), ABOUT(R.string.nav_about, Glyph.INFO);

    val isTab: Boolean get() = ordinal <= MORE.ordinal
}

@Composable
internal fun VoiceOsRoot(
    activity: Activity,
    model: SessionViewModel,
    site: SiteViewModel,
    onMic: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Boolean,
) {
    val siteState by site.state.collectAsStateWithLifecycle()
    val config by model.settings.collectAsStateWithLifecycle()
    val session by model.session.collectAsStateWithLifecycle()
    val history by model.history.collectAsStateWithLifecycle()
    val memory by model.memory.collectAsStateWithLifecycle()
    val conversation by model.conversation.collectAsStateWithLifecycle()
    val audio by model.audio.collectAsStateWithLifecycle()
    val mic by model.microphone.collectAsStateWithLifecycle()
    val voiceIssue by model.voiceAvailability.collectAsStateWithLifecycle()
    val base = LocalConfiguration.current
    val context = remember(config.language, base, activity) { activity.localized(config.language, base) }
    val dark = when (config.appearance) {
        Appearance.DARK -> true
        Appearance.LIGHT -> false
        Appearance.SYSTEM -> isSystemInDarkTheme()
    }
    SideEffect {
        val window = activity.window
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
    }

    var stack by rememberSaveable { mutableStateOf(arrayListOf(Page.HOME.name)) }
    var draft by remember { mutableStateOf("") } // don't serialize command text into instance state
    val stateHolder = rememberSaveableStateHolder()
    val page = Page.valueOf(stack.last())
    fun back() { if (stack.size > 1) stack = ArrayList(stack.dropLast(1)) }
    fun navigate(target: Page) {
        if (target == page) return
        stack = if (target.isTab) {
            if (target == Page.HOME) arrayListOf(Page.HOME.name) else arrayListOf(Page.HOME.name, target.name)
        } else if (target.name in stack) {
            ArrayList(stack.take(stack.indexOf(target.name) + 1))
        } else ArrayList(stack + target.name)
    }
    BackHandler(stack.size > 1, ::back)

    CompositionLocalProvider(
        LocalContext provides context,
        LocalConfiguration provides context.resources.configuration,
        LocalLayoutDirection provides if (config.language == VoiceLanguage.ARABIC) LayoutDirection.Rtl else LayoutDirection.Ltr,
    ) {
        VoiceTheme(dark) {
            Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                val ime = WindowInsets.ime.getBottom(LocalDensity.current) > 0
                Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    VoiceHeader(page, canStop = session.busy || audio.playing, onStop = model::stop, onBack = ::back,
                        onSettings = { navigate(Page.SETTINGS) })
                    Box(Modifier.weight(1f)) {
                        stateHolder.SaveableStateProvider(page.name) {
                            when (page) {
                                Page.HOME -> HomeScreen(session, audio, config, memory.name, history.entries, voiceIssue, onMic,
                                    model::stop, model::replay, model::runShortcut, ::navigate)
                                Page.ASSISTANT -> AssistantScreen(session, audio, conversation, voiceIssue, draft, { draft = it },
                                    onSubmit = { if (model.submit(draft)) { draft = ""; true } else false }, onMic, model::replay, ::navigate)
                                Page.ACTIONS -> ActionsScreen(session, audio, model::runShortcut, model::replay)
                                Page.MORE -> MoreScreen(::navigate)
                                Page.MEMORY -> MemoryScreen(memory, model::saveName, model::removeName, ::navigate)
                                Page.HISTORY -> HistoryScreen(history, config, { draft = ""; model.clearHistory() }, ::navigate)
                                Page.AUTOMATIONS -> AutomationsScreen(config, model::togglePin, ::navigate)
                                Page.SETTINGS -> SettingsScreen(config, audio, voiceIssue, model::updateSettings, ::navigate)
                                Page.SITE -> SiteScreen(activity, siteState, site::setSite, site::checkRelease, site::cancelCheck)
                                Page.PERMISSIONS -> PermissionsScreen(mic, voiceIssue, config, onRequestPermission, onOpenAppSettings, ::navigate)
                                Page.ABOUT -> AboutScreen(::navigate)
                            }
                        }
                    }
                    if (!ime) VoiceNavigation(if (page.isTab) page else Page.MORE, ::navigate)
                }
            }
        }
    }
}

@Composable
private fun VoiceHeader(page: Page, canStop: Boolean, onStop: () -> Unit, onBack: () -> Unit, onSettings: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(Modifier.widthIn(max = 640.dp).fillMaxWidth().heightIn(min = 66.dp).padding(start = 20.dp, end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!page.isTab) {
                IconButton(onClick = onBack) { Mark(Glyph.BACK, description = stringResource(R.string.back)) }
            } else TileIcon(Glyph.WAVE, boxSize = 38.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(stringResource(R.string.app_name), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                Kicker(stringResource(R.string.brand_caption), MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (canStop) {
                IconButton(onClick = onStop, modifier = Modifier.testTag("header_stop")) {
                    Mark(Glyph.STOP, tint = MaterialTheme.colorScheme.error, description = stringResource(R.string.stop))
                }
            }
            if (page != Page.SETTINGS) {
                IconButton(onClick = onSettings) { Mark(Glyph.SETTINGS, description = stringResource(R.string.nav_settings)) }
            }
        }
    }
}

@Composable
private fun VoiceNavigation(selected: Page, navigate: (Page) -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp),
            shape = RoundedCornerShape(23.dp), color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, LocalVoiceColors.current.line),
        ) {
            Row(Modifier.fillMaxWidth().padding(5.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf(Page.HOME, Page.ASSISTANT, Page.ACTIONS, Page.MORE).forEach { target ->
                    val active = target == selected
                    val foreground = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    val shape = RoundedCornerShape(18.dp)
                    Column(Modifier.weight(1f).heightIn(min = 52.dp).clip(shape)
                        .background(if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                        .selectable(selected = active, role = Role.Tab, onClick = { navigate(target) })
                        .padding(horizontal = 3.dp, vertical = 5.dp).testTag("nav_${target.name.lowercase()}"),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Mark(target.glyph, Modifier.size(19.dp), tint = foreground)
                        Text(stringResource(target.title), color = foreground, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

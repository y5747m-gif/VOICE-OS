package ai.voiceos.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.voiceos.R
import ai.voiceos.ai.Phase

internal val CardShape = RoundedCornerShape(22.dp)
internal val InsetShape = RoundedCornerShape(16.dp)

@Composable
internal fun ScrollPage(scroll: ScrollState = rememberScrollState(), content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(scroll).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.height(1.dp))
            content()
            Spacer(Modifier.height(5.dp))
        }
    }
}

@Composable
internal fun Kicker(text: String, color: Color = MaterialTheme.colorScheme.primary) {
    Text(text, color = color, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, letterSpacing = if (LocalLayoutDirection.current == LayoutDirection.Rtl) 0.sp else 1.1.sp, lineHeight = 17.sp)
}

@Composable
internal fun PageHeading(kicker: String, title: String, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Kicker(kicker)
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun SectionTitle(title: String, detail: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val ink = LocalVoiceColors.current
    Surface(modifier.fillMaxWidth(), shape = CardShape, color = ink.panel, border = BorderStroke(1.dp, ink.line)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp), content = content)
    }
}

@Composable
internal fun TileIcon(glyph: Glyph, color: Color = MaterialTheme.colorScheme.primary, boxSize: Dp = 42.dp) {
    Box(Modifier.size(boxSize).background(color.copy(alpha = if (LocalVoiceColors.current.dark) .12f else .09f), RoundedCornerShape(13.dp)),
        contentAlignment = Alignment.Center) { Mark(glyph, Modifier.size(21.dp), tint = color) }
}

@Composable
internal fun StatusBadge(phase: Phase, modifier: Modifier = Modifier) {
    val color = phaseColor(phase)
    Row(
        modifier.background(color.copy(alpha = .10f), CircleShape).padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Text(stringResource(phase.titleId()), style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
internal fun phaseColor(phase: Phase): Color = when (phase) {
    Phase.READY -> LocalVoiceColors.current.green
    Phase.LISTENING -> LocalVoiceColors.current.ice
    Phase.THINKING -> MaterialTheme.colorScheme.primary
    Phase.EXECUTING -> MaterialTheme.colorScheme.secondary
    Phase.COMPLETED -> LocalVoiceColors.current.green
    Phase.ERROR -> MaterialTheme.colorScheme.error
    Phase.STOPPED -> LocalVoiceColors.current.amber
}

internal fun Phase.titleId(): Int = when (this) {
    Phase.READY -> R.string.phase_ready
    Phase.LISTENING -> R.string.phase_listening
    Phase.THINKING -> R.string.phase_thinking
    Phase.EXECUTING -> R.string.phase_executing
    Phase.COMPLETED -> R.string.phase_completed
    Phase.ERROR -> R.string.phase_error
    Phase.STOPPED -> R.string.phase_stopped
}

@Composable
internal fun Notice(glyph: Glyph, text: String, emphasized: Boolean = false) {
    val color = if (emphasized) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().background(color.copy(alpha = .075f), InsetShape).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top,
    ) {
        Mark(glyph, Modifier.size(18.dp).padding(top = 1.dp), tint = color)
        Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.weight(1f))
    }
}

@Composable
internal fun SettingToggle(title: String, detail: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).toggleable(value = checked, role = Role.Switch, onValueChange = change),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
internal fun RowTile(
    glyph: Glyph,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    val ink = LocalVoiceColors.current
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), enabled = enabled, shape = InsetShape,
        border = BorderStroke(1.dp, ink.line), color = ink.panel) {
        TileContent(glyph, title, detail, color) {
            Mark(Glyph.NEXT, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun TileContent(glyph: Glyph, title: String, detail: String, color: Color = MaterialTheme.colorScheme.primary,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 66.dp).padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.CenterVertically) {
        TileIcon(glyph, color)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

@Composable
internal fun EmptyPanel(glyph: Glyph, title: String, detail: String) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TileIcon(glyph)
            Spacer(Modifier.width(11.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

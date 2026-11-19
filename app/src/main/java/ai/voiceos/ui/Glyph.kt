package ai.voiceos.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import ai.voiceos.core.DeviceAction

internal enum class Glyph {
    WAVE, HOME, CHAT, BOLT, GRID, SETTINGS, BACK, NEXT, MIC, STOP, CHECK, ALERT, KEYBOARD, HISTORY, MEMORY,
    SHIELD, INFO, PLAY, CAMERA, PHONE, MAP, WIFI, BLUETOOTH, SCREEN, BATTERY, MUSIC, SOCIAL, SEND, PIN, SOUND, TRASH,
}

@Composable
internal fun Mark(
    glyph: Glyph,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    description: String? = null,
) {
    val mirror = LocalLayoutDirection.current == LayoutDirection.Rtl && glyph in setOf(Glyph.BACK, Glyph.NEXT, Glyph.SEND)
    Canvas(modifier.size(24.dp).then(if (description == null) Modifier else Modifier.semantics { contentDescription = description })) {
        val stroke = Stroke(1.65f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        scale(size.width / 24f * if (mirror) -1f else 1f, size.height / 24f,
            pivot = if (mirror) Offset(size.width / 2f, 0f) else Offset.Zero) {
            fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
                drawLine(tint, Offset(x1, y1), Offset(x2, y2), stroke.width, cap = StrokeCap.Round)
            fun path(draw: Path.() -> Unit) = drawPath(Path().apply(draw), tint, style = stroke)
            fun dot(x: Float, y: Float, r: Float = 1.15f) = drawCircle(tint, r, Offset(x, y))
            fun circle(x: Float, y: Float, r: Float) = drawCircle(tint, r, Offset(x, y), style = stroke)
            fun box(x: Float, y: Float, w: Float, h: Float, r: Float = 2f) =
                drawRoundRect(tint, Offset(x, y), Size(w, h), CornerRadius(r), style = stroke)
            when (glyph) {
                Glyph.WAVE -> listOf(6f, 12f, 17f, 12f, 6f).forEachIndexed { i, h ->
                    line(4f + i * 4, 12f - h / 2, 4f + i * 4, 12f + h / 2)
                }
                Glyph.HOME -> path {
                    moveTo(3f, 10f); lineTo(12f, 3f); lineTo(21f, 10f); lineTo(21f, 19f); lineTo(15f, 19f)
                    lineTo(15f, 13f); lineTo(9f, 13f); lineTo(9f, 19f); lineTo(3f, 19f); close()
                }
                Glyph.CHAT -> {
                    path {
                        moveTo(5f, 4f); lineTo(18f, 4f); quadraticTo(21f, 4f, 21f, 7f); lineTo(21f, 15f)
                        quadraticTo(21f, 18f, 18f, 18f); lineTo(9f, 18f); lineTo(3f, 21f); lineTo(3f, 7f); quadraticTo(3f, 4f, 5f, 4f)
                    }
                    line(7f, 11f, 9f, 11f); line(12f, 9f, 12f, 13f); line(15f, 11f, 17f, 11f)
                }
                Glyph.BOLT -> path { moveTo(13f, 2f); lineTo(4f, 13f); lineTo(11f, 13f); lineTo(10f, 22f); lineTo(20f, 10f); lineTo(13f, 10f); close() }
                Glyph.GRID -> for (x in listOf(4f, 14f)) for (y in listOf(4f, 14f)) box(x, y, 6f, 6f, 1.4f)
                Glyph.SETTINGS -> {
                    line(4f, 6f, 20f, 6f); line(4f, 12f, 20f, 12f); line(4f, 18f, 20f, 18f)
                    line(9f, 4f, 9f, 8f); line(15f, 10f, 15f, 14f); line(10f, 16f, 10f, 20f)
                }
                Glyph.BACK -> path { moveTo(14f, 5f); lineTo(7f, 12f); lineTo(14f, 19f) }
                Glyph.NEXT -> path { moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f) }
                Glyph.MIC -> {
                    box(9f, 3f, 6f, 11f, 3f)
                    path { moveTo(5.5f, 11f); lineTo(5.5f, 12f); cubicTo(5.5f, 20f, 18.5f, 20f, 18.5f, 12f); lineTo(18.5f, 11f) }
                    line(12f, 18.5f, 12f, 22f); line(9f, 22f, 15f, 22f)
                }
                Glyph.STOP -> drawRoundRect(tint, Offset(6f, 6f), Size(12f, 12f), CornerRadius(2f))
                Glyph.CHECK -> path { moveTo(5f, 12f); lineTo(10f, 17f); lineTo(19f, 7f) }
                Glyph.ALERT -> { circle(12f, 12f, 9f); line(12f, 7f, 12f, 12f); dot(12f, 16f) }
                Glyph.KEYBOARD -> {
                    box(2.5f, 5f, 19f, 14f)
                    for (x in 6..18 step 4) { dot(x.toFloat(), 9.5f, 0.8f); dot(x.toFloat(), 12.5f, 0.8f) }
                    line(8f, 16f, 16f, 16f)
                }
                Glyph.HISTORY -> {
                    path { moveTo(4f, 10f); lineTo(4f, 4f); moveTo(4f, 10f); lineTo(10f, 10f)
                        moveTo(4f, 10f); cubicTo(5f, 4f, 13f, 1f, 18f, 6f); cubicTo(24f, 12f, 19f, 21f, 12f, 21f); quadraticTo(8f, 21f, 5f, 18f) }
                    path { moveTo(12f, 7f); lineTo(12f, 12f); lineTo(16f, 14f) }
                }
                Glyph.MEMORY -> {
                    path { moveTo(4f, 6f); lineTo(4f, 18f); cubicTo(4f, 22f, 20f, 22f, 20f, 18f); lineTo(20f, 6f) }
                    for (y in listOf(6f, 11.5f, 16.5f)) path { moveTo(4f, y); cubicTo(4f, y + 4f, 20f, y + 4f, 20f, y) }
                    path { moveTo(4f, 6f); cubicTo(4f, 2f, 20f, 2f, 20f, 6f) }
                }
                Glyph.SHIELD -> {
                    path { moveTo(12f, 3f); lineTo(20f, 6f); lineTo(20f, 12f); quadraticTo(20f, 18f, 12f, 22f)
                        quadraticTo(4f, 18f, 4f, 12f); lineTo(4f, 6f); close() }
                    path { moveTo(8.5f, 12f); lineTo(11f, 14.5f); lineTo(15.5f, 9.5f) }
                }
                Glyph.INFO -> { circle(12f, 12f, 9f); dot(12f, 8f); line(12f, 11.5f, 12f, 16.5f) }
                Glyph.PLAY -> {
                    box(2.5f, 5f, 19f, 14f, 4f)
                    path { moveTo(10f, 9f); lineTo(15f, 12f); lineTo(10f, 15f); close() }
                }
                Glyph.CAMERA -> {
                    path { moveTo(3f, 7f); lineTo(7f, 7f); lineTo(9f, 4.5f); lineTo(15f, 4.5f); lineTo(17f, 7f)
                        lineTo(21f, 7f); lineTo(21f, 19f); lineTo(3f, 19f); close() }
                    circle(12f, 13f, 3.5f)
                }
                Glyph.PHONE -> path { moveTo(6f, 3f); lineTo(9f, 6f); lineTo(8f, 10f); quadraticTo(10f, 14f, 14f, 16f)
                    lineTo(18f, 15f); lineTo(21f, 18f); quadraticTo(19f, 23f, 14f, 21f); quadraticTo(6f, 18f, 3f, 10f); quadraticTo(1f, 5f, 6f, 3f); close() }
                Glyph.MAP -> {
                    path { moveTo(12f, 22f); cubicTo(9.5f, 18f, 5f, 13.5f, 5f, 9.5f); cubicTo(5f, 0.5f, 19f, 0.5f, 19f, 9.5f); cubicTo(19f, 14f, 14.5f, 18f, 12f, 22f); close() }
                    circle(12f, 9f, 2.5f)
                }
                Glyph.WIFI -> {
                    for (w in listOf(9f, 6.5f, 3.7f)) path { moveTo(12f - w, 15f - w * .7f); quadraticTo(12f, 14f - w * 1.55f, 12f + w, 15f - w * .7f) }
                    dot(12f, 17.5f, 1.4f)
                }
                Glyph.BLUETOOTH -> path { moveTo(6f, 7f); lineTo(17.5f, 17f); lineTo(12f, 22f); lineTo(12f, 2f); lineTo(17.5f, 7f); lineTo(6f, 17f) }
                Glyph.SCREEN -> { box(3f, 4f, 18f, 13f); line(12f, 17f, 12f, 21f); line(8f, 21f, 16f, 21f) }
                Glyph.BATTERY -> { box(2f, 7f, 18f, 10f); line(22f, 10f, 22f, 14f); line(6f, 12f, 14f, 12f) }
                Glyph.MUSIC -> {
                    path { moveTo(9f, 17f); lineTo(9f, 5f); lineTo(20f, 3f); lineTo(20f, 15f); moveTo(9f, 8.5f); lineTo(20f, 6.5f) }
                    circle(6.7f, 18f, 2.5f); circle(17.7f, 16f, 2.5f)
                }
                Glyph.SOCIAL -> { box(3.5f, 3.5f, 17f, 17f, 4.5f); circle(12f, 12f, 4f); dot(17f, 7f) }
                Glyph.SEND -> path { moveTo(3f, 4f); lineTo(21f, 12f); lineTo(3f, 20f); lineTo(6f, 12f); close(); moveTo(6f, 12f); lineTo(21f, 12f) }
                Glyph.PIN -> path { moveTo(15f, 3f); lineTo(21f, 9f); lineTo(17f, 10f); lineTo(14f, 13f); lineTo(14f, 16f); lineTo(8f, 10f); lineTo(11f, 10f); lineTo(14f, 7f); close(); moveTo(10.8f, 13.2f); lineTo(4f, 20f) }
                Glyph.SOUND -> {
                    path { moveTo(3f, 9f); lineTo(7f, 9f); lineTo(11f, 5f); lineTo(11f, 19f); lineTo(7f, 15f); lineTo(3f, 15f); close() }
                    for (x in listOf(14.5f, 18f)) path { moveTo(x, 12f - (x - 11f)); quadraticTo(x + 5f, 12f, x, 12f + (x - 11f)) }
                }
                Glyph.TRASH -> {
                    line(4f, 6f, 20f, 6f); line(9f, 3f, 15f, 3f)
                    path { moveTo(6f, 6f); lineTo(7f, 21f); lineTo(17f, 21f); lineTo(18f, 6f) }
                    line(10f, 10f, 10f, 17f); line(14f, 10f, 14f, 17f)
                }
            }
        }
    }
}

internal fun DeviceAction.glyph(): Glyph = when (this) {
    DeviceAction.WHATSAPP -> Glyph.CHAT
    DeviceAction.YOUTUBE -> Glyph.PLAY
    DeviceAction.INSTAGRAM, DeviceAction.FACEBOOK -> Glyph.SOCIAL
    DeviceAction.SPOTIFY -> Glyph.MUSIC
    DeviceAction.CAMERA -> Glyph.CAMERA
    DeviceAction.DIALER -> Glyph.PHONE
    DeviceAction.MAPS, DeviceAction.LOCATION -> Glyph.MAP
    DeviceAction.SETTINGS -> Glyph.SETTINGS
    DeviceAction.WIFI -> Glyph.WIFI
    DeviceAction.BLUETOOTH -> Glyph.BLUETOOTH
    DeviceAction.DISPLAY -> Glyph.SCREEN
    DeviceAction.BATTERY -> Glyph.BATTERY
}

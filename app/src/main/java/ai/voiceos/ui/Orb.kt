package ai.voiceos.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ai.voiceos.R
import ai.voiceos.ai.Phase
import ai.voiceos.ai.Session
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun VoiceStage(
    session: Session,
    playing: Boolean,
    reduceMotion: Boolean,
    caption: String,
    onMic: () -> Unit,
    onStop: () -> Unit,
) {
    val palette = LocalVoiceColors.current
    val shape = RoundedCornerShape(28.dp)
    val microphoneOpen = session.phase == Phase.LISTENING
    val target = if (microphoneOpen) session.level else 0f
    val smoothed = if (reduceMotion) null else animateFloatAsState(target, tween(130), label = "microphone amplitude")
    val rotation = if (reduceMotion) null else {
        val loop = rememberInfiniteTransition(label = "orb orbit")
        loop.animateFloat(0f, 1f, infiniteRepeatable(tween(13_000, easing = LinearEasing), RepeatMode.Restart), label = "orb angle")
    }
    Box(Modifier.fillMaxWidth().clip(shape).background(Brush.verticalGradient(listOf(palette.stageTop, palette.stageBottom)))
        .border(1.dp, palette.line, shape)) {
        Canvas(Modifier.matchParentSize()) {
            val mark = if (palette.dark) Color(0xFFACADFC) else Color(0xFF5243A5)
            val spacing = 23.dp.toPx()
            for (i in 1..(size.width / spacing).toInt()) {
                for (j in 1..(size.height / spacing).toInt()) {
                    if ((i * 13 + j * 7) % 7 != 0) {
                        val alpha = if ((i * 7 + j * 13) % 11 == 0) .19f else .07f
                        drawCircle(mark.copy(alpha = alpha), if (alpha > .1f) 1.05.dp.toPx() else .65.dp.toPx(), Offset(i * spacing, j * spacing))
                    }
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 15.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Kicker(stringResource(R.string.orb_label), MaterialTheme.colorScheme.onSurfaceVariant)
                StatusBadge(session.phase, Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite })
            }
            Orb(Modifier.size(185.dp), rotation, { smoothed?.value ?: target }, microphoneOpen)
            WaveBar({ smoothed?.value ?: target }, microphoneOpen, Modifier.padding(bottom = 3.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onMic, enabled = !session.busy || microphoneOpen,
                    modifier = Modifier.weight(1f).heightIn(min = 54.dp).testTag("home_mic"),
                ) {
                    Mark(if (microphoneOpen) Glyph.CHECK else Glyph.MIC, Modifier.padding(end = 7.dp))
                    Text(stringResource(if (microphoneOpen) R.string.mic_finish else R.string.mic_press))
                }
                if (session.busy || playing) {
                    OutlinedIconButton(onClick = onStop, modifier = Modifier.size(54.dp)) {
                        Mark(Glyph.STOP, description = stringResource(R.string.stop))
                    }
                }
            }
            Text(caption, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Orb(modifier: Modifier, orbitState: State<Float>?, level: () -> Float, listening: Boolean) {
    val dark = LocalVoiceColors.current.dark
    Canvas(modifier) {
        val center = this.center
        // Draw-phase reads invalidate just the art, not the surrounding controls.
        val rotation = orbitState?.value ?: .085f
        val phase = rotation * (2f * PI.toFloat())
        val pulse = (sin(phase * 2) + 1f) * .5f
        val outer = size.minDimension * .485f
        val core = size.minDimension * (.297f + level() * .021f + pulse * .008f)
        val violet = Color(0xFF8B57FF)
        val cyan = Color(0xFF9AF2FF)
        val ice = Color(0xFFD6E3FF)
        val ring = if (dark) Color(0xFFABA2FF) else Color(0xFF6448CB)
        val wash = if (listening) Color(0xFF42CCFF) else Color(0xFF8062FF)
        fun orbit(rad: Float, angle: Float) = Offset(center.x + cos(angle) * rad, center.y + sin(angle) * rad)
        fun arc(rad: Float, start: Float, length: Float, tint: Color, stroke: Float) {
            drawArc(tint, start, length, false, center - Offset(rad, rad), Size(2 * rad, 2 * rad), style = Stroke(stroke, cap = StrokeCap.Round))
        }

        drawCircle(Brush.radialGradient(listOf(wash.copy(alpha = .72f), violet.copy(alpha = .23f), Color.Transparent),
            center, outer), outer, center)
        drawCircle(ring.copy(alpha = .23f), outer * .94f, center, style = Stroke(.65.dp.toPx()))
        drawCircle(ring.copy(alpha = .14f), outer * .79f, center, style = Stroke(.75.dp.toPx()))
        arc(outer * .94f, rotation * 360f - 22f, 38f, ring.copy(alpha = .72f), 1.dp.toPx())
        arc(outer * .93f, rotation * 360f + 150f, 65f, ring.copy(alpha = .41f), .85.dp.toPx())
        arc(outer * .79f, -rotation * 360f + 30f, 94f, ring.copy(alpha = .26f), .7.dp.toPx())

        val ellipse = Size(outer * 1.96f, outer * .72f)
        rotate(-24f, center) {
            drawOval(ring.copy(alpha = .53f), center - Offset(ellipse.width / 2, ellipse.height / 2), ellipse, style = Stroke(.85.dp.toPx()))
        }
        rotate(30f, center) {
            drawOval(ring.copy(alpha = .27f), center - Offset(ellipse.width / 2, ellipse.height / 2), ellipse, style = Stroke(.7.dp.toPx()))
        }
        val bounds = Rect(center - Offset(core, core), Size(core * 2, core * 2))
        val disk = Path().apply { addOval(bounds) }
        clipPath(disk) {
            drawCircle(Brush.radialGradient(listOf(Color(0xFF639BFF), Color(0xFF392375), Color(0xFF111838), Color(0xFF0B1232)),
                center - Offset(core * .41f, core * .5f), core * 1.75f), core * 2, center)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFB18AFF).copy(alpha = .65f), Color.Transparent),
                center + Offset(core * .65f, core * .56f), core * 1.26f), core * 2, center)
            rotate(-15f + sin(phase) * 12f, center) {
                for (i in 0..14) {
                    val offset = (i - 7) * core * .071f
                    val lift = sin(phase * 2 + i * .18f) * core * .09f
                    val line = Path().apply {
                        moveTo(center.x - core * 1.3f, center.y - core * .20f + offset)
                        cubicTo(center.x - core * .15f, center.y - core * .91f + offset + lift,
                            center.x - core * .29f, center.y + core * .83f + offset - lift,
                            center.x + core * 1.2f, center.y + core * .07f + offset)
                    }
                    drawPath(line, Brush.linearGradient(listOf(cyan.copy(alpha = .85f), ice.copy(alpha = .68f), violet.copy(alpha = .92f)),
                        center - Offset(core, core), center + Offset(core, core)),
                        alpha = if (i % 4 == 0) .82f else .45f, style = Stroke(if (i % 4 == 0) 1.15.dp.toPx() else .68.dp.toPx()))
                }
            }
            drawCircle(Brush.radialGradient(listOf(ice.copy(alpha = .58f), Color.Transparent),
                center - Offset(core * .42f, core * .5f), core * .55f), core * 2, center)
        }
        drawCircle(Brush.sweepGradient(listOf(violet, cyan, ice, Color(0xFF515DC5), violet), center), core, center,
            style = Stroke(1.55.dp.toPx()))
        arc(core * 1.05f, 190f, 81f, cyan.copy(alpha = .65f), .8.dp.toPx())
        arc(core * 1.04f, 11f, 79f, violet.copy(alpha = .63f), 1.15.dp.toPx())
        for (i in 0..8) {
            val a = phase + i * (PI.toFloat() / 4.5f)
            val r = outer * (.82f + .126f * abs(cos(i * 8f)))
            drawCircle(ring.copy(alpha = .36f + (i % 3) * .17f), (if (i % 3 == 0) 1.3f else .72f).dp.toPx(), orbit(r, a))
        }
        val marker = orbit(outer * .94f, phase + .08f)
        drawCircle(if (dark) cyan else violet, 2.65.dp.toPx(), marker)
        drawCircle(ring.copy(alpha = .5f), 5.2.dp.toPx(), marker, style = Stroke(.67.dp.toPx()))
    }
}

@Composable
private fun WaveBar(level: () -> Float, listening: Boolean, modifier: Modifier = Modifier) {
    val color = if (listening) LocalVoiceColors.current.ice else MaterialTheme.colorScheme.primary
    Canvas(modifier.fillMaxWidth(.68f).height(22.dp)) {
        val strength = if (listening) level() else .14f // stationary decoration when the mic is closed
        val count = 37
        val cell = size.width / count
        val center = (count - 1) / 2f
        for (i in 0 until count) {
            val envelope = 1 - abs(i - center) / (center + 1)
            val profile = .48f + abs(sin(i * 1.38f)) * .52f
            val h = (2.dp.toPx() + (size.height - 2.dp.toPx()) * envelope * strength * profile).coerceAtMost(size.height)
            val w = (cell * .34f).coerceIn(1.dp.toPx(), 2.dp.toPx())
            drawRoundRect(color.copy(alpha = .26f + envelope * .60f), Offset((i + .5f) * cell, (size.height - h) / 2),
                Size(w, h), CornerRadius(w))
        }
    }
}

package ai.voiceos.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

internal data class VoiceColors(
    val dark: Boolean,
    val panel: Color,
    val line: Color,
    val stageTop: Color,
    val stageBottom: Color,
    val ice: Color,
    val green: Color,
    val amber: Color,
)

private val night = darkColorScheme(
    primary = Color(0xFFC6B7FF), onPrimary = Color(0xFF211246),
    primaryContainer = Color(0xFF29244B), onPrimaryContainer = Color(0xFFE6DCFF),
    secondary = Color(0xFF94CBFF), onSecondary = Color(0xFF0A2746),
    tertiary = Color(0xFF8EE8D7), onTertiary = Color(0xFF09352D),
    background = Color(0xFF090B15), onBackground = Color(0xFFF2F1FF),
    surface = Color(0xFF101320), onSurface = Color(0xFFF2F1FF),
    surfaceVariant = Color(0xFF1C1F34), onSurfaceVariant = Color(0xFFADB2CB),
    outline = Color(0xFF777D9C), outlineVariant = Color(0xFF2C3048),
    error = Color(0xFFFFA9B8), onError = Color(0xFF4D0E1D),
    errorContainer = Color(0xFF391724), onErrorContainer = Color(0xFFFFD9E0),
)
private val daylight = lightColorScheme(
    primary = Color(0xFF4F36AA), onPrimary = Color.White,
    primaryContainer = Color(0xFFE8DFFF), onPrimaryContainer = Color(0xFF2A185F),
    secondary = Color(0xFF244F88), onSecondary = Color.White,
    tertiary = Color(0xFF12645C), onTertiary = Color.White,
    background = Color(0xFFF4F5FC), onBackground = Color(0xFF16182E),
    surface = Color(0xFFFEFDFF), onSurface = Color(0xFF16182E),
    surfaceVariant = Color(0xFFECEAF7), onSurfaceVariant = Color(0xFF525773),
    outline = Color(0xFF777B98), outlineVariant = Color(0xFFD8D9EA),
    error = Color(0xFFA62547), onError = Color.White,
    errorContainer = Color(0xFFFFE0E7), onErrorContainer = Color(0xFF65132A),
)
private val nightInk = VoiceColors(true, Color(0xFF111424), Color(0xFF2A2E46), Color(0xFF17172D), Color(0xFF0B0D1B),
    Color(0xFF9AE6FF), Color(0xFF91EAC2), Color(0xFFFFCD91))
private val dayInk = VoiceColors(false, Color(0xFFFAFAFF), Color(0xFFD9D9ED), Color(0xFFE5E1FE), Color(0xFFF9FAFF),
    Color(0xFF165C81), Color(0xFF1F7058), Color(0xFF7D4C0D))
internal val LocalVoiceColors = staticCompositionLocalOf { nightInk }

private val voiceTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 44.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.75).sp),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 37.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontSize = 21.sp, lineHeight = 29.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
)

@Composable
internal fun VoiceTheme(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalVoiceColors provides if (dark) nightInk else dayInk) {
        MaterialTheme(colorScheme = if (dark) night else daylight, typography = voiceTypography, content = content)
    }
}

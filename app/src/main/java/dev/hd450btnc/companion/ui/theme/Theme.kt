package dev.hd450btnc.companion.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hd450btnc.companion.R

/** AfterTune palette: AMOLED black with neutral near-black layers, white text, one blue accent used sparingly. */
@Immutable
data class StudioColors(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceHigh: Color,
    val border: Color,
    val borderStrong: Color,
    val text: Color,
    val textMuted: Color,
    val textFaint: Color,
    val accent: Color,
    val accentSoft: Color,
    val accentHighlight: Color,
    val badgeBackground: Color,
    val badgeText: Color,
    val badgeBorder: Color,
    val onAccent: Color,
    val warning: Color,
    val warningSoft: Color,
    val error: Color,
    val isDark: Boolean,
)

val DarkStudio = StudioColors(
    // True AMOLED black: pixels are off behind content; near-black layers separate surfaces.
    background = Color(0xFF000000),
    surface = Color(0xFF0A0A0A),
    surfaceRaised = Color(0xFF101010),
    surfaceHigh = Color(0xFF1A1A1A),
    border = Color(0xFF1F1F1F),
    borderStrong = Color(0xFF303030),
    text = Color(0xFFFFFFFF),
    textMuted = Color(0xFFA0A0A0),
    textFaint = Color(0xFF7C7C7C),
    accent = Color(0xFF5294FF),
    accentSoft = Color(0x265294FF),
    accentHighlight = Color(0xFF78ADFF),
    badgeBackground = Color(0xFF242D3A),
    badgeText = Color(0xFF78ADFF),
    badgeBorder = Color(0xFF42699D),
    onAccent = Color(0xFF04101F),
    warning = Color(0xFF78ADFF),
    warningSoft = Color(0x1F5294FF),
    error = Color(0xFFFF7A70),
    isDark = true,
)

val LightStudio = StudioColors(
    background = Color(0xFFF2F4F7),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFF7F9FB),
    surfaceHigh = Color(0xFFE8ECF1),
    border = Color(0xFFDCE2E9),
    borderStrong = Color(0xFFC2CBD6),
    text = Color(0xFF0F141A),
    textMuted = Color(0xFF4E5A68),
    textFaint = Color(0xFF636E7C),
    accent = Color(0xFF1F5FCC),
    accentSoft = Color(0x1A1F5FCC),
    accentHighlight = Color(0xFF1F5FCC),
    badgeBackground = Color(0xFFE6EEFB),
    badgeText = Color(0xFF1A4FA8),
    badgeBorder = Color(0xFF9DB8E6),
    onAccent = Color(0xFFFFFFFF),
    warning = Color(0xFF1A4FA8),
    warningSoft = Color(0x1A1F5FCC),
    error = Color(0xFFB3261E),
    isDark = false,
)

object StudioType {
    /** TeX Gyre Heros, a free Helvetica-style face (GUST Font License, bundled in res/font); falls back to the platform sans if a glyph is missing. */
    val inter = FontFamily(
        Font(R.font.heros_regular, FontWeight.Normal),
        Font(R.font.heros_regular, FontWeight.Medium),
        Font(R.font.heros_bold, FontWeight.SemiBold),
        Font(R.font.heros_bold, FontWeight.Bold),
    )
    private val sans = inter
    /** Technical values: same face with tabular figures so digits line up (no monospace face needed). */
    val mono = inter
    private const val TABULAR = "tnum"

    val display = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.4).sp)
    val title = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.2).sp)
    val heading = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp)
    val body = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp)
    val bodySmall = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp)
    val label = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp)
    val overline = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp)
    val monoSmall = TextStyle(fontFamily = mono, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, fontFeatureSettings = TABULAR)
    val monoValue = TextStyle(fontFamily = mono, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp, fontFeatureSettings = TABULAR)
    val monoLarge = TextStyle(fontFamily = mono, fontWeight = FontWeight.Medium, fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.5).sp, fontFeatureSettings = TABULAR)
}

object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 20.dp
    val xxl = 28.dp
    val screen = 20.dp
}

object Shapes {
    val card = RoundedCornerShape(16.dp)
    val control = RoundedCornerShape(12.dp)
    val small = RoundedCornerShape(6.dp)
    /** Floating glass surfaces (mini player) use a capsule-like radius so they read as separate from cards. */
    val glass = RoundedCornerShape(28.dp)
}

/** Material components (dialogs, text fields) pick up Inter too, so no widget falls back to Roboto. */
private fun interTypography(): Typography {
    val d = Typography()
    fun TextStyle.inter() = copy(fontFamily = StudioType.inter)
    return Typography(
        displayLarge = d.displayLarge.inter(), displayMedium = d.displayMedium.inter(), displaySmall = d.displaySmall.inter(),
        headlineLarge = d.headlineLarge.inter(), headlineMedium = d.headlineMedium.inter(), headlineSmall = d.headlineSmall.inter(),
        titleLarge = d.titleLarge.inter(), titleMedium = d.titleMedium.inter(), titleSmall = d.titleSmall.inter(),
        bodyLarge = d.bodyLarge.inter(), bodyMedium = d.bodyMedium.inter(), bodySmall = d.bodySmall.inter(),
        labelLarge = d.labelLarge.inter(), labelMedium = d.labelMedium.inter(), labelSmall = d.labelSmall.inter(),
    )
}

val LocalStudioColors = staticCompositionLocalOf { DarkStudio }

object Studio {
    val colors: StudioColors
        @Composable get() = LocalStudioColors.current
}

@Composable
fun StudioTheme(dark: Boolean, content: @Composable () -> Unit) {
    val c = if (dark) DarkStudio else LightStudio
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.accent, onPrimary = c.onAccent, secondary = c.accent, onSecondary = c.onAccent,
            background = c.background, onBackground = c.text, surface = c.surface, onSurface = c.text,
            surfaceVariant = c.surfaceHigh, onSurfaceVariant = c.textMuted, outline = c.borderStrong,
            outlineVariant = c.border, error = c.error,
            surfaceContainerLowest = c.surface, surfaceContainerLow = c.surfaceRaised, surfaceContainer = c.surfaceRaised,
            surfaceContainerHigh = c.surfaceRaised, surfaceContainerHighest = c.surfaceHigh,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = c.onAccent, secondary = c.accent, onSecondary = c.onAccent,
            background = c.background, onBackground = c.text, surface = c.surface, onSurface = c.text,
            surfaceVariant = c.surfaceHigh, onSurfaceVariant = c.textMuted, outline = c.borderStrong,
            outlineVariant = c.border, error = c.error,
            surfaceContainerLowest = c.surface, surfaceContainerLow = c.surface, surfaceContainer = c.surface,
            surfaceContainerHigh = c.surface, surfaceContainerHighest = c.surfaceHigh,
        )
    }
    CompositionLocalProvider(LocalStudioColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = interTypography(), content = content)
    }
}

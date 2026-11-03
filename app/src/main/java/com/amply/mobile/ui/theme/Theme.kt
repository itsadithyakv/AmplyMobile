package com.amply.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.amply.mobile.R

/**
 * The desktop app's design tokens (src/index.css on the desktop), so both clients read as one
 * product: a warm neumorphic surface in light mode, warm charcoal in dark, one amber accent.
 * Depth comes from paired light/dark shadows (see Neu.kt), not from surface colour changes.
 */
@Immutable
data class AmplyPalette(
    val isDark: Boolean,
    val bg: Color,
    val bgDeep: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val onAccent: Color,
    val shadowLight: Color,
    val shadowDark: Color,
    val edge: Color,
    val danger: Color,
)

// --amply-* light tokens; shadows use the softened 1.8.1 alphas (0.45 / 0.19).
val LightPalette = AmplyPalette(
    isDark = false,
    bg = Color(236, 232, 225),
    bgDeep = Color(229, 224, 216),
    textPrimary = Color(35, 31, 27),
    textSecondary = Color(84, 76, 67),
    textMuted = Color(110, 100, 88),
    accent = Color(235, 126, 35),
    onAccent = Color(28, 22, 16),
    shadowLight = Color.White.copy(alpha = 0.45f),
    shadowDark = Color(163, 150, 132).copy(alpha = 0.19f),
    edge = Color(120, 105, 88).copy(alpha = 0.12f),
    danger = Color(196, 66, 58),
)

// :root[data-theme='dark'] tokens.
val DarkPalette = AmplyPalette(
    isDark = true,
    bg = Color(28, 27, 25),
    bgDeep = Color(22, 21, 19),
    textPrimary = Color(240, 237, 231),
    textSecondary = Color(178, 172, 163),
    textMuted = Color(150, 143, 134),
    accent = Color(240, 138, 52),
    onAccent = Color(28, 22, 16),
    shadowLight = Color.White.copy(alpha = 0.03f),
    shadowDark = Color.Black.copy(alpha = 0.34f),
    edge = Color.White.copy(alpha = 0.07f),
    danger = Color(226, 96, 86),
)

val LocalAmplyPalette = staticCompositionLocalOf { LightPalette }

/** Shorthand for the current palette inside composables. */
val Palette: AmplyPalette
    @Composable @ReadOnlyComposable get() = LocalAmplyPalette.current

// Accent is the same hue in both themes, and is also used inside Canvas draw blocks, so it stays a constant.
val AmplyOrange = Color(235, 126, 35)
val AmplyOrangeSoft = Color(0xFFF59A4C)
val AmplyLime = AmplyOrange
val AmplyOnAccent = Color(28, 22, 16)

// Older names used across the screens, now theme-aware.
val AmplyBg: Color @Composable @ReadOnlyComposable get() = Palette.bg
val AmplyBgPurple: Color @Composable @ReadOnlyComposable get() = Palette.bgDeep
val AmplyPanel: Color @Composable @ReadOnlyComposable get() = Palette.bgDeep
val AmplyGlass: Color @Composable @ReadOnlyComposable get() = Palette.bg
val AmplyCard: Color @Composable @ReadOnlyComposable get() = Palette.bg
val AmplyText: Color @Composable @ReadOnlyComposable get() = Palette.textPrimary
val AmplyMuted: Color @Composable @ReadOnlyComposable get() = Palette.textSecondary

/** ZT Nature, the desktop app's only typeface, for every text style. */
val ZtNature = FontFamily(
    Font(R.font.zt_nature_thin, FontWeight.Thin),
    Font(R.font.zt_nature_regular, FontWeight.Normal),
    Font(R.font.zt_nature_medium, FontWeight.Medium),
    Font(R.font.zt_nature_bold, FontWeight.Bold),
    Font(R.font.zt_nature_black, FontWeight.Black),
)

/** The wordmark is ZT Nature Black with tight tracking, as on desktop (it used to be Octosale). */
val AmplyBrand = ZtNature

/**
 * Material's default tracking (0.25-0.5sp on body text) spreads ZT Nature out until it looks
 * unfinished; the desktop renders it at normal spacing with tight, heavy headings.
 */
private val typography = Typography().let { base ->
    fun TextStyle.z(weight: FontWeight, size: Int, line: Int, tracking: Float = 0f) =
        copy(fontFamily = ZtNature, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = tracking.em)
    Typography(
        displayLarge = base.displayLarge.z(FontWeight.Black, 48, 52, -0.045f),
        displayMedium = base.displayMedium.z(FontWeight.Black, 40, 44, -0.045f),
        displaySmall = base.displaySmall.z(FontWeight.Black, 30, 34, -0.045f),
        headlineLarge = base.headlineLarge.z(FontWeight.Black, 30, 36, -0.03f),
        headlineMedium = base.headlineMedium.z(FontWeight.Black, 26, 32, -0.03f),
        headlineSmall = base.headlineSmall.z(FontWeight.Bold, 22, 28, -0.02f),
        titleLarge = base.titleLarge.z(FontWeight.Bold, 19, 24, -0.01f),
        titleMedium = base.titleMedium.z(FontWeight.Bold, 16, 22),
        titleSmall = base.titleSmall.z(FontWeight.Medium, 14, 20),
        bodyLarge = base.bodyLarge.z(FontWeight.Normal, 16, 22),
        bodyMedium = base.bodyMedium.z(FontWeight.Normal, 14, 20),
        bodySmall = base.bodySmall.z(FontWeight.Medium, 13, 18),
        labelLarge = base.labelLarge.z(FontWeight.Medium, 14, 18, 0.01f),
        labelMedium = base.labelMedium.z(FontWeight.Medium, 12, 16, 0.01f),
        labelSmall = base.labelSmall.z(FontWeight.Medium, 11, 14, 0.01f),
    )
}

/** True black (Pro, dark only): OLED-friendly surfaces; shadows can't show on black, so a hairline edge separates them. */
val TrueBlackPalette = DarkPalette.copy(
    bg = Color(4, 4, 5),
    bgDeep = Color(0, 0, 0),
    shadowLight = Color.White.copy(alpha = 0.05f),
    shadowDark = Color.Black.copy(alpha = 0.9f),
    edge = Color.White.copy(alpha = 0.1f),
)

/** Dark text on light accents (amber, gold), white on deep ones (violet, blue). */
private fun onAccentFor(accent: Color): Color =
    if (accent.luminance() > 0.36f) Color(28, 22, 16) else Color.White

@Composable
fun AmplyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentLight: Color = LightPalette.accent,
    accentDark: Color = DarkPalette.accent,
    trueBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    val base = when {
        darkTheme && trueBlack -> TrueBlackPalette
        darkTheme -> DarkPalette
        else -> LightPalette
    }
    val accent = if (darkTheme) accentDark else accentLight
    val palette = base.copy(accent = accent, onAccent = onAccentFor(accent))
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = palette.accent, onPrimary = palette.onAccent,
            secondary = palette.accent, onSecondary = palette.onAccent,
            background = palette.bg, onBackground = palette.textPrimary,
            surface = palette.bg, onSurface = palette.textPrimary,
            surfaceVariant = palette.bgDeep, onSurfaceVariant = palette.textSecondary,
            surfaceContainer = palette.bg, surfaceContainerHigh = palette.bg, surfaceContainerLow = palette.bg,
            outline = palette.textMuted.copy(alpha = 0.4f), error = palette.danger,
        )
    } else {
        lightColorScheme(
            primary = palette.accent, onPrimary = palette.onAccent,
            secondary = palette.accent, onSecondary = palette.onAccent,
            background = palette.bg, onBackground = palette.textPrimary,
            surface = palette.bg, onSurface = palette.textPrimary,
            surfaceVariant = palette.bgDeep, onSurfaceVariant = palette.textSecondary,
            surfaceContainer = palette.bg, surfaceContainerHigh = palette.bg, surfaceContainerLow = palette.bg,
            outline = palette.textMuted.copy(alpha = 0.4f), error = palette.danger,
        )
    }
    CompositionLocalProvider(LocalAmplyPalette provides palette) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}

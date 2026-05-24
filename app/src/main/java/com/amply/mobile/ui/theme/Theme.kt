package com.amply.mobile.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Typography
import com.amply.mobile.R

val AmplyOrange = Color(0xFFF29030)
val AmplyOrangeSoft = Color(0xFFFFA24A)
val AmplyLime = AmplyOrange
val AmplyBg = Color(0xFF090909)
val AmplyBgPurple = Color(0xFF171717)
val AmplyPanel = Color(0xFF141414)
val AmplyGlass = Color(0xE61E1E1E)
val AmplyCard = Color(0xFF202020)
val AmplyText = Color(0xFFF8F5EF)
val AmplyMuted = Color(0xFFA7A2AF)

val ZtNature = FontFamily(
    Font(R.font.gilroy_light, FontWeight.Light),
    Font(R.font.gilroy_regular, FontWeight.Normal),
    Font(R.font.gilroy_medium, FontWeight.Medium),
    Font(R.font.gilroy_bold, FontWeight.Bold),
    Font(R.font.gilroy_heavy, FontWeight.Black),
)

val AmplyBrand = FontFamily(
    Font(R.font.octosale, FontWeight.Normal),
)

private val colorScheme: ColorScheme = darkColorScheme(
    primary = AmplyOrange,
    onPrimary = Color(0xFF120B02),
    secondary = AmplyOrange,
    onSecondary = Color(0xFF160B02),
    background = AmplyBg,
    onBackground = AmplyText,
    surface = AmplyPanel,
    onSurface = AmplyText,
    surfaceVariant = AmplyCard,
    onSurfaceVariant = AmplyMuted,
    outline = Color(0xFF3A3A3A),
)

private val typography = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.withBrandFont(),
        displayMedium = base.displayMedium.withBrandFont(),
        displaySmall = base.displaySmall.withBrandFont(),
        headlineLarge = base.headlineLarge.withFont(),
        headlineMedium = base.headlineMedium.withFont(),
        headlineSmall = base.headlineSmall.withFont(),
        titleLarge = base.titleLarge.withFont(FontWeight.Bold),
        titleMedium = base.titleMedium.withFont(FontWeight.Medium),
        titleSmall = base.titleSmall.withFont(FontWeight.Medium),
        bodyLarge = base.bodyLarge.withFont(),
        bodyMedium = base.bodyMedium.withFont(),
        bodySmall = base.bodySmall.withFont(),
        labelLarge = base.labelLarge.withFont(FontWeight.Medium),
        labelMedium = base.labelMedium.withFont(FontWeight.Medium),
        labelSmall = base.labelSmall.withFont(FontWeight.Medium),
    )
}

private fun TextStyle.withFont(weight: FontWeight? = null): TextStyle =
    copy(fontFamily = ZtNature, fontWeight = weight ?: fontWeight)

private fun TextStyle.withBrandFont(): TextStyle =
    copy(fontFamily = AmplyBrand, fontWeight = FontWeight.Normal)

@Composable
fun AmplyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content,
    )
}

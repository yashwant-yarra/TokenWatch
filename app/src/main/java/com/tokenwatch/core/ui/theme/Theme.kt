package com.tokenwatch.core.ui.theme

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.tokenwatch.R
import com.tokenwatch.core.ui.components.InkWash

val LocalIsDarkTheme = staticCompositionLocalOf { false }

// Material roles mapped onto the bench. Primary is the glass ink itself, so filled buttons read as
// engraved plates rather than a brand color; error is the held-back alert vermilion.
private val DarkColors = darkColorScheme(
    primary = Bench.DarkInk,
    onPrimary = Bench.DarkGround,
    primaryContainer = Bench.DarkRaisedHigh,
    onPrimaryContainer = Bench.DarkInk,
    secondary = Bench.DarkInkMuted,
    onSecondary = Bench.DarkGround,
    secondaryContainer = Bench.DarkRaisedHigh,
    onSecondaryContainer = Bench.DarkInk,
    tertiary = Bench.DarkInkMuted,
    background = Bench.DarkGround,
    onBackground = Bench.DarkInk,
    surface = Bench.DarkGround,
    onSurface = Bench.DarkInk,
    surfaceVariant = Bench.DarkRaised,
    onSurfaceVariant = Bench.DarkInkMuted,
    surfaceContainerLowest = Bench.DarkGround,
    surfaceContainerLow = Bench.DarkRaised,
    surfaceContainer = Bench.DarkRaised,
    surfaceContainerHigh = Bench.DarkRaisedHigh,
    surfaceContainerHighest = Bench.DarkRaisedHigh,
    outline = Bench.DarkGlass,
    outlineVariant = Color(0xFF3A332B),
    error = Bench.DarkAlert,
    onError = Bench.DarkGround,
    errorContainer = Bench.DarkAlertSlip,
    onErrorContainer = Color(0xFFFFD9CF),
    inverseSurface = Bench.DarkInk,
    inverseOnSurface = Bench.DarkGround,
)

private val LightColors = lightColorScheme(
    primary = Bench.LightInk,
    onPrimary = Bench.LightRaised,
    primaryContainer = Bench.LightRaisedHigh,
    onPrimaryContainer = Bench.LightInk,
    secondary = Bench.LightInkMuted,
    onSecondary = Bench.LightRaised,
    secondaryContainer = Bench.LightRaisedHigh,
    onSecondaryContainer = Bench.LightInk,
    tertiary = Bench.LightInkMuted,
    background = Bench.LightGround,
    onBackground = Bench.LightInk,
    surface = Bench.LightGround,
    onSurface = Bench.LightInk,
    surfaceVariant = Bench.LightRaised,
    onSurfaceVariant = Bench.LightInkMuted,
    surfaceContainerLowest = Bench.LightRaised,
    surfaceContainerLow = Bench.LightRaised,
    surfaceContainer = Bench.LightRaised,
    surfaceContainerHigh = Bench.LightRaisedHigh,
    surfaceContainerHighest = Bench.LightRaisedHigh,
    outline = Bench.LightGlass,
    outlineVariant = Color(0xFFCBCEC9),
    error = Bench.LightAlert,
    onError = Bench.LightRaised,
    errorContainer = Bench.LightAlertSlip,
    onErrorContainer = Color(0xFF5A1405),
    inverseSurface = Bench.LightInk,
    inverseOnSurface = Bench.LightRaised,
)

private val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** Barlow Condensed: the engraved face of the glasses' numerals and scale marks. */
val Engraved = FontFamily(
    Font(R.font.barlow_condensed_medium, FontWeight.Medium),
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
)

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0, family: FontFamily = Inter) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
    // Tabular figures everywhere, so percentages and countdowns don't shift as they update.
    fontFeatureSettings = "tnum",
)

/** Every percent in the app uses this one size; rank comes from weight and order, never size. */
val Numeral = style(40, 42, FontWeight.SemiBold, -0.5, Engraved)

// Headings set in the engraved face, prose in Inter.
private val AppTypography = Typography(
    displayLarge = style(56, 60, FontWeight.SemiBold, -0.5, Engraved),
    displayMedium = style(46, 50, FontWeight.SemiBold, -0.4, Engraved),
    displaySmall = style(38, 42, FontWeight.SemiBold, -0.3, Engraved),
    headlineLarge = style(34, 38, FontWeight.SemiBold, -0.2, Engraved),
    headlineMedium = style(30, 34, FontWeight.SemiBold, -0.2, Engraved),
    headlineSmall = style(24, 28, FontWeight.SemiBold, -0.1, Engraved),
    titleLarge = style(22, 26, FontWeight.SemiBold, 0.0, Engraved),
    titleMedium = style(16, 22, FontWeight.SemiBold, -0.1),
    titleSmall = style(14, 20, FontWeight.SemiBold),
    bodyLarge = style(16, 24, FontWeight.Normal),
    bodyMedium = style(14, 20, FontWeight.Normal),
    bodySmall = style(12, 16, FontWeight.Normal),
    labelLarge = style(14, 20, FontWeight.Medium),
    labelMedium = style(12, 16, FontWeight.Medium),
    labelSmall = style(11, 14, FontWeight.Medium, 0.2),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TokenWatchTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
        val colors = if (darkTheme) DarkColors else LightColors
        MaterialTheme(colorScheme = colors, typography = AppTypography) {
            // No platform ripple or stretch: presses wash with ink, lists stop at their edge.
            CompositionLocalProvider(
                LocalIndication provides InkWash(colors.onSurface),
                LocalOverscrollConfiguration provides null,
                content = content,
            )
        }
    }
}

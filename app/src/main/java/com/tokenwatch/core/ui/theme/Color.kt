package com.tokenwatch.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.tokenwatch.domain.model.AiProvider

// The Running Glass: a smoked-bronze bench (dark) or porcelain bench (light), glasses drawn in
// warm glass ink, sand in each provider's color, and one vermilion held back for alerts only.
internal object Bench {
    val DarkGround = Color(0xFF14110E)
    val DarkRaised = Color(0xFF1E1A16)
    val DarkRaisedHigh = Color(0xFF29241E)
    val DarkInk = Color(0xFFEDE3D2)
    val DarkInkMuted = Color(0xFFAB9D88)
    val DarkGlass = Color(0xFF8C7F6C)
    val DarkAlert = Color(0xFFFF6A4D)
    val DarkAlertSlip = Color(0xFF3A1A12)

    val LightGround = Color(0xFFECEEEC)
    val LightRaised = Color(0xFFF7F8F7)
    val LightRaisedHigh = Color(0xFFDFE2DF)
    val LightInk = Color(0xFF1C1A17)
    val LightInkMuted = Color(0xFF5C564D)
    val LightGlass = Color(0xFF8D877E)
    val LightAlert = Color(0xFFC7361B)
    val LightAlertSlip = Color(0xFFF8DED6)
}

object ProviderColors {
    private val light = mapOf(
        AiProvider.CLAUDE to Color(0xFFA8741A),
        AiProvider.CODEX to Color(0xFF0B7A61),
        AiProvider.GEMINI to Color(0xFF5544C8),
    )
    private val dark = mapOf(
        AiProvider.CLAUDE to Color(0xFFDDB061),
        AiProvider.CODEX to Color(0xFF5ED6B4),
        AiProvider.GEMINI to Color(0xFFA699FF),
    )

    /** The sand color for a provider's glasses. */
    @Composable
    @ReadOnlyComposable
    fun of(provider: AiProvider): Color = (if (LocalIsDarkTheme.current) dark else light).getValue(provider)

    /** For views outside Compose, such as home-screen widgets. */
    fun argb(provider: AiProvider, isDark: Boolean): Int = (if (isDark) dark else light).getValue(provider).toArgb()
}

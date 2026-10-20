package com.tokenwatch.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.tokenwatch.domain.model.AiProvider

// The Running Glass: a graphite bench (dark) or porcelain bench (light), glasses drawn in neutral
// glass ink, sand in each provider's own brand color, and one rose red held back for alerts only.
internal object Bench {
    val DarkGround = Color(0xFF0B0C0D)
    val DarkRaised = Color(0xFF16181A)
    val DarkRaisedHigh = Color(0xFF202326)
    val DarkInk = Color(0xFFECEEF0)
    val DarkInkMuted = Color(0xFF8E949A)
    val DarkGlass = Color(0xFF6E747A)
    val DarkHairline = Color(0xFF24272A)
    val DarkAlert = Color(0xFFFF5468)
    val DarkAlertSlip = Color(0xFF3A1419)
    val DarkOnAlertSlip = Color(0xFFFFD6DC)

    val LightGround = Color(0xFFECEEEC)
    val LightRaised = Color(0xFFF7F8F7)
    val LightRaisedHigh = Color(0xFFDFE2DF)
    val LightInk = Color(0xFF1C1A17)
    val LightInkMuted = Color(0xFF5C564D)
    val LightGlass = Color(0xFF8D877E)
    val LightHairline = Color(0xFFCBCEC9)
    val LightAlert = Color(0xFFC4243A)
    val LightAlertSlip = Color(0xFFFADADF)
    val LightOnAlertSlip = Color(0xFF5C0C1A)
}

// Each sand is the provider's own brand hue (Claude terracotta, OpenAI green, Gemini blue), tuned
// per ground: brighter on graphite, deeper on porcelain for at least 3:1 against it.
object ProviderColors {
    private val light = mapOf(
        AiProvider.CLAUDE to Color(0xFFC15F3C),
        AiProvider.CODEX to Color(0xFF0A7F62),
        AiProvider.GEMINI to Color(0xFF2F6FD6),
    )
    private val dark = mapOf(
        AiProvider.CLAUDE to Color(0xFFE08B6B),
        AiProvider.CODEX to Color(0xFF3CCB9C),
        AiProvider.GEMINI to Color(0xFF6FA8FF),
    )

    /** The sand color for a provider's glasses. */
    @Composable
    @ReadOnlyComposable
    fun of(provider: AiProvider): Color = (if (LocalIsDarkTheme.current) dark else light).getValue(provider)

    /** For views outside Compose, such as home-screen widgets. */
    fun argb(provider: AiProvider, isDark: Boolean): Int = (if (isDark) dark else light).getValue(provider).toArgb()
}

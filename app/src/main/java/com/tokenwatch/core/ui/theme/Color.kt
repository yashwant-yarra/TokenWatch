package com.tokenwatch.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.tokenwatch.domain.model.AiProvider

// Ink-blue brand on cool neutrals; each provider gets its own accent so cards scan quickly.
internal val InkBlue = Color(0xFF2F4AA8)
internal val InkBlueLight = Color(0xFFB8C4FF)

object ProviderColors {
    private val light = mapOf(
        AiProvider.CLAUDE to Color(0xFFB8741A),
        AiProvider.CODEX to Color(0xFF0F8A6E),
        AiProvider.GEMINI to Color(0xFF2F6FDB),
    )
    private val dark = mapOf(
        AiProvider.CLAUDE to Color(0xFFF0B45E),
        AiProvider.CODEX to Color(0xFF5ED6B4),
        AiProvider.GEMINI to Color(0xFF8FB4FF),
    )

    @Composable
    @ReadOnlyComposable
    fun of(provider: AiProvider): Color = (if (LocalIsDarkTheme.current) dark else light).getValue(provider)

    /** For views outside Compose, such as home-screen widgets. */
    fun argb(provider: AiProvider, isDark: Boolean): Int = (if (isDark) dark else light).getValue(provider).toArgb()
}

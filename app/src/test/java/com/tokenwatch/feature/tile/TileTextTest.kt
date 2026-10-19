package com.tokenwatch.feature.tile

import com.tokenwatch.domain.model.AiProvider
import com.tokenwatch.domain.model.ConnectionStatus
import com.tokenwatch.domain.model.IntegrationStatus
import com.tokenwatch.domain.model.ProviderCapabilities
import com.tokenwatch.domain.model.ProviderConnection
import com.tokenwatch.domain.model.ProviderSnapshot
import com.tokenwatch.domain.model.QuotaWindow
import com.tokenwatch.domain.model.QuotaWindowType
import com.tokenwatch.domain.model.UsageFailure
import com.tokenwatch.domain.model.UsageSource
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class TileTextTest {
    private val now = Instant.parse("2026-10-19T12:00:00Z")

    private fun window(provider: AiProvider, label: String, used: Double?, resetsIn: Duration?) = QuotaWindow(
        provider = provider,
        windowId = label,
        label = label,
        windowType = if (label == "Weekly") QuotaWindowType.WEEKLY else QuotaWindowType.ROLLING_HOURS,
        usedPercent = used,
        resetsAt = resetsIn?.let(now::plus),
        observedAt = now,
        source = UsageSource.WEB_PAGE,
    )

    private fun snapshot(
        provider: AiProvider,
        vararg windows: QuotaWindow,
        status: ConnectionStatus = ConnectionStatus.CONNECTED,
        failure: UsageFailure? = null,
    ) = ProviderSnapshot(
        connection = ProviderConnection(provider, status, IntegrationStatus.PARTIAL_SUPPORT, ProviderCapabilities.NONE),
        quotaWindows = windows.toList(),
        lastFailure = failure,
    )

    @Test
    fun showsTheWindowClosestToEmpty() {
        val text = tileText(
            listOf(
                snapshot(AiProvider.CLAUDE, window(AiProvider.CLAUDE, "Current session", 64.0, Duration.ofMinutes(72)), window(AiProvider.CLAUDE, "Weekly", 20.0, null)),
                snapshot(AiProvider.CODEX, window(AiProvider.CODEX, "Weekly", 7.0, Duration.ofDays(5))),
            ),
            now,
        )
        assertEquals(TileText("Claude · 36% left", "Current session · resets in 1h 12m", active = true), text)
    }

    @Test
    fun withoutAResetTimeTheSubtitleIsJustTheWindow() {
        val text = tileText(listOf(snapshot(AiProvider.GEMINI, window(AiProvider.GEMINI, "Weekly", 50.0, null))), now)
        assertEquals(TileText("Gemini · 50% left", "Weekly", active = true), text)
    }

    @Test
    fun aFailedReadSaysTheNumberIsOld() {
        val text = tileText(
            listOf(snapshot(AiProvider.CODEX, window(AiProvider.CODEX, "Weekly", 7.0, null), failure = UsageFailure.Network)),
            now,
        )
        assertEquals(TileText("Codex · 93% left", "Weekly · last reading", active = true), text)
    }

    @Test
    fun disconnectedProvidersAndMissingPercentsAreIgnored() {
        val text = tileText(
            listOf(
                snapshot(AiProvider.CLAUDE, window(AiProvider.CLAUDE, "Weekly", 99.0, null), status = ConnectionStatus.DISCONNECTED),
                snapshot(AiProvider.CODEX, window(AiProvider.CODEX, "Weekly", null, null)),
            ),
            now,
        )
        assertEquals(TileText("TokenWatch", "No readings yet", active = false), text)
    }
}

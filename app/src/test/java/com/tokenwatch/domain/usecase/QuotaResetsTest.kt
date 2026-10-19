package com.tokenwatch.domain.usecase

import com.tokenwatch.domain.model.AiProvider
import com.tokenwatch.domain.model.ConnectionStatus
import com.tokenwatch.domain.model.IntegrationStatus
import com.tokenwatch.domain.model.ProviderCapabilities
import com.tokenwatch.domain.model.ProviderConnection
import com.tokenwatch.domain.model.ProviderSnapshot
import com.tokenwatch.domain.model.QuotaWindow
import com.tokenwatch.domain.model.QuotaWindowType
import com.tokenwatch.domain.model.UsageSource
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class QuotaResetsTest {
    private val resetAt = Instant.parse("2026-10-19T15:00:00Z")
    private val before = resetAt.minus(Duration.ofHours(1))
    private val after = resetAt.plus(Duration.ofMinutes(2))

    private fun window(id: String, used: Double?, resetsAt: Instant?, observedAt: Instant) = QuotaWindow(
        provider = AiProvider.CLAUDE,
        windowId = id,
        label = id,
        windowType = if (id == "weekly") QuotaWindowType.WEEKLY else QuotaWindowType.ROLLING_HOURS,
        usedPercent = used,
        resetsAt = resetsAt,
        observedAt = observedAt,
        source = UsageSource.WEB_PAGE,
    )

    private fun snapshot(vararg windows: QuotaWindow) = ProviderSnapshot(
        connection = ProviderConnection(
            provider = AiProvider.CLAUDE,
            status = ConnectionStatus.CONNECTED,
            integrationStatus = IntegrationStatus.PARTIAL_SUPPORT,
            capabilities = ProviderCapabilities.NONE,
        ),
        quotaWindows = windows.toList(),
    )

    private fun resets(old: ProviderSnapshot?, new: ProviderSnapshot) = quotaResets(old, new).map { it.windowId }

    @Test
    fun aPassedResetWithUsageDroppedIsConfirmed() {
        val old = snapshot(window("session", 64.0, resetAt, before))
        val new = snapshot(window("session", 0.0, null, after))
        assertEquals(listOf("session"), resets(old, new))
    }

    @Test
    fun aNewResetTimeAlsoConfirmsIt() {
        // Usage was already back to its old level, but the page moved on to the next window.
        val old = snapshot(window("weekly", 20.0, resetAt, before))
        val new = snapshot(window("weekly", 20.0, resetAt.plus(Duration.ofDays(7)), after))
        assertEquals(listOf("weekly"), resets(old, new))
    }

    @Test
    fun aResetTimeThatPassedWithoutAnyChangeIsNotAnnounced() {
        // The page hasn't caught up yet; saying "reset" now would be a guess.
        val old = snapshot(window("session", 64.0, resetAt, before))
        val new = snapshot(window("session", 64.0, resetAt, after))
        assertEquals(emptyList<String>(), resets(old, new))
    }

    @Test
    fun aDropBeforeTheResetTimeIsNotAReset() {
        val old = snapshot(window("session", 64.0, resetAt, before))
        val new = snapshot(window("session", 10.0, resetAt, before.plus(Duration.ofMinutes(5))))
        assertEquals(emptyList<String>(), resets(old, new))
    }

    @Test
    fun anUnusedWindowHasNothingToAnnounce() {
        val old = snapshot(window("session", 0.0, resetAt, before))
        val new = snapshot(window("session", 0.0, resetAt.plus(Duration.ofHours(5)), after))
        assertEquals(emptyList<String>(), resets(old, new))
    }

    @Test
    fun onlyTheWindowsThatResetAreReported() {
        val old = snapshot(window("session", 64.0, resetAt, before), window("weekly", 30.0, resetAt.plus(Duration.ofDays(3)), before))
        val new = snapshot(window("session", 0.0, null, after), window("weekly", 31.0, resetAt.plus(Duration.ofDays(3)), after))
        assertEquals(listOf("session"), resets(old, new))
    }

    @Test
    fun theFirstReadHasNothingToCompare() {
        assertEquals(emptyList<String>(), resets(null, snapshot(window("session", 0.0, null, after))))
    }
}

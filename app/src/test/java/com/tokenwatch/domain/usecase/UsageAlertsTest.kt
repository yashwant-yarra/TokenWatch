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
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class UsageAlertsTest {
    private fun snapshot(session: Double?, weekly: Double?) = ProviderSnapshot(
        connection = ProviderConnection(
            provider = AiProvider.CLAUDE,
            status = ConnectionStatus.CONNECTED,
            integrationStatus = IntegrationStatus.PARTIAL_SUPPORT,
            capabilities = ProviderCapabilities.NONE,
        ),
        quotaWindows = listOf(
            window("current_session", QuotaWindowType.ROLLING_HOURS, session),
            window("weekly_limit", QuotaWindowType.WEEKLY, weekly),
        ),
    )

    private fun window(id: String, type: QuotaWindowType, used: Double?) = QuotaWindow(
        provider = AiProvider.CLAUDE,
        windowId = id,
        label = id,
        windowType = type,
        usedPercent = used,
        observedAt = Instant.EPOCH,
        source = UsageSource.WEB_PAGE,
    )

    private fun alerted(old: ProviderSnapshot?, new: ProviderSnapshot) =
        usageAlerts(old, new, sessionLimit = 80, weeklyLimit = 90).map { it.window.windowId to it.limitPercent }

    @Test
    fun sessionAndWeeklyUseTheirOwnLimits() {
        assertEquals(listOf("current_session" to 80), alerted(snapshot(70.0, 70.0), snapshot(85.0, 85.0)))
        assertEquals(listOf("weekly_limit" to 90), alerted(snapshot(10.0, 85.0), snapshot(10.0, 90.0)))
    }

    @Test
    fun aCrossingIsAnnouncedOnceNotOnEveryRefresh() {
        assertEquals(emptyList<Pair<String, Int>>(), alerted(snapshot(85.0, 10.0), snapshot(95.0, 10.0)))
    }

    @Test
    fun firstReadAboveTheLimitAlertsAndMissingPercentsNever() {
        assertEquals(listOf("current_session" to 80), alerted(null, snapshot(81.0, null)))
    }

    @Test
    fun aWindowThatResetAndClimbsAgainAlertsAgain() {
        assertEquals(listOf("current_session" to 80), alerted(snapshot(5.0, 10.0), snapshot(82.0, 10.0)))
    }
}

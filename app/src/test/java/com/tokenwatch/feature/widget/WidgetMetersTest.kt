package com.tokenwatch.feature.widget

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

class WidgetMetersTest {
    private fun snapshot(provider: AiProvider, status: ConnectionStatus, vararg windows: Pair<QuotaWindowType, Double>) = ProviderSnapshot(
        connection = ProviderConnection(
            provider = provider,
            status = status,
            integrationStatus = IntegrationStatus.PARTIAL_SUPPORT,
            capabilities = ProviderCapabilities.NONE,
        ),
        quotaWindows = windows.map { (type, used) ->
            QuotaWindow(provider, type.name, type.name, type, usedPercent = used, observedAt = Instant.EPOCH, source = UsageSource.WEB_PAGE)
        },
    )

    @Test
    fun eachWidgetPicksItsOwnWindowInFixedProviderOrder() {
        val snapshots = listOf(
            // Listed out of order and with weekly first, like the page reader can return them.
            snapshot(AiProvider.GEMINI, ConnectionStatus.CONNECTED, QuotaWindowType.WEEKLY to 5.0, QuotaWindowType.ROLLING_HOURS to 1.0),
            snapshot(AiProvider.CLAUDE, ConnectionStatus.CONNECTED, QuotaWindowType.WEEKLY to 35.0, QuotaWindowType.ROLLING_HOURS to 38.0),
        )
        assertEquals(listOf(38.0, null, 1.0), widgetMeters(snapshots, WidgetKind.SESSION).map { it.usedPercent })
        assertEquals(listOf(35.0, null, 5.0), widgetMeters(snapshots, WidgetKind.WEEKLY).map { it.usedPercent })
        assertEquals(AiProvider.entries, widgetMeters(snapshots, WidgetKind.WEEKLY).map { it.provider })
    }

    @Test
    fun signedOutProviderShowsNoPercentInsteadOfOldData() {
        val snapshots = listOf(snapshot(AiProvider.CODEX, ConnectionStatus.DISCONNECTED, QuotaWindowType.WEEKLY to 80.0))
        assertEquals(null, widgetMeters(snapshots, WidgetKind.WEEKLY).first { it.provider == AiProvider.CODEX }.usedPercent)
    }
}

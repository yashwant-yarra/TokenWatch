package com.tokenwatch.feature.widget

import com.tokenwatch.core.ui.components.AlertLimits
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
            snapshot(AiProvider.COPILOT, ConnectionStatus.CONNECTED, QuotaWindowType.MONTHLY to 25.0),
        )
        // Copilot has only a monthly window: it sits on the weekly widget and is left off the session one.
        assertEquals(listOf(38.0, null, 1.0), widgetMeters(snapshots, WidgetKind.SESSION).map { it.usedPercent })
        assertEquals(listOf(35.0, null, 5.0, 25.0), widgetMeters(snapshots, WidgetKind.WEEKLY).map { it.usedPercent })
        assertEquals(AiProvider.entries - AiProvider.COPILOT, widgetMeters(snapshots, WidgetKind.SESSION).map { it.provider })
        assertEquals("MONTHLY", widgetMeters(snapshots, WidgetKind.WEEKLY).last().windowLabel)
    }

    @Test
    fun combinedWidgetShowsSessionAsTheGlassAndWeeklyAsTheBar() {
        val snapshots = listOf(
            snapshot(AiProvider.CLAUDE, ConnectionStatus.CONNECTED, QuotaWindowType.WEEKLY to 35.0, QuotaWindowType.ROLLING_HOURS to 38.0),
            snapshot(AiProvider.CODEX, ConnectionStatus.DISCONNECTED, QuotaWindowType.WEEKLY to 80.0, QuotaWindowType.ROLLING_HOURS to 10.0),
            snapshot(AiProvider.COPILOT, ConnectionStatus.CONNECTED, QuotaWindowType.MONTHLY to 25.0),
        )
        val meters = combinedMeters(snapshots)
        assertEquals(AiProvider.entries, meters.map { it.first.provider })
        // Glass, bar: session and weekly; nothing for a signed-out provider; Copilot's monthly window in both.
        assertEquals(
            listOf(38.0 to 35.0, null to null, null to null, 25.0 to 25.0),
            meters.map { (glass, bar) -> glass.usedPercent to bar.usedPercent },
        )
    }

    @Test
    fun metersUseTheUsersAlertLimitsPerWindowKind() {
        val snapshots = listOf(
            snapshot(AiProvider.CLAUDE, ConnectionStatus.CONNECTED, QuotaWindowType.WEEKLY to 83.0, QuotaWindowType.ROLLING_HOURS to 50.0),
            snapshot(AiProvider.COPILOT, ConnectionStatus.CONNECTED, QuotaWindowType.MONTHLY to 85.0),
        )
        val limits = AlertLimits(session = 60, weekly = 80)
        val (claudeGlass, claudeBar) = combinedMeters(snapshots, limits).first()
        assertEquals(60 to false, claudeGlass.alertUsed to claudeGlass.pastLine)
        assertEquals(80 to true, claudeBar.alertUsed to claudeBar.pastLine)
        // Copilot's monthly glass uses the weekly limit, as alerts do.
        assertEquals(80 to true, combinedMeters(snapshots, limits).last().first.let { it.alertUsed to it.pastLine })
        // Alerts off: no line, and only nearly full windows (90% used) read as past it.
        val off = combinedMeters(snapshots).first()
        assertEquals(listOf(null to false, null to false), listOf(off.first, off.second).map { it.alertUsed to it.pastLine })
    }

    @Test
    fun signedOutProviderShowsNoPercentInsteadOfOldData() {
        val snapshots = listOf(snapshot(AiProvider.CODEX, ConnectionStatus.DISCONNECTED, QuotaWindowType.WEEKLY to 80.0))
        assertEquals(null, widgetMeters(snapshots, WidgetKind.WEEKLY).first { it.provider == AiProvider.CODEX }.usedPercent)
    }
}

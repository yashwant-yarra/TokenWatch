package com.tokenwatch.feature.dashboard

import com.tokenwatch.core.ui.components.AlertLimits
import com.tokenwatch.domain.model.AiProvider
import com.tokenwatch.domain.model.QuotaWindow
import com.tokenwatch.domain.model.QuotaWindowType
import com.tokenwatch.domain.model.UsageSource
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class VerdictTest {
    private val now = Instant.parse("2026-10-20T10:00:00Z")
    private val limits = AlertLimits(session = 90, weekly = 80)

    private fun window(label: String, type: QuotaWindowType, used: Double, resetsIn: Duration?) = AiProvider.CLAUDE to QuotaWindow(
        provider = AiProvider.CLAUDE,
        windowId = label,
        label = label,
        windowType = type,
        usedPercent = used,
        resetsAt = resetsIn?.let(now::plus),
        observedAt = now,
        source = UsageSource.WEB_PAGE,
    )

    @Test
    fun allClearNamesTheLowestWindowAndWhenItResets() {
        val windows = listOf(
            window("Current session", QuotaWindowType.ROLLING_HOURS, 15.0, Duration.ofHours(4)),
            window("Weekly", QuotaWindowType.WEEKLY, 34.0, Duration.ofHours(16).plusMinutes(10)),
        )
        assertEquals("All clear. Lowest is Claude weekly, 66% left, resets in 16h 10m.", verdict(windows, limits, now))
    }

    @Test
    fun aWindowPastItsLineLeadsEvenIfAnotherHasLessLeft() {
        val windows = listOf(
            window("Current session", QuotaWindowType.ROLLING_HOURS, 92.0, Duration.ofMinutes(72)),
            window("Weekly", QuotaWindowType.WEEKLY, 70.0, Duration.ofDays(3)),
        )
        assertEquals("Claude current session is past your line: 8% left, resets in 1h 12m.", verdict(windows, limits, now))
    }

    @Test
    fun withinTenPointsOfTheLineIsCloseNotClear() {
        val windows = listOf(window("Weekly", QuotaWindowType.WEEKLY, 72.0, null))
        assertEquals("Claude weekly is close to your line: 28% left.", verdict(windows, limits, now))
    }

    @Test
    fun noReadingsAsksForAPull() {
        assertEquals("No readings yet. Pull down to turn the glasses.", verdict(emptyList(), limits, now))
    }
}

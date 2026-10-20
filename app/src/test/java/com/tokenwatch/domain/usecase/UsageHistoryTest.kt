package com.tokenwatch.domain.usecase

import com.tokenwatch.domain.model.AiProvider
import com.tokenwatch.domain.model.QuotaWindow
import com.tokenwatch.domain.model.QuotaWindowType
import com.tokenwatch.domain.model.UsageSource
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class UsageHistoryTest {
    private val t0 = Instant.parse("2026-10-19T08:00:00Z")
    private fun at(minutes: Long) = t0.plus(Duration.ofMinutes(minutes))

    private fun reading(minutes: Long, used: Double?, resetsAt: Instant? = null, id: String = "session") = QuotaWindow(
        provider = AiProvider.CLAUDE,
        windowId = id,
        label = id,
        windowType = QuotaWindowType.ROLLING_HOURS,
        usedPercent = used,
        resetsAt = resetsAt,
        observedAt = at(minutes),
        source = UsageSource.WEB_PAGE,
    )

    private fun used(series: UsageHistory) = series.segments.map { segment -> segment.map { it.usedPercent } }

    @Test
    fun closeReadingsFormOneLineInTimeOrder() {
        val series = usageHistory(listOf(reading(10, 20.0), reading(0, 10.0), reading(20, 35.0)))
        assertEquals(listOf(listOf(10.0, 20.0, 35.0)), used(series))
        assertEquals(3, series.readings)
        assertEquals(35.0, series.peakUsed!!, 0.0)
    }

    @Test
    fun aLongGapIsLeftAsAGapNotDrawnAcross() {
        val series = usageHistory(listOf(reading(0, 10.0), reading(30, 20.0), reading(300, 40.0)))
        assertEquals(listOf(listOf(10.0, 20.0), listOf(40.0)), used(series))
    }

    @Test
    fun oneMissedHourlyReadDoesNotBreakTheLine() {
        // Android runs background reads about hourly; one skipped run leaves a ~2 h gap.
        val series = usageHistory(listOf(reading(0, 10.0), reading(65, 20.0), reading(195, 30.0)))
        assertEquals(listOf(listOf(10.0, 20.0, 30.0)), used(series))
    }

    @Test
    fun aResetStartsANewSegmentAndIsMarkedAtItsTime() {
        val resetAt = at(60)
        val series = usageHistory(listOf(reading(0, 50.0, resetAt), reading(30, 70.0, resetAt), reading(70, 0.0, at(360))))
        assertEquals(listOf(listOf(50.0, 70.0), listOf(0.0)), used(series))
        assertEquals(listOf(resetAt), series.resets)
    }

    @Test
    fun theDrawnLineDropsToZeroAtAResetInsteadOfBreaking() {
        val resetAt = at(60)
        val series = usageHistory(listOf(reading(0, 50.0, resetAt), reading(30, 70.0, resetAt), reading(70, 5.0, at(360))))
        val drawn = series.drawnRuns().map { run -> run.map { it.at to it.usedPercent } }
        assertEquals(listOf(listOf(at(0) to 50.0, at(30) to 70.0, resetAt to 70.0, resetAt to 0.0, at(70) to 5.0)), drawn)
    }

    @Test
    fun theDrawnLineStillBreaksAtALongGapEvenAcrossAReset() {
        val series = usageHistory(listOf(reading(0, 60.0), reading(600, 5.0)))
        assertEquals(listOf(listOf(60.0), listOf(5.0)), series.drawnRuns().map { run -> run.map { it.usedPercent } })
    }

    @Test
    fun aDropWithoutAKnownResetTimeIsMarkedBetweenTheReadings() {
        val series = usageHistory(listOf(reading(0, 60.0), reading(40, 5.0)))
        assertEquals(listOf(listOf(60.0), listOf(5.0)), used(series))
        assertEquals(listOf(at(20)), series.resets)
    }

    @Test
    fun readingsWithoutAPercentAreSkippedNotDrawnAsZero() {
        val series = usageHistory(listOf(reading(0, 10.0), reading(10, null), reading(20, 15.0)))
        assertEquals(listOf(listOf(10.0, 15.0)), used(series))
        assertEquals(2, series.readings)
    }

    @Test
    fun anUnusedWindowSitsAtZero() {
        val series = usageHistory(listOf(reading(0, 0.0), reading(10, 0.0)))
        assertEquals(listOf(listOf(0.0, 0.0)), used(series))
        assertEquals(0.0, series.peakUsed!!, 0.0)
    }

    @Test
    fun noReadingsIsAnEmptyHistory() {
        val series = usageHistory(emptyList())
        assertEquals(emptyList<List<Double>>(), used(series))
        assertEquals(null, series.peakUsed)
        assertEquals(0, series.readings)
    }

    private fun windowOf(provider: AiProvider, id: String, type: QuotaWindowType, minutes: Long, used: Double) = QuotaWindow(
        provider = provider,
        windowId = id,
        label = id,
        windowType = type,
        usedPercent = used,
        observedAt = at(minutes),
        source = UsageSource.WEB_PAGE,
    )

    @Test
    fun theCombinedGraphHasOneLinePerProviderForTheChosenWindowKind() {
        val readings = listOf(
            windowOf(AiProvider.CODEX, "5_hour", QuotaWindowType.ROLLING_HOURS, 0, 10.0),
            windowOf(AiProvider.CLAUDE, "current_session", QuotaWindowType.ROLLING_HOURS, 0, 40.0),
            windowOf(AiProvider.CLAUDE, "weekly", QuotaWindowType.WEEKLY, 0, 5.0),
            windowOf(AiProvider.CLAUDE, "current_session", QuotaWindowType.ROLLING_HOURS, 10, 45.0),
        )
        val session = combinedHistory(readings, WindowGroup.SESSION)
        assertEquals(listOf(AiProvider.CLAUDE, AiProvider.CODEX), session.map { it.provider })
        assertEquals(listOf(listOf(40.0, 45.0)), session.first().history.segments.map { seg -> seg.map { it.usedPercent } })
        assertEquals(listOf(AiProvider.CLAUDE), combinedHistory(readings, WindowGroup.WEEKLY).map { it.provider })
    }

    @Test
    fun scrubbingReadsEachProvidersNearestReadingWithinReach() {
        val lines = combinedHistory(
            listOf(
                windowOf(AiProvider.CLAUDE, "s", QuotaWindowType.ROLLING_HOURS, 0, 10.0),
                windowOf(AiProvider.CLAUDE, "s", QuotaWindowType.ROLLING_HOURS, 60, 30.0),
                windowOf(AiProvider.CODEX, "s", QuotaWindowType.ROLLING_HOURS, 200, 50.0),
            ),
            WindowGroup.SESSION,
        )
        val values = valuesAt(lines, at(50), reach = Duration.ofMinutes(30))
        // Claude's 60-minute reading is nearest; Codex has nothing within reach, so it says nothing.
        assertEquals(listOf(AiProvider.CLAUDE to 30.0), values.map { (line, point) -> line.provider to point.usedPercent })
    }

    @Test
    fun theTimeAxisFitsTheReadingsThatExist() {
        val now = at(600)
        // Readings only from the last 40 minutes of a 24 h range: the axis starts at the first one.
        assertEquals(at(560), chartStart(now.minus(Duration.ofHours(24)), at(560), now))
        // Readings older than the range: the range wins.
        assertEquals(now.minus(Duration.ofHours(1)), chartStart(now.minus(Duration.ofHours(1)), at(0), now))
        // A single fresh reading still gets a readable 30-minute axis.
        assertEquals(now.minus(Duration.ofMinutes(30)), chartStart(now.minus(Duration.ofHours(24)), at(599), now))
        // No readings: the whole range.
        assertEquals(now.minus(Duration.ofHours(24)), chartStart(now.minus(Duration.ofHours(24)), null, now))
    }
}

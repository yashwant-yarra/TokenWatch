package com.tokenwatch.domain.usecase

import com.tokenwatch.domain.model.AiProvider
import com.tokenwatch.domain.model.QuotaWindow
import com.tokenwatch.domain.model.QuotaWindowType
import java.time.Duration
import java.time.Instant
import kotlin.math.abs

/** How far back a history chart looks. */
enum class HistoryRange(val label: String, val span: Duration) {
    DAY("24 h", Duration.ofHours(24)),
    WEEK("7 days", Duration.ofDays(7)),
    MONTH("30 days", Duration.ofDays(30)),
}

/** One reading on the history chart, as percent used: an unused window sits at 0. */
data class HistoryPoint(val at: Instant, val usedPercent: Double)

/**
 * A window's history: runs of readings close enough to join with a line, the resets between them,
 * how many readings there were, and the most that was ever used.
 */
data class UsageHistory(
    val segments: List<List<HistoryPoint>>,
    val resets: List<Instant>,
    val readings: Int,
    val peakUsed: Double?,
)

/**
 * Readings further apart than this aren't joined: what happened between them wasn't seen. Android
 * often runs background reads only about hourly, so this allows a couple of missed runs, while
 * staying under a 5-hour session so a whole session can't hide inside a joined gap.
 */
val HISTORY_MAX_GAP: Duration = Duration.ofHours(4)

/** Scrubbing reads a line only within this reach of the finger, so a far-off reading isn't passed off as "then". */
val SCRUB_REACH: Duration = Duration.ofMinutes(90)

/**
 * Builds one window's history from its stored readings. Readings without a percent are skipped
 * (never drawn as zero). A new segment starts after a gap or a reset; a reset is placed at the
 * reset time the page gave, or halfway between two readings when usage dropped without one.
 */
fun usageHistory(readings: List<QuotaWindow>, maxGap: Duration = HISTORY_MAX_GAP): UsageHistory {
    val usable = readings.mapNotNull { window -> window.usedPercent?.let { window to it } }.sortedBy { it.first.observedAt }
    val segments = mutableListOf<MutableList<HistoryPoint>>()
    val resets = mutableListOf<Instant>()
    var previous: Pair<QuotaWindow, Double>? = null
    for ((window, used) in usable) {
        val point = HistoryPoint(window.observedAt, used.coerceIn(0.0, 100.0))
        val before = previous
        val resetAt = before?.let { (prevWindow, prevUsed) -> resetBetween(prevWindow, prevUsed, window, used) }
        val gap = before?.let { Duration.between(it.first.observedAt, window.observedAt) > maxGap } ?: true
        if (resetAt != null || gap) segments.add(mutableListOf())
        resetAt?.let(resets::add)
        segments.last().add(point)
        previous = window to used
    }
    return UsageHistory(
        segments = segments,
        resets = resets,
        readings = usable.size,
        peakUsed = segments.flatten().maxOfOrNull { it.usedPercent },
    )
}

/**
 * The lines to draw: segments split only by a reset are joined, holding the last reading until the
 * reset and then dropping to 0, so the chart reads as one sawtooth. Long gaps stay gaps. For drawing
 * only; the added corner points weren't read, so scrubbing uses [UsageHistory.segments].
 */
fun UsageHistory.drawnRuns(maxGap: Duration = HISTORY_MAX_GAP): List<List<HistoryPoint>> {
    val runs = mutableListOf<MutableList<HistoryPoint>>()
    for (segment in segments) {
        val run = runs.lastOrNull()
        val last = run?.last()
        val first = segment.first()
        if (run == null || last == null || Duration.between(last.at, first.at) > maxGap) {
            runs.add(segment.toMutableList())
            continue
        }
        // Within the gap limit, the split was a reset. A stale reset time is clamped between the readings.
        val resetAt = (resets.lastOrNull { !it.isAfter(first.at) } ?: last.at).coerceIn(last.at, first.at)
        run += HistoryPoint(resetAt, last.usedPercent)
        run += HistoryPoint(resetAt, 0.0)
        run += segment
    }
    return runs
}

private fun resetBetween(before: QuotaWindow, usedBefore: Double, after: QuotaWindow, usedAfter: Double): Instant? {
    val known = before.resetsAt
    if (known != null && !after.observedAt.isBefore(known)) return known
    // Small dips are rounding on the page, not a reset.
    if (usedAfter < usedBefore - 0.5) {
        return before.observedAt.plus(Duration.between(before.observedAt, after.observedAt).dividedBy(2))
    }
    return null
}

/** Which windows the combined graph compares: session windows (rolling or daily) or weekly ones. */
enum class WindowGroup(val label: String) {
    SESSION("Session"),
    WEEKLY("Weekly"),
    ;

    fun includes(type: QuotaWindowType): Boolean = when (this) {
        SESSION -> type == QuotaWindowType.ROLLING_HOURS || type == QuotaWindowType.DAILY
        WEEKLY -> type == QuotaWindowType.WEEKLY
    }
}

/** One line on the combined graph: one provider's window. */
data class ProviderHistory(val provider: AiProvider, val label: String, val history: UsageHistory)

/** The combined graph's lines for [group]: one per provider window, in provider order. */
fun combinedHistory(readings: List<QuotaWindow>, group: WindowGroup): List<ProviderHistory> =
    readings
        .filter { group.includes(it.windowType) }
        .groupBy { it.provider to it.windowId }
        .map { (key, windows) -> ProviderHistory(key.first, windows.last().label, usageHistory(windows)) }
        .sortedBy { it.provider.ordinal }

/**
 * Each line's reading nearest to [at], for scrubbing the combined graph. A line with nothing within
 * [reach] says nothing rather than borrowing a far-off reading.
 */
fun valuesAt(lines: List<ProviderHistory>, at: Instant, reach: Duration): List<Pair<ProviderHistory, HistoryPoint>> =
    nearestReadings(lines.map { it.history }, at, reach).map { (index, point) -> lines[index] to point }

/** Same as [valuesAt] for plain histories: each one's index and its reading nearest to [at], within [reach]. */
fun nearestReadings(histories: List<UsageHistory>, at: Instant, reach: Duration): List<Pair<Int, HistoryPoint>> =
    histories.mapIndexedNotNull { index, history ->
        history.segments.flatten()
            .minByOrNull { abs(Duration.between(at, it.at).toMillis()) }
            ?.takeIf { abs(Duration.between(at, it.at).toMillis()) <= reach.toMillis() }
            ?.let { index to it }
    }

/** The shortest time axis a chart gets, so one fresh reading isn't squeezed into a sliver. */
val MIN_CHART_SPAN: Duration = Duration.ofMinutes(30)

/**
 * Where a chart's time axis starts: at the first reading that exists, but never before [rangeStart]
 * (the chosen range) and never less than [MIN_CHART_SPAN] before [now]. Without readings, the range.
 */
fun chartStart(rangeStart: Instant, firstReading: Instant?, now: Instant): Instant {
    if (firstReading == null) return rangeStart
    val fitted = maxOf(rangeStart, firstReading)
    return minOf(fitted, now.minus(MIN_CHART_SPAN))
}

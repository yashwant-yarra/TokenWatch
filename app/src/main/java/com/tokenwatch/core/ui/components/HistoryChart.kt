package com.tokenwatch.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tokenwatch.core.ui.theme.Engraved
import com.tokenwatch.core.util.Format
import com.tokenwatch.domain.usecase.HistoryPoint
import com.tokenwatch.domain.usecase.SCRUB_REACH
import com.tokenwatch.domain.usecase.UsageHistory
import com.tokenwatch.domain.usecase.drawnRuns
import com.tokenwatch.domain.usecase.nearestReadings
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One line on a history chart: a name for the scrub readout, its sand color, and its history. */
data class ChartLine(val name: String, val color: Color, val history: UsageHistory)

/**
 * History as percent left over time, like the glasses: lines drain toward 0 and jump back to 100 at a
 * reset (marked by a tick on the baseline); long gaps stay gaps. The alert line stays rim ink until a
 * line is near it. One line per window; several lines share the chart in the combined view, where
 * only a lone line gets the sand fill. It draws in on open, and dragging reads every line off at that
 * moment with a needle and a haptic tick.
 */
@Composable
fun HistoryChart(
    lines: List<ChartLine>,
    from: Instant,
    to: Instant,
    /** The user's alert limit, in percent used. */
    alertUsed: Double?,
    /** Plain words for the left edge of the time axis, e.g. "24 h ago". */
    fromLabel: String,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 132.dp,
    /** Changes when the chart should draw in again, e.g. the chosen range. */
    revealKey: Any = Unit,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val rule = MaterialTheme.colorScheme.outlineVariant
    val rim = MaterialTheme.colorScheme.outline
    val alert = MaterialTheme.colorScheme.error
    val ground = MaterialTheme.colorScheme.background
    val measurer = rememberTextMeasurer()
    val haptics = rememberHaptics()
    val zone = remember { ZoneId.systemDefault() }
    val span = Duration.between(from, to)
    val reach = maxOf(SCRUB_REACH, span.dividedBy(30))
    val readFormat = remember { DateTimeFormatter.ofPattern("EEE HH:mm", Locale.getDefault()) }

    val reveal = remember { Animatable(0f) }
    // Draws in when the chart opens or [revealKey] changes; not on every clock tick.
    LaunchedEffect(revealKey) {
        if (motionEnabled()) {
            reveal.snapTo(0f)
            reveal.animateTo(1f, spring(dampingRatio = 1f, stiffness = Spring.StiffnessVeryLow))
        } else {
            reveal.snapTo(1f)
        }
    }
    // The moment under the finger, and each line's reading there (by line index).
    var scrub by remember { mutableStateOf<Pair<Instant, List<Pair<Int, HistoryPoint>>>?>(null) }

    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .semantics { contentDescription = description }
                .pointerInput(lines, from, to) {
                    val gutter = GUTTER.toPx()
                    val histories = lines.map { it.history }
                    fun read(x: Float): Pair<Instant, List<Pair<Int, HistoryPoint>>>? {
                        val plotWidth = size.width - gutter - END_INSET.toPx()
                        if (plotWidth <= 0f) return null
                        val fraction = ((x - gutter) / plotWidth).coerceIn(0f, 1f)
                        val t = Instant.ofEpochMilli(from.toEpochMilli() + (fraction * (to.toEpochMilli() - from.toEpochMilli())).toLong())
                        val values = nearestReadings(histories, t, reach)
                        return if (values.isEmpty()) null else values.maxOf { it.second.at } to values
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        scrub = read(down.position.x).also { if (it != null) haptics.tick() }
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            val next = read(change.position.x)
                            if (next != null && next.second != scrub?.second) haptics.tick()
                            scrub = next
                            change.consume()
                        } while (event.changes.any { it.pressed })
                        scrub = null
                    }
                },
        ) {
            val gutter = GUTTER.toPx()
            val top = 26.dp.toPx()
            val bottom = size.height - 4.dp.toPx()
            // Inset on the right so the newest reading is never cut off at the edge.
            val plot = Rect(gutter, top, size.width - END_INSET.toPx(), bottom)
            val hair = 1.dp.toPx()
            fun x(at: Instant) = plot.left + plot.width * ((at.toEpochMilli() - from.toEpochMilli()).toFloat() / (to.toEpochMilli() - from.toEpochMilli()).coerceAtLeast(1))
            // Plotted as percent left (100 at the top), the same way the glasses read.
            fun y(used: Double) = plot.top + plot.height * (used / 100.0).toFloat()

            // Engraved scale: 100 / 50 / 0 left, with its unit.
            val scale = TextStyle(fontFamily = Engraved, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = muted)
            listOf(0.0, 50.0, 100.0).forEach { used ->
                drawLine(rule, Offset(plot.left, y(used)), Offset(plot.right, y(used)), strokeWidth = hair)
                val label = measurer.measure("${(100 - used).toInt()}", scale)
                drawText(label, topLeft = Offset(gutter - label.size.width - 6.dp.toPx(), y(used) - label.size.height / 2f))
            }
            // The unit heads the scale column, clear of the lines (which often run along 100).
            if (scrub == null) {
                val unit = measurer.measure("% left", scale)
                drawText(unit, topLeft = Offset(0f, 0f))
            }
            alertUsed?.let { level ->
                // Rim ink while every line is clear of it; alert only once one comes within reach.
                val latest = lines.mapNotNull { it.history.segments.lastOrNull()?.lastOrNull()?.usedPercent }
                val warn = latest.any { it >= level - ALERT_NEAR_POINTS }
                drawLine(if (warn) alert else rim, Offset(plot.left, y(level)), Offset(plot.right, y(level)), strokeWidth = hair, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
            }
            lines.forEach { line ->
                // Resets are short ticks on the baseline, in the line's own color when several share the chart.
                val mark = if (lines.size == 1) rim else line.color.copy(alpha = 0.8f)
                line.history.resets.filter { it in from..to }.forEach { at ->
                    drawLine(mark, Offset(x(at), plot.bottom), Offset(x(at), plot.bottom - 6.dp.toPx()), strokeWidth = 1.5.dp.toPx())
                }
            }

            // The runs of readings, revealed left to right.
            clipRect(left = 0f, top = 0f, right = plot.left + (plot.width + END_INSET.toPx()) * reveal.value, bottom = size.height) {
                lines.forEachIndexed { index, line ->
                    // ponytail: lines that sit on the same value (often 100% left) would hide each other, so
                    // several lines are spread 2dp apart; scrubbing still reads the exact values.
                    val nudge = if (lines.size > 1) (index - (lines.size - 1) / 2f) * 2.dp.toPx() else 0f
                    translate(top = nudge) {
                    line.history.drawnRuns().forEach { segment ->
                        val last = segment.last()
                        if (segment.size > 1) {
                            val path = Path().apply {
                                segment.forEachIndexed { i, p -> if (i == 0) moveTo(x(p.at), y(p.usedPercent)) else lineTo(x(p.at), y(p.usedPercent)) }
                            }
                            // Only a lone line gets the sand fill; overlapping fills would muddy each other.
                            if (lines.size == 1) {
                                val area = Path().apply {
                                    addPath(path)
                                    lineTo(x(last.at), plot.bottom)
                                    lineTo(x(segment.first().at), plot.bottom)
                                    close()
                                }
                                drawPath(area, Brush.verticalGradient(listOf(line.color.copy(alpha = 0.38f), line.color.copy(alpha = 0.04f)), startY = plot.top, endY = plot.bottom))
                            }
                            drawPath(path, line.color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                        // The latest reading of each run gets a dot, so even a short run is easy to see.
                        drawCircle(line.color, radius = 3.dp.toPx(), center = Offset(x(last.at), y(last.usedPercent)))
                    }
                    }
                }
            }

            // The needle and its readout, while scrubbing.
            scrub?.let { (at, values) ->
                val px = x(at)
                drawLine(ink, Offset(px, plot.top), Offset(px, plot.bottom), strokeWidth = 1.5.dp.toPx())
                values.forEach { (index, p) ->
                    val center = Offset(x(p.at), y(p.usedPercent))
                    drawCircle(ground, radius = 5.dp.toPx(), center = center)
                    drawCircle(lines[index].color, radius = 5.dp.toPx(), center = center, style = Stroke(2.dp.toPx()))
                }
                val reading = if (lines.size == 1) {
                    "${Format.percent(100.0 - values.first().second.usedPercent)} left"
                } else {
                    values.joinToString("  ") { (index, p) -> "${lines[index].name} ${Format.percent(100.0 - p.usedPercent)}" }
                }
                val label = measurer.measure(
                    "$reading · ${readFormat.format(at.atZone(zone))}",
                    TextStyle(fontFamily = Engraved, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = ground),
                )
                val pad = 6.dp.toPx()
                val boxW = label.size.width + pad * 2
                val boxX = (px - boxW / 2f).coerceIn(0f, (size.width - boxW).coerceAtLeast(0f))
                drawRoundRect(ink, topLeft = Offset(boxX, 0f), size = Size(boxW, label.size.height + 2f), cornerRadius = CornerRadius(3.dp.toPx()))
                drawText(label, topLeft = Offset(boxX + pad, 1f))
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = GUTTER, top = 2.dp)) {
            Text(fromLabel, style = MaterialTheme.typography.labelMedium, color = muted)
            Spacer(Modifier.weight(1f))
            Text("now", style = MaterialTheme.typography.labelMedium, color = muted)
        }
    }
}

private val GUTTER = 34.dp
private val END_INSET = 6.dp

/** Percentage points from the alert line at which the chart's line turns from rim ink to alert. */
private const val ALERT_NEAR_POINTS = 10

package com.tokenwatch.core.util

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

object Format {

    fun tokens(value: Long): String {
        val magnitude = abs(value)
        val (scaled, suffix) = when {
            magnitude >= 1_000_000_000L -> value / 1e9 to "B"
            magnitude >= 1_000_000L -> value / 1e6 to "M"
            magnitude >= 1_000L -> value / 1e3 to "K"
            else -> return value.toString()
        }
        val pattern = if (abs(scaled) >= 100) "%.0f" else "%.1f"
        return String.format(Locale.US, pattern, scaled).removeSuffix(".0") + suffix
    }

    fun percent(value: Double): String = "${value.roundToInt()}%"

    fun duration(d: Duration): String {
        val totalMinutes = d.toMinutes().coerceAtLeast(0)
        val days = totalMinutes / (60 * 24)
        val hours = (totalMinutes / 60) % 24
        val minutes = totalMinutes % 60
        return when {
            days > 0 -> "${days}d ${hours}h"
            hours > 0 -> "${hours}h ${minutes}m"
            else -> "${minutes}m"
        }
    }

    fun resetLabel(resetsAt: Instant, now: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
        val remaining = Duration.between(now, resetsAt)
        return when {
            remaining.isNegative || remaining.isZero -> "Window has reset since this was fetched"
            remaining < Duration.ofDays(2) -> "Resets in ${duration(remaining)}"
            else -> "Resets " + resetsAt.atZone(zone).dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        }
    }

    fun ago(then: Instant, now: Instant): String {
        val d = Duration.between(then, now)
        val minutes = d.toMinutes()
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> plural(minutes, "minute") + " ago"
            d.toHours() < 24 -> plural(d.toHours(), "hour") + " ago"
            else -> plural(d.toDays(), "day") + " ago"
        }
    }

    private fun plural(n: Long, unit: String) = if (n == 1L) "1 $unit" else "$n ${unit}s"
}

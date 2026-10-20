package com.tokenwatch.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tokenwatch.core.ui.theme.Numeral
import com.tokenwatch.core.util.Format
import com.tokenwatch.domain.model.AppSettings
import com.tokenwatch.domain.model.ProviderSnapshot
import com.tokenwatch.domain.model.QuotaWindow
import com.tokenwatch.domain.model.QuotaWindowType
import java.time.Instant

/** The user's alert limits (percent used), drawn as a line on every glass. Null when alerts are off. */
data class AlertLimits(val session: Int, val weekly: Int) {
    fun forWindow(window: QuotaWindow): Int = if (window.windowType == QuotaWindowType.WEEKLY) weekly else session
}

fun AppSettings.alertLimits(): AlertLimits? =
    if (usageAlertsEnabled) AlertLimits(sessionAlertPercent, weeklyAlertPercent) else null

val QuotaWindow.leftPercent: Double? get() = usedPercent?.let { (100.0 - it).coerceIn(0.0, 100.0) }

/** The least left in any of this provider's windows; null when nothing was reported. */
val ProviderSnapshot.lowestLeft: Double? get() = quotaWindows.mapNotNull { it.leftPercent }.minOrNull()

/** Without alert limits, a window this full still reads as close to its limit. */
private const val DEFAULT_WARN_USED = 90

/** True when a window has crossed the user's alert line (or is nearly full when alerts are off). */
fun QuotaWindow.isPastLine(limits: AlertLimits?): Boolean {
    val used = usedPercent ?: return false
    return used >= (limits?.forWindow(this) ?: DEFAULT_WARN_USED)
}

/**
 * One window as a glass with its reading. [tall] is for the most-at-risk provider: a taller glass,
 * the same numeral size (rank is carried by the glass and the order, never by shrinking a number).
 */
@Composable
fun GlassReading(
    window: QuotaWindow,
    sand: Color,
    now: Instant,
    limits: AlertLimits?,
    stale: Boolean,
    tall: Boolean,
    modifier: Modifier = Modifier,
    spinning: Boolean = false,
) {
    val left = window.leftPercent
    val reset = window.resetsAt?.let { Format.resetLabel(it, now) }
    val past = window.isPastLine(limits)
    val description = buildString {
        append(window.label).append(": ")
        append(left?.let { "${Format.percent(it)} left" } ?: "percentage not reported")
        reset?.let { append(". ").append(it) }
        limits?.let { append(". Alert line at ${100 - it.forWindow(window)}% left") }
        if (past) append(". Past the alert line")
        if (stale) append(". Last reading, refresh failed")
    }
    val numeral: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.Bottom) {
            RollingText(
                left?.let(Format::percent) ?: "–",
                style = Numeral.copy(fontWeight = if (tall) FontWeight.SemiBold else FontWeight.Medium),
                color = if (past) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                if (left == null) "not reported" else "left",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 7.dp),
            )
        }
    }
    val caption: @Composable () -> Unit = {
        Text(window.label, style = MaterialTheme.typography.labelLarge)
        if (reset != null) {
            Text(reset, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    val glass: @Composable () -> Unit = {
        SandGlass(
            leftPercent = left,
            sand = sand,
            alertUsedPercent = limits?.forWindow(window),
            stale = stale,
            spinning = spinning,
            width = if (tall) 64.dp else 34.dp,
            height = if (tall) 148.dp else 72.dp,
        )
    }
    val semantics = Modifier.clearAndSetSemantics { contentDescription = description }
    if (tall) {
        // Centered in its half, so the glass sits over its reading instead of hugging the left edge.
        Column(modifier.then(semantics), verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            glass()
            Column(Modifier.padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                numeral()
                caption()
            }
        }
    } else {
        Row(modifier.then(semantics), verticalAlignment = Alignment.CenterVertically) {
            glass()
            // Optical centering: the glass keeps shadow room under its base and the numeral face has
            // empty ascent above the digits, so plain centering sat the text ~5dp low.
            Column(Modifier.padding(start = 12.dp).offset(y = (-5).dp)) {
                numeral()
                caption()
            }
        }
    }
}

/** A provider's windows side by side, in a fixed order so they don't swap places between reads. */
@Composable
fun GlassRack(
    windows: List<QuotaWindow>,
    sand: Color,
    now: Instant,
    limits: AlertLimits?,
    stale: Boolean,
    tall: Boolean,
    modifier: Modifier = Modifier,
    spinning: Boolean = false,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        windows.sortedBy { it.windowType }.forEach { window ->
            GlassReading(window, sand, now, limits, stale, tall, Modifier.weight(1f), spinning)
        }
    }
}

package com.tokenwatch.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.tokenwatch.core.util.Format
import com.tokenwatch.domain.model.QuotaWindow
import java.time.Instant

/** One quota window. Near-limit windows switch to the error color, and the percent is always written out. */
@Composable
fun QuotaRow(window: QuotaWindow, accent: Color, now: Instant, modifier: Modifier = Modifier) {
    val percent = window.usedPercent
    val reset = window.resetsAt?.let { Format.resetLabel(it, now) }
    val description = buildString {
        append(window.label)
        append(": ")
        append(percent?.let { "${Format.percent(it)} used" } ?: "percentage not reported")
        reset?.let { append(". $it") }
    }
    Column(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(window.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(percent?.let(Format::percent) ?: "Not reported", style = MaterialTheme.typography.titleSmall)
        }
        if (percent != null) {
            val barColor = if (percent >= 90) MaterialTheme.colorScheme.error else accent
            LinearProgressIndicator(
                progress = { (percent / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                color = barColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeCap = StrokeCap.Round,
                drawStopIndicator = {},
            )
        }
        if (reset != null) {
            Text(
                reset,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/**
 * A provider's quota windows. Windows sit further apart than the lines inside one, so each
 * label, bar and reset time reads as a group.
 */
@Composable
fun QuotaList(windows: List<QuotaWindow>, accent: Color, now: Instant, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        // Extraction order follows page text length, which changes as numbers change; sort so rows don't jump.
        windows.sortedBy { it.windowType }.forEach { QuotaRow(it, accent, now) }
    }
}

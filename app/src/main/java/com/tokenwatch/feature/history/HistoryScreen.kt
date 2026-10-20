package com.tokenwatch.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tokenwatch.core.ui.components.Arrive
import com.tokenwatch.core.ui.components.ChartLine
import com.tokenwatch.core.ui.components.HistoryChart
import com.tokenwatch.core.ui.components.InfoNote
import com.tokenwatch.core.ui.components.LoadingState
import com.tokenwatch.core.ui.components.LocalBottomBarPadding
import com.tokenwatch.core.ui.components.ProviderMark
import com.tokenwatch.core.ui.components.Rule
import com.tokenwatch.core.ui.components.ScaleSelector
import com.tokenwatch.core.ui.components.TopLevelBar
import com.tokenwatch.core.ui.theme.ProviderColors
import com.tokenwatch.core.util.Format
import com.tokenwatch.domain.usecase.HistoryRange
import com.tokenwatch.domain.usecase.ProviderHistory
import com.tokenwatch.domain.usecase.WindowGroup
import com.tokenwatch.domain.usecase.chartStart
import com.tokenwatch.domain.usecase.combinedHistory

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopLevelBar("History") },
    ) { padding ->
        val current = state
        if (current == null) {
            LoadingState(Modifier.padding(padding))
        } else {
            HistoryBody(current, viewModel::setRange, viewModel::setGroup, Modifier.padding(padding))
        }
    }
}

/** Every provider on one graph, as percent left, for one kind of window at a time. */
@Composable
private fun HistoryBody(
    state: HistoryUiState,
    onRange: (HistoryRange) -> Unit,
    onGroup: (WindowGroup) -> Unit,
    modifier: Modifier,
) {
    val lines = combinedHistory(state.readings, state.group)
    // The axis covers the readings there are, not the whole range when most of it is empty.
    val from = chartStart(state.now.minus(state.range.span), lines.flatMap { it.history.segments.flatten() }.minOfOrNull { it.at }, state.now)
    val chartLines = lines.map { ChartLine(it.provider.displayName, ProviderColors.of(it.provider), it.history) }
    val alertUsed = state.limits?.let { (if (state.group == WindowGroup.WEEKLY) it.weekly else it.session).toDouble() }
    val description = buildString {
        append("${state.group.label} history, last ${state.range.label}. ")
        append(lines.joinToString(". ") { "${it.provider.displayName}: ${summary(it)}" }.ifEmpty { "No readings yet" })
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp + LocalBottomBarPadding.current),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Arrive(0) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ScaleSelector(WindowGroup.entries, state.group, { it.label }, onGroup)
                    ScaleSelector(HistoryRange.entries, state.range, { it.label }, onRange)
                }
            }
        }
        item {
            Arrive(1) {
                Column {
                    Rule()
                    HistoryChart(
                        lines = chartLines,
                        from = from,
                        to = state.now,
                        alertUsed = alertUsed,
                        fromLabel = Format.ago(from, state.now),
                        revealKey = state.range to state.group,
                        description = description,
                        height = 240.dp,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
        }
        if (lines.isEmpty()) {
            item {
                InfoNote("History is kept from now on: every reading is saved on this device for 35 days and drawn here.")
            }
        }
        // The legend doubles as each line's summary.
        lines.forEachIndexed { index, line ->
            item(key = "${line.provider.name}:${line.label}") {
                Arrive(index + 2) { LegendRow(line) }
            }
        }
    }
}

@Composable
private fun LegendRow(line: ProviderHistory) {
    Column {
        Rule()
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(width = 18.dp, height = 3.dp).background(ProviderColors.of(line.provider), RoundedCornerShape(2.dp)))
            ProviderMark(line.provider, size = 20.dp)
            Column(Modifier.weight(1f)) {
                Text(line.provider.displayName, style = MaterialTheme.typography.titleMedium)
                Text(line.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(summary(line), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun summary(line: ProviderHistory): String = listOfNotNull(
    "${line.history.readings} ${if (line.history.readings == 1) "reading" else "readings"}",
    line.history.peakUsed?.let { "lowest ${Format.percent(100.0 - it)} left" },
).joinToString(" · ")

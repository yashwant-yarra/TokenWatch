package com.tokenwatch.feature.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tokenwatch.R
import com.tokenwatch.core.ui.components.AlertLimits
import com.tokenwatch.core.ui.components.Arrive
import com.tokenwatch.core.ui.components.ErrataSlip
import com.tokenwatch.core.ui.components.Glyph
import com.tokenwatch.core.ui.components.GlyphIcon
import com.tokenwatch.core.ui.components.Rule
import com.tokenwatch.core.ui.components.rememberHaptics
import com.tokenwatch.core.ui.components.GlassRack
import com.tokenwatch.core.ui.components.LoadingState
import com.tokenwatch.core.ui.components.LocalBottomBarPadding
import com.tokenwatch.core.ui.components.MessageState
import com.tokenwatch.core.ui.components.ProviderMark
import com.tokenwatch.core.ui.components.SandGlass
import com.tokenwatch.core.ui.components.TopLevelBar
import com.tokenwatch.core.ui.components.TurningGlassIndicator
import com.tokenwatch.core.ui.components.pullGap
import com.tokenwatch.core.ui.components.connectionLabel
import com.tokenwatch.core.ui.components.isPastLine
import com.tokenwatch.core.ui.components.leftPercent
import com.tokenwatch.core.ui.components.lowestLeft
import com.tokenwatch.core.ui.components.needsReconnect
import com.tokenwatch.core.ui.theme.ProviderColors
import com.tokenwatch.core.ui.theme.TokenWatchTheme
import com.tokenwatch.core.util.Format
import com.tokenwatch.domain.model.AiProvider
import com.tokenwatch.domain.model.ConnectionStatus
import com.tokenwatch.domain.model.ProviderSnapshot
import com.tokenwatch.domain.model.QuotaWindow
import java.time.Instant
import java.util.Locale

@Composable
fun DashboardScreen(
    onProviderClick: (AiProvider) -> Unit,
    onOpenConnections: () -> Unit,
    onSignIn: (AiProvider) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DashboardContent(state, viewModel::refreshAll, onSignIn, onProviderClick, onOpenConnections)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardContent(
    state: DashboardUiState,
    onRefreshAll: () -> Unit,
    onSignIn: (AiProvider) -> Unit,
    onProviderClick: (AiProvider) -> Unit,
    onOpenConnections: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopLevelBar(stringResource(R.string.app_name)) },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (state) {
            DashboardUiState.Loading -> LoadingState(modifier)
            DashboardUiState.Empty -> MessageState(
                glyph = null,
                art = { SandGlass(leftPercent = null, sand = MaterialTheme.colorScheme.onSurface, width = 48.dp, height = 88.dp) },
                title = "No accounts connected yet",
                message = "Connect Claude, Codex or Gemini to see how much of each limit is left and when it comes back.",
                actionLabel = "Connect an account",
                onAction = onOpenConnections,
                modifier = modifier,
            )
            is DashboardUiState.Error -> MessageState(
                glyph = Glyph.ALERT,
                title = "Usage couldn't be read",
                message = state.message,
                actionLabel = "Open accounts",
                onAction = onOpenConnections,
                modifier = modifier,
            )
            is DashboardUiState.Content -> {
                val pull = rememberPullToRefreshState()
                // A soft knock when a pull's reading lands.
                val haptics = rememberHaptics()
                var wasRefreshing by remember { mutableStateOf(false) }
                LaunchedEffect(state.isRefreshing) {
                    if (wasRefreshing && !state.isRefreshing) haptics.confirm()
                    wasRefreshing = state.isRefreshing
                }
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = onRefreshAll,
                    state = pull,
                    modifier = modifier,
                    indicator = { TurningGlassIndicator(pull, state.isRefreshing) },
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .pullGap(pull, state.isRefreshing),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 24.dp + LocalBottomBarPadding.current),
                    ) {
                        item { Arrive(0) { Verdict(state, onRefreshAll) } }
                        itemsIndexed(state.providers, key = { _, it -> it.provider.name }) { index, snapshot ->
                            Arrive(index + 1) {
                                ProviderShelf(
                                    snapshot = snapshot,
                                    now = state.now,
                                    limits = state.limits,
                                    // The provider with the least left gets the tall glasses.
                                    lead = index == 0 && snapshot.lowestLeft != null,
                                    spinning = snapshot.provider in state.refreshing,
                                    onClick = { onProviderClick(snapshot.provider) },
                                    onSignIn = { onSignIn(snapshot.provider) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One sentence answering "am I about to hit a limit?" before any glass is read. */
@Composable
private fun Verdict(state: DashboardUiState.Content, onRefresh: () -> Unit) {
    val windows = state.providers
        .filter { it.isActive }
        .flatMap { snapshot -> snapshot.quotaWindows.map { snapshot.provider to it } }
    val sentence = verdict(windows, state.limits, state.now)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp)
            // The first thing a screen reader lands on; pulling is the only refresh gesture, so it's offered here too.
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                customActions = listOf(CustomAccessibilityAction("Refresh usage") { onRefresh(); true })
            },
    ) {
        Text(sentence, style = MaterialTheme.typography.bodyLarge)
        if (state.isStale && state.lastUpdated != null) {
            Text(
                "Oldest reading is from ${Format.ago(state.lastUpdated, state.now)}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** How close (in percentage points) a window may come to its alert line before it stops being "all clear". */
private const val NEAR_LINE_POINTS = 10

/**
 * The dashboard's one-line answer to "can I keep going, and until when?". A window past its line
 * leads, then one close to it; otherwise it's all clear and names the lowest window. The reset
 * time is added only when the page gave one that is still ahead.
 */
internal fun verdict(windows: List<Pair<AiProvider, QuotaWindow>>, limits: AlertLimits?, now: Instant): String {
    val read = windows.filter { it.second.leftPercent != null }.sortedBy { it.second.leftPercent }
    if (read.isEmpty()) return "No readings yet. Pull down to turn the glasses."
    fun near(window: QuotaWindow): Boolean {
        val line = limits?.forWindow(window) ?: return false
        return (window.usedPercent ?: return false) >= line - NEAR_LINE_POINTS
    }
    val (provider, window) = read.firstOrNull { it.second.isPastLine(limits) } ?: read.firstOrNull { near(it.second) } ?: read.first()
    val name = "${provider.displayName} ${window.label.lowercase(Locale.getDefault())}"
    val reading = "${Format.percent(window.leftPercent ?: 0.0)} left" +
        (window.resetsAt?.takeIf { it.isAfter(now) }?.let { ", " + Format.resetLabel(it, now).replaceFirstChar(Char::lowercaseChar) } ?: "")
    return when {
        window.isPastLine(limits) && limits == null -> "$name is nearly out: $reading."
        window.isPastLine(limits) -> "${name.replaceFirstChar(Char::uppercaseChar)} is past your line: $reading."
        near(window) -> "${name.replaceFirstChar(Char::uppercaseChar)} is close to your line: $reading."
        else -> "All clear. Lowest is $name, $reading."
    }
}

/** One provider on the bench: a ruled shelf with its name, its glasses, and any errata slip. */
@Composable
private fun ProviderShelf(
    snapshot: ProviderSnapshot,
    now: Instant,
    limits: AlertLimits?,
    lead: Boolean,
    spinning: Boolean,
    onClick: () -> Unit,
    onSignIn: () -> Unit,
) {
    val connection = snapshot.connection
    val stale = snapshot.lastFailure != null
    Column(Modifier.fillMaxWidth()) {
        Rule()
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = "Open ${connection.provider.displayName}", role = Role.Button, onClick = onClick)
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ProviderMark(connection.provider)
            Text(connection.provider.displayName, style = MaterialTheme.typography.titleLarge)
            Text(
                listOfNotNull(
                    connection.planName ?: connectionLabel(connection.status),
                    connection.lastSyncedAt?.let { "read ${Format.ago(it, now)}" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            GlyphIcon(Glyph.CHEVRON, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 20.dp)
        }
        when {
            connection.status == ConnectionStatus.DISCONNECTED -> Text(
                connection.integrationNote ?: "Not connected. Connect it from Accounts.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 20.dp),
            )
            snapshot.quotaWindows.isEmpty() -> Text(
                "Usage unavailable",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 20.dp),
            )
            else -> GlassRack(
                windows = snapshot.quotaWindows,
                sand = ProviderColors.of(connection.provider),
                now = now,
                limits = limits,
                stale = stale,
                tall = lead,
                spinning = spinning,
                modifier = Modifier.padding(top = 4.dp, bottom = if (stale) 12.dp else 24.dp),
            )
        }
        snapshot.lastFailure?.let { failure ->
            ErrataSlip(
                provider = connection.provider,
                failure = failure,
                now = now,
                actionLabel = if (failure.needsReconnect) "Sign in" else null,
                onAction = onSignIn,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

@Preview(name = "Dashboard, light")
@Preview(name = "Dashboard, dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DashboardEmptyPreview() {
    TokenWatchTheme(darkTheme = isSystemInDarkTheme()) {
        DashboardContent(DashboardUiState.Empty, {}, {}, {}, {})
    }
}

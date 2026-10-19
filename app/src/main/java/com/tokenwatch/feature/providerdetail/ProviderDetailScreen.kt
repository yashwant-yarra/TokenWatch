package com.tokenwatch.feature.providerdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tokenwatch.core.ui.components.BenchDialog
import com.tokenwatch.core.ui.components.BenchHeader
import com.tokenwatch.core.ui.components.ErrataSlip
import com.tokenwatch.core.ui.components.PlateButton
import com.tokenwatch.core.ui.components.PlateStyle
import com.tokenwatch.core.ui.components.Rule
import com.tokenwatch.core.ui.components.GlassRack
import com.tokenwatch.core.ui.components.InfoNote
import com.tokenwatch.core.ui.components.LabelValueRow
import com.tokenwatch.core.ui.components.LoadingState
import com.tokenwatch.core.ui.components.ProviderMark
import com.tokenwatch.core.ui.components.Section
import com.tokenwatch.core.ui.components.TurningGlassIndicator
import com.tokenwatch.core.ui.components.pullGap
import com.tokenwatch.core.ui.components.connectionLabel
import com.tokenwatch.core.ui.components.needsReconnect
import com.tokenwatch.core.ui.theme.ProviderColors
import com.tokenwatch.core.util.Format
import com.tokenwatch.domain.model.AiProvider
import com.tokenwatch.domain.model.ConnectionStatus
import com.tokenwatch.domain.model.IntegrationStatus
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ProviderDetailScreen(
    onBack: () -> Unit,
    onWebSession: (AiProvider) -> Unit,
    viewModel: ProviderDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProviderDetailContent(
        title = viewModel.provider.displayName,
        state = state,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onConnect = { onWebSession(viewModel.provider) },
        onDisconnect = viewModel::signOut,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderDetailContent(
    title: String,
    state: ProviderDetailUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    var confirmDisconnect by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { BenchHeader(title, onBack = onBack) },
    ) { padding ->
        when (state) {
            ProviderDetailUiState.Loading -> LoadingState(Modifier.padding(padding))
            // Pull down to turn the glasses, like the Usage tab.
            is ProviderDetailUiState.Content -> {
                val pull = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = onRefresh,
                    state = pull,
                    modifier = Modifier.padding(padding),
                    indicator = { TurningGlassIndicator(pull, state.isRefreshing) },
                ) {
                    DetailBody(
                        state = state,
                        modifier = Modifier
                            .fillMaxSize()
                            .pullGap(pull, state.isRefreshing),
                        onRefresh = onRefresh,
                        onConnect = onConnect,
                        onDisconnect = { confirmDisconnect = true },
                    )
                }
            }
        }
    }
    if (confirmDisconnect) {
        BenchDialog(
            title = "Sign out of $title?",
            text = "You're signed out of $title in this app and its cached usage is removed from this device.",
            confirmLabel = "Sign out",
            onConfirm = {
                confirmDisconnect = false
                onDisconnect()
            },
            onDismiss = { confirmDisconnect = false },
        )
    }
}

@Composable
private fun DetailBody(
    state: ProviderDetailUiState.Content,
    modifier: Modifier,
    onRefresh: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val snapshot = state.snapshot
    val connection = snapshot.connection
    val caps = connection.capabilities
    val isActive = connection.status != ConnectionStatus.DISCONNECTED
    val zone = remember { ZoneId.systemDefault() }
    val resetFormat = remember { DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.getDefault()) }
    val windows = snapshot.quotaWindows.sortedBy { it.windowType }

    LazyColumn(
        modifier = modifier.navigationBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
    ) {
        item {
            // The provider name is the screen title, so this line carries only its status.
            Row(
                Modifier
                    .padding(bottom = 20.dp)
                    // Pulling is the only refresh gesture, so screen readers get it as an action here.
                    .semantics(mergeDescendants = true) {
                        customActions = listOf(CustomAccessibilityAction("Refresh usage") { onRefresh(); true })
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ProviderMark(connection.provider, size = 26.dp)
                Text(
                    listOfNotNull(
                        connectionLabel(connection.status),
                        connection.planName.takeIf { caps.subscriptionInfo },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        snapshot.lastFailure?.let { failure ->
            item {
                ErrataSlip(
                    provider = connection.provider,
                    failure = failure,
                    now = state.now,
                    actionLabel = if (failure.needsReconnect) "Reconnect" else null,
                    onAction = onConnect,
                    modifier = Modifier.padding(bottom = 20.dp),
                )
            }
        }

        if (!isActive) {
            item {
                Section(title = "Not connected") {
                    val supported = connection.integrationStatus != IntegrationStatus.NOT_YET_SUPPORTED
                    Text(
                        if (supported) {
                            "Connect ${connection.provider.displayName} to see its usage here."
                        } else {
                            "This provider can't be connected until an official API is available."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    PlateButton("Connect ${connection.provider.displayName}", onConnect, enabled = supported)
                }
            }
            return@LazyColumn
        }

        item {
            Section(title = null) {
                if (caps.quotaWindows && windows.isNotEmpty()) {
                    GlassRack(
                        windows = windows,
                        sand = ProviderColors.of(connection.provider),
                        now = state.now,
                        limits = state.limits,
                        stale = snapshot.lastFailure != null,
                        tall = true,
                        spinning = state.isRefreshing,
                    )
                } else {
                    InfoNote("${connection.provider.displayName} quota information isn't available through the current integration.")
                }
            }
        }

        item {
            Section(title = "Readings") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    windows.forEach { window ->
                        window.resetsAt?.let { LabelValueRow("${window.label} resets", resetFormat.format(it.atZone(zone))) }
                    }
                    state.limits?.let { limits ->
                        windows.forEach { window -> LabelValueRow("${window.label} alert line", "${100 - limits.forWindow(window)}% left") }
                    }
                    connection.accountLabel?.let { LabelValueRow("Account", it) }
                    snapshot.fetchedAt?.let { LabelValueRow("Last read", Format.ago(it, state.now)) }
                }
                if (state.limits == null) {
                    InfoNote("Turn on usage alerts in Settings to mark your alert line on each glass.")
                }
            }
        }

        item {
            Rule()
            PlateButton(
                "Sign out of ${connection.provider.displayName}",
                onDisconnect,
                style = PlateStyle.QUIET,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp).offset(x = (-12).dp),
            )
        }
    }
}

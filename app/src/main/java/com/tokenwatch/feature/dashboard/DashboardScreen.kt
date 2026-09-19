package com.tokenwatch.feature.dashboard

import com.tokenwatch.core.ui.components.LocalBottomBarPadding
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tokenwatch.R
import com.tokenwatch.core.ui.components.FailureBanner
import com.tokenwatch.core.ui.components.LoadingState
import com.tokenwatch.core.ui.components.MessageState
import com.tokenwatch.core.ui.components.ProviderAvatar
import com.tokenwatch.core.ui.components.QuotaList
import com.tokenwatch.core.ui.components.SectionCard
import com.tokenwatch.core.ui.components.StaleBanner
import com.tokenwatch.core.ui.components.TopLevelBar
import com.tokenwatch.core.ui.components.connectionLabel
import com.tokenwatch.core.ui.components.needsReconnect
import com.tokenwatch.core.ui.theme.TokenWatchTheme
import com.tokenwatch.core.ui.theme.ProviderColors
import com.tokenwatch.core.util.Format
import com.tokenwatch.domain.model.AiProvider
import com.tokenwatch.domain.model.ConnectionStatus
import com.tokenwatch.domain.model.ProviderSnapshot
import java.time.Instant

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
                icon = Icons.Default.AccountCircle,
                title = "Connect your first AI account",
                message = "Link Claude, Codex or Gemini to see quotas and reset times in one place.",
                actionLabel = "Go to connections",
                onAction = onOpenConnections,
                modifier = modifier,
            )
            is DashboardUiState.Error -> MessageState(
                icon = Icons.Default.Warning,
                title = "Usage couldn't be loaded",
                message = state.message,
                actionLabel = "Open accounts",
                onAction = onOpenConnections,
                modifier = modifier,
            )
            is DashboardUiState.Content -> PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefreshAll,
                modifier = modifier,
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + LocalBottomBarPadding.current),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (state.isStale && state.lastUpdated != null) {
                        item { StaleBanner(state.lastUpdated, state.now) }
                    }
                    items(state.providers, key = { it.provider.name }) { snapshot ->
                        ProviderCard(
                            snapshot = snapshot,
                            now = state.now,
                            onClick = { onProviderClick(snapshot.provider) },
                            onSignIn = { onSignIn(snapshot.provider) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderCard(
    snapshot: ProviderSnapshot,
    now: Instant,
    onClick: () -> Unit,
    onSignIn: () -> Unit,
) {
    val connection = snapshot.connection
    val accent = ProviderColors.of(connection.provider)
    SectionCard(title = null, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ProviderAvatar(connection.provider)
            Column(Modifier.weight(1f)) {
                Text(connection.provider.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(
                        connection.planName ?: connectionLabel(connection.status),
                        connection.lastSyncedAt?.let { "Updated ${Format.ago(it, now)}" },
                    ).joinToString(" \u00B7 "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        snapshot.lastFailure?.let { failure ->
            FailureBanner(
                provider = connection.provider,
                failure = failure,
                now = now,
                actionLabel = if (failure.needsReconnect) "Sign in" else null,
                onAction = onSignIn,
            )
        }
        when {
            connection.status == ConnectionStatus.DISCONNECTED -> Text(
                connection.integrationNote ?: "Not connected",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            snapshot.quotaWindows.isEmpty() -> Text(
                "Usage information unavailable",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> QuotaList(snapshot.quotaWindows, accent, now, Modifier.padding(top = 6.dp))
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

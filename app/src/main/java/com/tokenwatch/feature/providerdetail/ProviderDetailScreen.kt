package com.tokenwatch.feature.providerdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tokenwatch.core.ui.components.FailureBanner
import com.tokenwatch.core.ui.components.InfoNote
import com.tokenwatch.core.ui.components.LabelValueRow
import com.tokenwatch.core.ui.components.LoadingState
import com.tokenwatch.core.ui.components.ProviderAvatar
import com.tokenwatch.core.ui.components.QuotaList
import com.tokenwatch.core.ui.components.SectionCard
import com.tokenwatch.core.ui.components.StaleBanner
import com.tokenwatch.core.ui.components.connectionLabel
import com.tokenwatch.core.ui.components.needsReconnect
import com.tokenwatch.core.ui.theme.ProviderColors
import com.tokenwatch.core.util.Format
import com.tokenwatch.domain.model.AiProvider
import com.tokenwatch.domain.model.ConnectionStatus
import com.tokenwatch.domain.model.IntegrationStatus

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
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        when (state) {
            ProviderDetailUiState.Loading -> LoadingState(Modifier.padding(padding))
            // Pull down to refresh, like the Usage tab.
            is ProviderDetailUiState.Content -> PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.padding(padding),
            ) {
                DetailBody(
                    state = state,
                    modifier = Modifier.fillMaxSize(),
                    onConnect = onConnect,
                    onDisconnect = { confirmDisconnect = true },
                )
            }
        }
    }
    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text("Sign out of $title?") },
            text = { Text("You're signed out of $title in this app and its cached usage is removed from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDisconnect = false
                    onDisconnect()
                }) { Text("Sign out") }
            },
            dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DetailBody(
    state: ProviderDetailUiState.Content,
    modifier: Modifier,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val snapshot = state.snapshot
    val connection = snapshot.connection
    val caps = connection.capabilities
    val accent = ProviderColors.of(connection.provider)
    val isActive = connection.status != ConnectionStatus.DISCONNECTED

    LazyColumn(
        modifier = modifier.navigationBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(title = null) {
                // The provider name is already the screen title, so the header shows only its status.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProviderAvatar(connection.provider, size = 48.dp)
                    Text(connectionLabel(connection.status), style = MaterialTheme.typography.titleMedium)
                }
                connection.accountLabel?.let { LabelValueRow("Account", it) }
                if (caps.subscriptionInfo) connection.planName?.let { LabelValueRow("Plan", it) }
                connection.lastSyncedAt?.let { LabelValueRow("Updated", Format.ago(it, state.now)) }
            }
        }

        snapshot.lastFailure?.let { failure ->
            item {
                FailureBanner(
                    provider = connection.provider,
                    failure = failure,
                    now = state.now,
                    actionLabel = if (failure.needsReconnect) "Reconnect" else null,
                    onAction = onConnect,
                )
            }
            if (snapshot.hasData) snapshot.fetchedAt?.let { fetched -> item { StaleBanner(fetched, state.now) } }
        }

        if (!isActive) {
            item {
                SectionCard(title = "Not connected") {
                    val supported = connection.integrationStatus != IntegrationStatus.NOT_YET_SUPPORTED
                    Text(
                        if (supported) {
                            "Connect ${connection.provider.displayName} to see its usage here."
                        } else {
                            "This provider can't be connected until an official API is available."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = onConnect, enabled = supported) { Text("Connect ${connection.provider.displayName}") }
                }
            }
            return@LazyColumn
        }

        item {
            SectionCard(title = "Current quotas") {
                if (caps.quotaWindows && snapshot.quotaWindows.isNotEmpty()) {
                    QuotaList(snapshot.quotaWindows, accent, state.now)
                } else {
                    InfoNote("${connection.provider.displayName} quota information isn't available through the current integration.")
                }
            }
        }

        item {
            OutlinedButton(
                onClick = onDisconnect,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
            ) {
                Text("Sign out of ${connection.provider.displayName}")
            }
        }
    }
}

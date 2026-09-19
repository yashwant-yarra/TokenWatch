package com.tokenwatch.feature.connections

import com.tokenwatch.core.ui.components.LocalBottomBarPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.tokenwatch.core.ui.components.TopLevelBar
import com.tokenwatch.core.ui.components.LoadingState
import com.tokenwatch.core.ui.components.ProviderAvatar
import com.tokenwatch.core.ui.components.SectionCard
import com.tokenwatch.core.ui.components.connectionLabel
import com.tokenwatch.core.util.Format
import com.tokenwatch.domain.model.AiProvider
import com.tokenwatch.domain.model.ConnectionStatus
import com.tokenwatch.domain.model.IntegrationStatus
import com.tokenwatch.domain.model.ProviderSnapshot
import java.time.Instant

@Composable
fun ConnectionsScreen(
    onManage: (AiProvider) -> Unit,
    onWebSession: (AiProvider) -> Unit,
    viewModel: ConnectionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ConnectionsContent(state, onManage, onWebSession, viewModel::signOut)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionsContent(
    state: ConnectionsUiState,
    onManage: (AiProvider) -> Unit,
    onConnect: (AiProvider) -> Unit,
    onDisconnect: (AiProvider) -> Unit,
) {
    var connecting by rememberSaveable { mutableStateOf<AiProvider?>(null) }
    var disconnecting by rememberSaveable { mutableStateOf<AiProvider?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopLevelBar("Accounts") },
    ) { padding ->
        when (state) {
            ConnectionsUiState.Loading -> LoadingState(Modifier.padding(padding))
            is ConnectionsUiState.Content -> LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + LocalBottomBarPadding.current),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(state.connections, key = { it.provider.name }) { snapshot ->
                    ConnectionCard(
                        snapshot = snapshot,
                        now = state.now,
                        onConnect = { connecting = snapshot.provider },
                        onDisconnect = { disconnecting = snapshot.provider },
                        onManage = { onManage(snapshot.provider) },
                    )
                }
                item {
                    InfoNote(
                        "Connecting opens the provider's own sign-in page inside this app. " +
                            "Passwords and web sessions stay in the private web view.",
                        Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }

    connecting?.let { provider ->
        AlertDialog(
            onDismissRequest = { connecting = null },
            title = { Text("Connect ${provider.displayName}") },
            text = {
                Text(
                    "Sign in to ${provider.displayName} inside this app. " +
                        "Your password and session stay in the private web view.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    connecting = null
                    onConnect(provider)
                }) { Text("Connect") }
            },
            dismissButton = { TextButton(onClick = { connecting = null }) { Text("Cancel") } },
        )
    }

    disconnecting?.let { provider ->
        AlertDialog(
            onDismissRequest = { disconnecting = null },
            title = { Text("Sign out of ${provider.displayName}?") },
            text = { Text("You're signed out of ${provider.displayName} in this app and its cached usage is removed from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    disconnecting = null
                    onDisconnect(provider)
                }) { Text("Sign out") }
            },
            dismissButton = { TextButton(onClick = { disconnecting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ConnectionCard(
    snapshot: ProviderSnapshot,
    now: Instant,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onManage: () -> Unit,
) {
    val connection = snapshot.connection
    SectionCard(title = null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ProviderAvatar(connection.provider)
            Column(Modifier.weight(1f)) {
                Text(connection.provider.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    connectionLabel(connection.status),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (connection.status == ConnectionStatus.CONNECTED) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }

        // Details sit close together as one group under the header.
        val details = listOfNotNull(
            connection.planName?.let { "Plan" to it },
            connection.accountLabel?.let { "Account" to it },
            connection.lastSyncedAt?.let { "Updated" to Format.ago(it, now) },
        )
        if (details.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                details.forEach { (label, value) -> LabelValueRow(label, value) }
            }
        }
        snapshot.lastFailure?.let { FailureBanner(connection.provider, it, now) }

        val signOutColors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
        Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            when (connection.status) {
                ConnectionStatus.CONNECTED -> {
                    OutlinedButton(onClick = onManage, modifier = Modifier.weight(1f)) { Text("Manage") }
                    TextButton(onClick = onDisconnect, modifier = Modifier.weight(1f), colors = signOutColors) { Text("Sign out") }
                }
                ConnectionStatus.EXPIRED, ConnectionStatus.REVOKED, ConnectionStatus.ERROR -> {
                    Button(onClick = onConnect, modifier = Modifier.weight(1f)) { Text("Reconnect") }
                    TextButton(onClick = onDisconnect, modifier = Modifier.weight(1f), colors = signOutColors) { Text("Sign out") }
                }
                ConnectionStatus.DISCONNECTED -> Button(
                    onClick = onConnect,
                    enabled = connection.integrationStatus != IntegrationStatus.NOT_YET_SUPPORTED,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Connect") }
            }
        }
    }
}

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.tokenwatch.core.ui.components.Glyph
import com.tokenwatch.core.ui.components.GlyphIcon
import com.tokenwatch.core.ui.components.Rule
import com.tokenwatch.core.ui.components.SandGlass
import com.tokenwatch.core.ui.components.leftPercent
import com.tokenwatch.core.ui.theme.ProviderColors
import com.tokenwatch.core.ui.components.Arrive
import com.tokenwatch.core.ui.components.BenchDialog
import com.tokenwatch.core.ui.components.ErrataSlip
import com.tokenwatch.core.ui.components.PlateButton
import com.tokenwatch.core.ui.components.PlateStyle
import com.tokenwatch.core.ui.components.InfoNote
import com.tokenwatch.core.ui.components.TopLevelBar
import com.tokenwatch.core.ui.components.LoadingState
import com.tokenwatch.core.ui.components.ProviderMark
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
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp + LocalBottomBarPadding.current),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                itemsIndexed(state.connections, key = { _, it -> it.provider.name }) { index, snapshot ->
                    Arrive(index) {
                        ConnectionCard(
                            snapshot = snapshot,
                            now = state.now,
                            onConnect = { connecting = snapshot.provider },
                            onDisconnect = { disconnecting = snapshot.provider },
                            onManage = { onManage(snapshot.provider) },
                        )
                    }
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
        BenchDialog(
            title = "Connect ${provider.displayName}",
            text = "Sign in to ${provider.displayName} inside this app. Your password and session stay in the private web view.",
            confirmLabel = "Connect",
            onConfirm = {
                connecting = null
                onConnect(provider)
            },
            onDismiss = { connecting = null },
        )
    }

    disconnecting?.let { provider ->
        BenchDialog(
            title = "Sign out of ${provider.displayName}?",
            text = "You're signed out of ${provider.displayName} in this app and its cached usage is removed from this device.",
            confirmLabel = "Sign out",
            onConfirm = {
                disconnecting = null
                onDisconnect(provider)
            },
            onDismiss = { disconnecting = null },
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
    val name = connection.provider.displayName
    val disconnected = connection.status == ConnectionStatus.DISCONNECTED
    val lapsed = connection.status == ConnectionStatus.EXPIRED ||
        connection.status == ConnectionStatus.REVOKED ||
        connection.status == ConnectionStatus.ERROR
    val supported = connection.integrationStatus != IntegrationStatus.NOT_YET_SUPPORTED
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth()) {
        Rule()
        // The whole row opens the provider, like the shelves on the Usage tab.
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(
                    enabled = !disconnected || supported,
                    role = Role.Button,
                    onClickLabel = if (disconnected) "Connect $name" else "Manage $name",
                ) { if (disconnected) onConnect() else onManage() }
                .padding(top = 16.dp, bottom = if (disconnected || lapsed) 16.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProviderMark(connection.provider, size = 26.dp)
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleLarge)
                // The signed-in address, when the provider's page shows one (Claude's doesn't).
                connection.accountLabel?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    listOfNotNull(
                        connectionLabel(connection.status),
                        connection.planName,
                        connection.lastSyncedAt?.let { "read ${Format.ago(it, now)}" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (lapsed) MaterialTheme.colorScheme.error else muted,
                )
                if (!disconnected && !lapsed) {
                    // Sign out stays with its account, under the status line.
                    PlateButton(
                        "Sign out",
                        onDisconnect,
                        // Quiet plates carry their own inset; pull it back so the label meets the text edge.
                        Modifier.offset(x = (-12).dp),
                        style = PlateStyle.QUIET,
                        color = muted,
                    )
                }
            }
            // The account's live glasses, dashed when its sign-in has lapsed.
            if (!disconnected && snapshot.quotaWindows.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    snapshot.quotaWindows.sortedBy { it.windowType }.forEach { window ->
                        SandGlass(
                            leftPercent = window.leftPercent,
                            sand = ProviderColors.of(connection.provider),
                            stale = lapsed || snapshot.lastFailure != null,
                            width = 18.dp,
                            height = 34.dp,
                        )
                    }
                }
            }
            GlyphIcon(Glyph.CHEVRON, tint = muted, size = 20.dp)
        }
        snapshot.lastFailure?.let { ErrataSlip(connection.provider, it, now, Modifier.padding(bottom = 12.dp)) }
        when {
            disconnected -> PlateButton("Connect", onConnect, Modifier.fillMaxWidth().padding(bottom = 20.dp), enabled = supported)
            lapsed -> Row(Modifier.padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                PlateButton("Reconnect", onConnect)
                PlateButton("Sign out", onDisconnect, style = PlateStyle.QUIET, color = muted)
            }
            else -> Unit
        }
    }
}

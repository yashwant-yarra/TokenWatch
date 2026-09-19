package com.tokenwatch.data.repository

import com.tokenwatch.data.local.SnapshotDao
import com.tokenwatch.data.local.SnapshotEntity
import com.tokenwatch.data.web.parseSanitizedQuotaPayload
import com.tokenwatch.domain.model.AiProvider
import com.tokenwatch.domain.model.ConnectionStatus
import com.tokenwatch.domain.model.IntegrationStatus
import com.tokenwatch.domain.model.ProviderCapabilities
import com.tokenwatch.domain.model.ProviderConnection
import com.tokenwatch.domain.model.ProviderSnapshot
import com.tokenwatch.domain.model.UsageFailure
import com.tokenwatch.domain.repository.UsageAlertSink
import com.tokenwatch.domain.repository.UsageRepository
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json

@Singleton
class UsageRepositoryImpl @Inject constructor(
    private val dao: SnapshotDao,
    private val json: Json,
    private val clock: Clock,
    private val alerts: UsageAlertSink,
) : UsageRepository {

    private val _refreshing = MutableStateFlow<Set<AiProvider>>(emptySet())
    override val refreshing: StateFlow<Set<AiProvider>> = _refreshing.asStateFlow()

    private val _hasRefreshed = MutableStateFlow(false)
    override val hasRefreshed: StateFlow<Boolean> = _hasRefreshed.asStateFlow()

    override val snapshots: Flow<List<ProviderSnapshot>> = dao.observeAll()
        .map { rows -> rows.mapNotNull(::decode).sortedBy { it.provider.ordinal } }
        .distinctUntilChanged()

    override fun snapshot(provider: AiProvider): Flow<ProviderSnapshot?> =
        snapshots.map { list -> list.firstOrNull { it.provider == provider } }.distinctUntilChanged()

    override suspend fun refreshAll() {
        AiProvider.entries.forEach { provider ->
            if (dao.get(provider.name) == null) write(ProviderSnapshot(connection = placeholderConnection(provider)))
        }
        _hasRefreshed.value = true
    }

    override suspend fun disconnect(provider: AiProvider) {
        write(ProviderSnapshot(connection = placeholderConnection(provider)))
    }

    override fun requestRefresh(provider: AiProvider) = _refreshing.update { it + provider }

    override suspend fun saveWebPayload(provider: AiProvider, rawJson: String) {
        val observedAt = clock.instant()
        val payload = parseSanitizedQuotaPayload(json, rawJson, provider, observedAt)
        val old = dao.get(provider.name)?.let(::decode)
        val snapshot = ProviderSnapshot(
            connection = ProviderConnection(
                provider = provider,
                status = ConnectionStatus.CONNECTED,
                integrationStatus = IntegrationStatus.PARTIAL_SUPPORT,
                capabilities = ProviderCapabilities(
                    quotaWindows = true,
                    resetTimes = payload.windows.any { it.resetsAt != null },
                    subscriptionInfo = payload.planName != null,
                ),
                planName = payload.planName,
                integrationNote = "Usage is read on-device from the provider's signed-in usage page.",
                lastSyncedAt = observedAt,
            ),
            quotaWindows = payload.windows,
            fetchedAt = observedAt,
        )
        write(snapshot)
        alerts.onUsageRead(old, snapshot)
    }

    override suspend fun finishRefresh(provider: AiProvider, failure: UsageFailure?) {
        if (failure != null) {
            dao.get(provider.name)?.let(::decode)?.let { old ->
                val status = if (failure == UsageFailure.AuthExpired) ConnectionStatus.EXPIRED else old.connection.status
                write(old.copy(connection = old.connection.copy(status = status), lastFailure = failure))
            }
        }
        _refreshing.update { it - provider }
    }

    override suspend fun clearCache() {
        dao.clear()
        AiProvider.entries.forEach { write(ProviderSnapshot(connection = placeholderConnection(it))) }
    }

    private suspend fun write(snapshot: ProviderSnapshot) {
        dao.upsert(
            SnapshotEntity(
                provider = snapshot.provider.name,
                json = json.encodeToString(ProviderSnapshot.serializer(), snapshot),
                updatedAtMillis = clock.millis(),
            ),
        )
    }

    private fun decode(entity: SnapshotEntity): ProviderSnapshot? =
        runCatching { json.decodeFromString(ProviderSnapshot.serializer(), entity.json) }.getOrNull()

    private fun placeholderConnection(provider: AiProvider) = ProviderConnection(
        provider = provider,
        status = ConnectionStatus.DISCONNECTED,
        integrationStatus = IntegrationStatus.PARTIAL_SUPPORT,
        capabilities = ProviderCapabilities.NONE,
        integrationNote = "Usage is read from the provider page in a private on-device web session.",
    )
}

package com.tokenwatch.feature.websession

import androidx.lifecycle.SavedStateHandle
import com.tokenwatch.data.local.ReadingDao
import com.tokenwatch.data.local.ReadingEntity
import com.tokenwatch.data.local.SnapshotDao
import com.tokenwatch.data.local.SnapshotEntity
import com.tokenwatch.data.repository.UsageRepositoryImpl
import com.tokenwatch.domain.model.AiProvider
import com.tokenwatch.domain.model.ConnectionStatus
import com.tokenwatch.domain.model.UsageFailure
import com.tokenwatch.domain.model.ProviderSnapshot
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Covers saving page reads and the refresh bookkeeping behind them, through the real repository. */
@OptIn(ExperimentalCoroutinesApi::class)
class WebSessionViewModelTest {
    private val rows = mutableMapOf<String, SnapshotEntity>()
    private val dao = object : SnapshotDao {
        override fun observeAll() = MutableStateFlow(rows.values.toList())
        override suspend fun get(provider: String) = rows[provider]
        override suspend fun upsert(entity: SnapshotEntity) { rows[entity.provider] = entity }
        override suspend fun clear() = rows.clear()
    }
    private val history = MutableStateFlow<List<ReadingEntity>>(emptyList())
    private val readings = object : ReadingDao {
        override suspend fun insertAll(readings: List<ReadingEntity>) { history.value = history.value + readings }
        override fun observeSince(provider: String, sinceMillis: Long) =
            history.map { all -> all.filter { it.provider == provider && it.observedAtMillis >= sinceMillis } }
        override fun observeAllSince(sinceMillis: Long) = history.map { all -> all.filter { it.observedAtMillis >= sinceMillis } }
        override suspend fun deleteOlderThan(cutoffMillis: Long) { history.value = history.value.filter { it.observedAtMillis >= cutoffMillis } }
        override suspend fun deleteProvider(provider: String) { history.value = history.value.filter { it.provider != provider } }
        override suspend fun clear() { history.value = emptyList() }
    }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val clock = Clock.fixed(Instant.parse("2026-09-18T12:00:00Z"), ZoneOffset.UTC)
    private val alertReads = mutableListOf<Pair<Double?, Double?>>()
    private val repository = UsageRepositoryImpl(dao, readings, json, clock) { old, new ->
        alertReads += old?.quotaWindows?.single()?.usedPercent to new.quotaWindows.single().usedPercent
    }

    private fun stored(provider: AiProvider = AiProvider.CLAUDE) =
        rows[provider.name]?.let { json.decodeFromString(ProviderSnapshot.serializer(), it.json) }

    private fun viewModel() = WebSessionViewModel(
        SavedStateHandle(mapOf(WebSessionViewModel.ARG_PROVIDER to AiProvider.CLAUDE.name)),
        repository,
        clock,
    )

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun automaticReadWithNoValuesStaysSilent() {
        val vm = viewModel()
        vm.save(EMPTY, manual = false)
        assertNull(vm.uiState.value.message)
        assertEquals(false, vm.uiState.value.saving)
    }

    @Test
    fun manualReadWithNoValuesExplainsWhatToDo() {
        val vm = viewModel()
        vm.save(EMPTY, manual = true)
        assertNotNull(vm.uiState.value.message)
        assertEquals(false, vm.uiState.value.saving)
    }

    @Test
    fun validReadIsSavedOnce() {
        val vm = viewModel()
        vm.save(VALID, manual = false)
        vm.save(VALID, manual = false)
        assertNotNull(vm.uiState.value.savedAt)
        assertEquals(40.0, stored()!!.quotaWindows.single().usedPercent!!, 0.0)
    }

    @Test
    fun expiredRefreshKeepsUsageAndAsksToSignIn() = runTest {
        repository.saveWebPayload(AiProvider.CLAUDE, VALID)
        repository.requestRefresh(AiProvider.CLAUDE)
        repository.finishRefresh(AiProvider.CLAUDE, UsageFailure.AuthExpired)

        val snapshot = stored()!!
        assertEquals(ConnectionStatus.EXPIRED, snapshot.connection.status)
        assertEquals(UsageFailure.AuthExpired, snapshot.lastFailure)
        assertEquals(1, snapshot.quotaWindows.size)
        assertTrue(repository.refreshing.value.isEmpty())
    }

    @Test
    fun alertsSeeThePreviousAndTheNewReading() = runTest {
        repository.saveWebPayload(AiProvider.CLAUDE, VALID)
        repository.saveWebPayload(AiProvider.CLAUDE, VALID.replace("\"percent\":40", "\"percent\":85"))

        assertEquals(listOf<Pair<Double?, Double?>>(null to 40.0, 40.0 to 85.0), alertReads)
    }

    @Test
    fun successfulRefreshClearsTheFailure() = runTest {
        repository.saveWebPayload(AiProvider.CLAUDE, VALID)
        repository.finishRefresh(AiProvider.CLAUDE, UsageFailure.AuthExpired)
        repository.saveWebPayload(AiProvider.CLAUDE, VALID)

        assertEquals(ConnectionStatus.CONNECTED, stored()!!.connection.status)
        assertNull(stored()!!.lastFailure)
    }

    @Test
    fun everyReadIsKeptForHistory() = runTest {
        repository.saveWebPayload(AiProvider.CLAUDE, VALID)
        repository.saveWebPayload(AiProvider.CLAUDE, VALID.replace("\"percent\":40", "\"percent\":55"))

        val kept = repository.history(AiProvider.CLAUDE, Instant.EPOCH).first()
        assertEquals(listOf(40.0, 55.0), kept.map { it.usedPercent })
        assertEquals("weekly", kept.first().windowId)
    }

    @Test
    fun signingOutDeletesThatProvidersHistory() = runTest {
        repository.saveWebPayload(AiProvider.CLAUDE, VALID)
        repository.saveWebPayload(AiProvider.CODEX, VALID)
        repository.disconnect(AiProvider.CLAUDE)

        assertTrue(repository.history(AiProvider.CLAUDE, Instant.EPOCH).first().isEmpty())
        assertEquals(1, repository.history(AiProvider.CODEX, Instant.EPOCH).first().size)
    }

    @Test
    fun readingsOlderThan35DaysAreDropped() = runTest {
        history.value = listOf(ReadingEntity(provider = "CLAUDE", windowId = "old", label = "Old", windowType = "WEEKLY", usedPercent = 10.0, resetsAtMillis = null, observedAtMillis = 0))
        repository.saveWebPayload(AiProvider.CLAUDE, VALID)

        assertEquals(listOf("weekly"), repository.history(AiProvider.CLAUDE, Instant.EPOCH).first().map { it.windowId })
    }

    private companion object {
        const val EMPTY = """{"schemaVersion":1,"planName":null,"windows":[]}"""
        const val VALID = """{"schemaVersion":1,"windows":[{"windowId":"weekly","label":"Weekly","windowType":"WEEKLY","percent":40,"percentMeaning":"USED"}]}"""
    }
}

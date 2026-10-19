package com.tokenwatch.domain.repository

import com.tokenwatch.domain.model.AiProvider
import com.tokenwatch.domain.model.ProviderSnapshot
import com.tokenwatch.domain.model.UsageFailure
import com.tokenwatch.domain.model.QuotaWindow
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Room is the single source of truth: refreshes write to it and the UI observes it. */
interface UsageRepository {
    val snapshots: Flow<List<ProviderSnapshot>>
    val refreshing: StateFlow<Set<AiProvider>>
    val hasRefreshed: StateFlow<Boolean>

    fun snapshot(provider: AiProvider): Flow<ProviderSnapshot?>

    suspend fun refreshAll()

    suspend fun disconnect(provider: AiProvider)

    /** Asks the on-screen page reader to re-read [provider]'s usage page; [refreshing] tracks it. */
    fun requestRefresh(provider: AiProvider)

    /** Validates a page-extraction payload and stores it. Throws if it holds no usable quota. */
    suspend fun saveWebPayload(provider: AiProvider, rawJson: String)

    /** Ends a refresh. A [failure] keeps the cached usage and is shown next to it. */
    suspend fun finishRefresh(provider: AiProvider, failure: UsageFailure?)

    suspend fun clearCache()

    /** Every stored reading of [provider]'s windows since [since], oldest first. */
    fun history(provider: AiProvider, since: Instant): Flow<List<QuotaWindow>>

    /** Every stored reading of every provider since [since], oldest first. */
    fun historyAll(since: Instant): Flow<List<QuotaWindow>>
}

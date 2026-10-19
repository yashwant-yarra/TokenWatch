package com.tokenwatch.feature.websession

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.tokenwatch.domain.model.AiProvider
import com.tokenwatch.domain.model.ConnectionStatus
import com.tokenwatch.domain.model.ProviderSnapshot
import com.tokenwatch.domain.model.UsageFailure
import dagger.hilt.android.EntryPointAccessors
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Re-reads a provider just after each of its windows resets, so the reset notification is confirmed
 * by the page rather than guessed from the clock. One queued read per window; a newer reading
 * replaces it. Android may run it a few minutes late while the phone is idle.
 */
object ResetWatch {
    private const val TAG = "reset_read"

    // Pages take a moment to roll over, so the read waits a little past the reset time.
    private val GRACE: Duration = Duration.ofMinutes(2)

    /** Queues a read at each of [snapshot]'s upcoming reset times. */
    fun schedule(context: Context, snapshot: ProviderSnapshot, now: Instant) {
        val work = WorkManager.getInstance(context)
        snapshot.quotaWindows.forEach { window ->
            val resetAt = window.resetsAt?.takeIf { it.isAfter(now) } ?: return@forEach
            val request = OneTimeWorkRequestBuilder<ResetReadWorker>()
                .setInitialDelay(Duration.between(now, resetAt).plus(GRACE).toMillis(), TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.LINEAR, 2, TimeUnit.MINUTES)
                .setInputData(workDataOf(KEY_PROVIDER to snapshot.provider.name, KEY_WINDOW to window.windowId))
                .addTag(TAG)
                .build()
            work.enqueueUniqueWork("$TAG:${snapshot.provider.name}:${window.windowId}", ExistingWorkPolicy.REPLACE, request)
        }
    }

    /** Drops every queued reset read, for when reset notifications are turned off. */
    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
    }

    internal const val KEY_PROVIDER = "provider"
    internal const val KEY_WINDOW = "window"
}

/**
 * Reads one provider after a reset. Saving the reading is what notifies: the alert sink compares it
 * with the reading before. If the page hasn't rolled over yet, it tries again a few times.
 */
class ResetReadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val provider = inputData.getString(ResetWatch.KEY_PROVIDER)?.let { name -> AiProvider.entries.firstOrNull { it.name == name } }
            ?: return Result.failure()
        val windowId = inputData.getString(ResetWatch.KEY_WINDOW) ?: return Result.failure()
        val deps = EntryPointAccessors.fromApplication(applicationContext, UsageRefreshWorkerEntryPoint::class.java)
        val repository = deps.usageRepository()
        val before = repository.snapshots.first().firstOrNull { it.provider == provider }
        if (before?.connection?.status != ConnectionStatus.CONNECTED) return Result.success()

        val failure = withContext(Dispatchers.Main) {
            readInvisibleWebView(applicationContext, provider) { payload ->
                runCatching { repository.saveWebPayload(provider, payload) }.isSuccess
            }
        }
        if (failure == UsageFailure.AuthExpired) {
            repository.finishRefresh(provider, failure)
            return Result.success()
        }
        // Still showing the old window (its reset time is behind us): the page hasn't caught up yet.
        val window = repository.snapshots.first().firstOrNull { it.provider == provider }
            ?.quotaWindows?.firstOrNull { it.windowId == windowId }
        val notRolledOver = window?.resetsAt?.isAfter(Instant.now()) == false
        return if ((failure != null || notRolledOver) && runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
    }

    private companion object {
        const val MAX_RETRIES = 3
    }
}

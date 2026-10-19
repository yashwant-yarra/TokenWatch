package com.tokenwatch.feature.websession

import android.content.Context
import android.view.View
import android.webkit.WebView
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tokenwatch.data.web.UsagePageSpec
import com.tokenwatch.domain.model.AiProvider
import com.tokenwatch.domain.model.ConnectionStatus
import com.tokenwatch.domain.model.UsageFailure
import com.tokenwatch.domain.repository.SettingsRepository
import com.tokenwatch.domain.repository.UsageRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Schedules the background read of every connected provider. Android's periodic work can't run more
 * often than every 15 minutes, so each run instead queues the next one-off run [minutes] later.
 * Android may still delay runs while the phone is idle.
 */
object BackgroundRefresh {
    private const val WORK_NAME = "usage_refresh_chain"

    /**
     * Applies a new interval; 0 turns it off. [restart] replaces the queued run, for when the
     * setting changes; otherwise an existing chain is kept, so opening the app doesn't reset it.
     */
    fun schedule(context: Context, minutes: Int, restart: Boolean) {
        val work = WorkManager.getInstance(context)
        work.cancelUniqueWork("background_usage_refresh") // Periodic job from an earlier test build.
        if (minutes <= 0) {
            work.cancelUniqueWork(WORK_NAME)
            return
        }
        val policy = if (restart) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
        work.enqueueUniqueWork(WORK_NAME, policy, request(minutes))
    }

    /** Queues the run after the current one; it starts once the current run finishes. */
    internal fun scheduleNext(context: Context, minutes: Int) {
        if (minutes > 0) {
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request(minutes))
        }
    }

    private fun request(minutes: Int) = OneTimeWorkRequestBuilder<UsageRefreshWorker>()
        .setInitialDelay(minutes.toLong(), TimeUnit.MINUTES)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .build()
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface UsageRefreshWorkerEntryPoint {
    fun usageRepository(): UsageRepository

    fun settingsRepository(): SettingsRepository
}

/**
 * Reads each connected provider's usage page in a web view that is never shown, using the sign-in
 * already saved in the app. Only sign-outs are recorded as failures: a page that is merely slow in
 * the background shouldn't put an error on the dashboard.
 */
class UsageRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, UsageRefreshWorkerEntryPoint::class.java)
        try {
            readConnectedProviders(applicationContext, deps.usageRepository())
        } finally {
            // Always queue the next run, even after a failure, so the chain never stops by itself.
            BackgroundRefresh.scheduleNext(applicationContext, deps.settingsRepository().settings.first().refreshIntervalMinutes)
        }
        return Result.success()
    }
}

/**
 * Reads every connected provider in turn, in web views that are never shown. Only sign-outs are
 * recorded as failures: a page that is merely slow in the background shouldn't put an error on the dashboard.
 */
internal suspend fun readConnectedProviders(context: Context, repository: UsageRepository) {
    val connected = repository.snapshots.first()
        .filter { it.connection.status == ConnectionStatus.CONNECTED }
        .map { it.provider }
    connected.forEach { provider ->
        val failure = withContext(Dispatchers.Main) {
            readInvisibleWebView(context, provider) { payload ->
                runCatching { repository.saveWebPayload(provider, payload) }.isSuccess
            }
        }
        if (failure == UsageFailure.AuthExpired) repository.finishRefresh(provider, failure)
    }
}

/** Loads [provider]'s usage page in a web view laid out at screen size but never attached to a window. Main thread only. */
internal suspend fun readInvisibleWebView(context: Context, provider: AiProvider, save: suspend (String) -> Boolean): UsageFailure? {
    val spec = UsagePageSpec.forProvider(provider)
    val webView = WebView(context)
    return try {
        webView.configureForUsagePage(spec)
        // Pages lay out to the view's size; an unattached view is 0x0 unless measured by hand.
        val metrics = context.resources.displayMetrics
        webView.measure(
            View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.EXACTLY),
        )
        webView.layout(0, 0, metrics.widthPixels, metrics.heightPixels)
        webView.loadUrl(spec.url)
        webView.readUsage(spec, save)
    } finally {
        webView.stopLoading()
        webView.destroy()
    }
}

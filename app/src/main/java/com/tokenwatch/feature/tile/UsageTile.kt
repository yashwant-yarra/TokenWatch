package com.tokenwatch.feature.tile

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tokenwatch.R
import com.tokenwatch.core.util.Format
import com.tokenwatch.domain.model.ConnectionStatus
import com.tokenwatch.domain.model.ProviderSnapshot
import com.tokenwatch.domain.repository.UsageRepository
import com.tokenwatch.feature.websession.UsageRefreshWorkerEntryPoint
import com.tokenwatch.feature.websession.readConnectedProviders
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** What the tile says: the window closest to empty, or that there's nothing to show yet. */
data class TileText(val label: String, val subtitle: String, val active: Boolean)

fun tileText(snapshots: List<ProviderSnapshot>, now: Instant): TileText {
    val lowest = snapshots
        .filter { it.connection.status != ConnectionStatus.DISCONNECTED }
        .flatMap { snapshot -> snapshot.quotaWindows.mapNotNull { window -> window.usedPercent?.let { Triple(snapshot, window, 100.0 - it) } } }
        .minByOrNull { it.third }
        ?: return TileText("TokenWatch", "No readings yet", active = false)
    val (snapshot, window, left) = lowest
    val detail = when {
        snapshot.lastFailure != null -> "last reading"
        else -> window.resetsAt?.takeIf { it.isAfter(now) }?.let { Format.resetLabel(it, now).replaceFirstChar(Char::lowercaseChar) }
    }
    return TileText(
        label = "${snapshot.provider.displayName} · ${Format.percent(left.coerceIn(0.0, 100.0))} left",
        subtitle = listOfNotNull(window.label, detail).joinToString(" · "),
        active = true,
    )
}

/**
 * Quick Settings tile showing the window closest to empty. Tapping re-reads every connected provider
 * in the background; long-pressing opens the app (MainActivity handles QS_TILE_PREFERENCES).
 */
@AndroidEntryPoint
class UsageTileService : TileService() {
    @Inject lateinit var repository: UsageRepository

    @Inject lateinit var clock: Clock

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        scope.launch { show(tileText(repository.snapshots.first(), clock.instant())) }
    }

    override fun onClick() {
        val tile = qsTile ?: return
        if (tile.state == Tile.STATE_UNAVAILABLE) return
        tile.subtitleCompat("Reading…")
        tile.updateTile()
        UsageTile.refresh(this)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun show(text: TileText) {
        val tile = qsTile ?: return
        tile.label = text.label
        tile.subtitleCompat(text.subtitle)
        tile.contentDescription = "${text.label}. ${text.subtitle}"
        tile.state = if (text.active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    private fun Tile.subtitleCompat(text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) subtitle = text
    }
}

object UsageTile {
    private const val WORK_NAME = "tile_refresh"

    private fun component(context: Context) = ComponentName(context, UsageTileService::class.java)

    /** Asks the system to redraw the tile from the latest cached readings. */
    fun requestUpdate(context: Context) {
        runCatching { TileService.requestListeningState(context, component(context)) }
    }

    /** One background read of every connected provider; a tap while one is running is ignored. */
    fun refresh(context: Context) {
        val request = OneTimeWorkRequestBuilder<TileRefreshWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    /** Offers to place the tile in Quick Settings (Android 13+), so it needn't be found in the tile editor. */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun requestAdd(context: Context) {
        context.getSystemService(StatusBarManager::class.java).requestAddTileService(
            component(context),
            context.getString(R.string.tile_label),
            Icon.createWithResource(context, R.drawable.ic_notification),
            context.mainExecutor,
        ) { }
    }
}

/** The tile's refresh: reads every connected provider, then redraws the tile. */
class TileRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repository = EntryPointAccessors.fromApplication(applicationContext, UsageRefreshWorkerEntryPoint::class.java).usageRepository()
        try {
            readConnectedProviders(applicationContext, repository)
        } finally {
            UsageTile.requestUpdate(applicationContext)
        }
        return Result.success()
    }
}

package com.tokenwatch

import android.app.Application
import com.tokenwatch.domain.repository.UsageRepository
import com.tokenwatch.feature.tile.UsageTile
import com.tokenwatch.feature.widget.UsageWidgets
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@HiltAndroidApp
class TokenWatchApp : Application() {
    @Inject lateinit var usageRepository: UsageRepository

    override fun onCreate() {
        super.onCreate()
        // Every usage write (sign-in, pull to refresh, background refresh) lands in Room, so one observer keeps widgets current.
        usageRepository.snapshots
            .onEach {
                UsageWidgets.updateAll(this, it)
                UsageTile.requestUpdate(this)
            }
            .launchIn(CoroutineScope(SupervisorJob() + Dispatchers.Default))
    }
}

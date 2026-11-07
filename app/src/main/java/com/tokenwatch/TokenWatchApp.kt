package com.tokenwatch

import android.app.Application
import com.tokenwatch.core.ui.components.alertLimits
import com.tokenwatch.domain.repository.SettingsRepository
import com.tokenwatch.domain.repository.UsageRepository
import com.tokenwatch.feature.tile.UsageTile
import com.tokenwatch.feature.widget.UsageWidgets
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

@HiltAndroidApp
class TokenWatchApp : Application() {
    @Inject lateinit var usageRepository: UsageRepository
    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate() {
        super.onCreate()
        // Every usage write (sign-in, pull to refresh, background refresh) lands in Room, so one observer keeps widgets current.
        // Widgets draw the alert limits too, so a change in Settings redraws them.
        usageRepository.snapshots
            .onEach { UsageTile.requestUpdate(this) }
            .combine(settingsRepository.settings.map { it.alertLimits() }.distinctUntilChanged()) { snapshots, limits ->
                UsageWidgets.updateAll(this, snapshots, limits)
            }
            .launchIn(CoroutineScope(SupervisorJob() + Dispatchers.Default))
    }
}

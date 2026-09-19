package com.tokenwatch.data.notify

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.tokenwatch.MainActivity
import com.tokenwatch.R
import com.tokenwatch.core.util.Format
import com.tokenwatch.domain.model.ProviderSnapshot
import com.tokenwatch.domain.repository.SettingsRepository
import com.tokenwatch.domain.repository.UsageAlertSink
import com.tokenwatch.domain.usecase.UsageAlert
import com.tokenwatch.domain.usecase.usageAlerts
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** Posts a notification when a session or weekly quota reaches the limit set in Settings. */
@Singleton
class UsageAlertNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
) : UsageAlertSink {

    override suspend fun onUsageRead(old: ProviderSnapshot?, new: ProviderSnapshot) {
        val settings = settingsRepository.settings.first()
        if (!settings.usageAlertsEnabled || !canNotify()) return
        val alerts = usageAlerts(old, new, settings.sessionAlertPercent, settings.weeklyAlertPercent)
        if (alerts.isEmpty()) return

        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("Usage limit alerts")
                .build(),
        )
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        alerts.forEach { alert ->
            val window = alert.window
            val message = alertMessage(new.provider.displayName, alert, clock.instant()) ?: return@forEach
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(message.title)
                .setContentText(message.text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message.text))
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setWhen(window.observedAt.toEpochMilli())
                .setShowWhen(true)
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .build()
            // One notification per window, so a newer alert replaces an older one for the same window.
            try {
                manager.notify("${new.provider.name}:${window.windowId}".hashCode(), notification)
            } catch (e: SecurityException) {
                return // Notification permission was revoked after the check above.
            }
        }
    }

    internal data class AlertMessage(val title: String, val text: String)

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val CHANNEL_ID = "usage_limit_alerts"
    }
}

/**
 * Notification wording, e.g. "Claude session at 81%" / "Passed your 75% alert · resets in 2h 40m".
 * At 100% the title says the limit is reached. Null if the window has no percent.
 */
internal fun alertMessage(providerName: String, alert: UsageAlert, now: Instant): UsageAlertNotifier.AlertMessage? {
    val used = alert.window.usedPercent ?: return null
    val kind = if (alert.isWeekly) "weekly" else "session"
    val title = when {
        used >= 100 -> "$providerName $kind limit reached"
        alert.isWeekly -> "$providerName weekly usage at ${Format.percent(used)}"
        else -> "$providerName session at ${Format.percent(used)}"
    }
    // A reset that has already passed would be stale news, so it's left out.
    val reset = alert.window.resetsAt?.takeIf { it.isAfter(now) }
        ?.let { Format.resetLabel(it, now).replaceFirstChar(Char::lowercaseChar) }
    val text = listOfNotNull("Passed your ${alert.limitPercent}% alert", reset).joinToString(" · ")
    return UsageAlertNotifier.AlertMessage(title, text)
}

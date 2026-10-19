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
import com.tokenwatch.domain.model.QuotaWindow
import com.tokenwatch.domain.model.QuotaWindowType
import com.tokenwatch.domain.repository.SettingsRepository
import com.tokenwatch.domain.repository.UsageAlertSink
import com.tokenwatch.domain.usecase.UsageAlert
import com.tokenwatch.domain.usecase.quotaResets
import com.tokenwatch.domain.usecase.usageAlerts
import com.tokenwatch.feature.websession.ResetWatch
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Posts a notification when a session or weekly quota reaches the limit set in Settings, and, if
 * turned on, once a window has reset. Every read also queues the re-read at each upcoming reset.
 */
@Singleton
class UsageAlertNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
) : UsageAlertSink {

    override suspend fun onUsageRead(old: ProviderSnapshot?, new: ProviderSnapshot) {
        val settings = settingsRepository.settings.first()
        if (settings.resetAlertsEnabled) {
            ResetWatch.schedule(context, new, clock.instant())
            if (canNotify()) notifyResets(old, new)
        }
        if (settings.usageAlertsEnabled && canNotify()) notifyLimits(old, new, settings.sessionAlertPercent, settings.weeklyAlertPercent)
    }

    private fun notifyResets(old: ProviderSnapshot?, new: ProviderSnapshot) {
        val resets = quotaResets(old, new)
        if (resets.isEmpty()) return
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(RESET_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("Limit resets")
                .build(),
        )
        resets.forEach { window ->
            val message = resetMessage(new.provider.displayName, window, clock.instant())
            post(manager, RESET_CHANNEL_ID, "reset:${new.provider.name}:${window.windowId}", message, window.observedAt)
        }
    }

    private fun notifyLimits(old: ProviderSnapshot?, new: ProviderSnapshot, sessionLimit: Int, weeklyLimit: Int) {
        val alerts = usageAlerts(old, new, sessionLimit, weeklyLimit)
        if (alerts.isEmpty()) return

        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("Usage limit alerts")
                .build(),
        )
        alerts.forEach { alert ->
            val message = alertMessage(new.provider.displayName, alert, clock.instant()) ?: return@forEach
            post(manager, CHANNEL_ID, "${new.provider.name}:${alert.window.windowId}", message, alert.window.observedAt)
        }
    }

    /** One notification per [key], so a newer one replaces an older one for the same window. */
    private fun post(manager: NotificationManagerCompat, channel: String, key: String, message: AlertMessage, at: Instant) {
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(message.title)
            .setContentText(message.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.text))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setWhen(at.toEpochMilli())
            .setShowWhen(true)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(key.hashCode(), notification)
        } catch (e: SecurityException) {
            // Notification permission was revoked after the check.
        }
    }

    internal data class AlertMessage(val title: String, val text: String)

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val CHANNEL_ID = "usage_limit_alerts"
        const val RESET_CHANNEL_ID = "limit_resets"
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

/**
 * Reset wording, e.g. "Codex session reset" / "100% left · next reset in 5h 0m". Without a percent it
 * says only that usage is available again, never a made-up number.
 */
internal fun resetMessage(providerName: String, window: QuotaWindow, now: Instant): UsageAlertNotifier.AlertMessage {
    val title = if (window.windowType == QuotaWindowType.WEEKLY) "$providerName weekly limit reset" else "$providerName session reset"
    val left = window.usedPercent?.let { "${Format.percent((100.0 - it).coerceIn(0.0, 100.0))} left" }
    val next = window.resetsAt?.takeIf { it.isAfter(now) }?.let { Format.resetLabel(it, now).replaceFirst("Resets", "next reset") }
    val text = listOfNotNull(left, next).joinToString(" · ").ifEmpty { "Usage is available again" }
    return UsageAlertNotifier.AlertMessage(title, text)
}

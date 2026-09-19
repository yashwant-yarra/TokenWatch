package com.tokenwatch.feature.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.widget.RemoteViews
import com.tokenwatch.MainActivity
import com.tokenwatch.R
import com.tokenwatch.core.ui.theme.ProviderColors
import com.tokenwatch.core.util.Format
import com.tokenwatch.domain.model.AiProvider
import com.tokenwatch.domain.model.ProviderSnapshot
import com.tokenwatch.domain.model.QuotaWindowType
import com.tokenwatch.domain.repository.UsageRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class WidgetKind(val title: String, val types: Set<QuotaWindowType>) {
    SESSION("Current session", setOf(QuotaWindowType.ROLLING_HOURS, QuotaWindowType.DAILY)),
    WEEKLY("Weekly", setOf(QuotaWindowType.WEEKLY)),
}

/** One ring. A null percent means not connected or not reported, shown as an empty ring with a faded logo, never as 0%. */
data class WidgetMeter(val provider: AiProvider, val usedPercent: Double?)

/** One meter per provider, always in the same order, so rings never swap places. */
fun widgetMeters(snapshots: List<ProviderSnapshot>, kind: WidgetKind): List<WidgetMeter> =
    AiProvider.entries.map { provider ->
        val snapshot = snapshots.firstOrNull { it.provider == provider }?.takeIf { it.isActive }
        val window = snapshot?.quotaWindows?.firstOrNull { it.windowType in kind.types }
        WidgetMeter(provider, window?.usedPercent)
    }

object UsageWidgets {
    private val meterIds = listOf(R.id.meter_0, R.id.meter_1, R.id.meter_2)

    /** Redraws every placed widget of both kinds. Called whenever cached usage changes. */
    fun updateAll(context: Context, snapshots: List<ProviderSnapshot>) {
        val manager = AppWidgetManager.getInstance(context)
        WidgetKind.entries.forEach { kind ->
            val ids = manager.getAppWidgetIds(ComponentName(context, receiverFor(kind)))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, render(context, kind, snapshots))
        }
    }

    fun render(context: Context, kind: WidgetKind, snapshots: List<ProviderSnapshot>): RemoteViews {
        val isDark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val views = RemoteViews(context.packageName, R.layout.widget_usage)
        widgetMeters(snapshots, kind).forEachIndexed { i, meter ->
            val name = meter.provider.displayName
            views.setImageViewBitmap(meterIds[i], ring(context, meter, isDark))
            views.setContentDescription(
                meterIds[i],
                "$name ${kind.title.lowercase()}: " + (meter.usedPercent?.let { "${Format.percent(it)} used" } ?: "not available"),
            )
        }
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        views.setOnClickPendingIntent(R.id.widget_root, open)
        return views
    }

    // The ring is a bitmap drawn for the theme at render time; a light/dark switch shows on the next usage update.
    private fun ring(context: Context, meter: WidgetMeter, isDark: Boolean): Bitmap {
        val density = context.resources.displayMetrics.density
        val size = (48 * density).toInt()
        val stroke = 4.5f * density
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bounds = RectF(stroke / 2, stroke / 2, size - stroke / 2, size - stroke / 2)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
        }
        paint.color = context.getColor(R.color.widget_track)
        canvas.drawArc(bounds, 0f, 360f, false, paint)
        val percent = meter.usedPercent
        if (percent != null && percent > 0) {
            // Same rule as the app's bars: near the limit the ring turns to the error color.
            paint.color = if (percent >= 90) context.getColor(R.color.widget_error) else ProviderColors.argb(meter.provider, isDark)
            canvas.drawArc(bounds, -90f, (percent.coerceIn(0.0, 100.0) * 3.6).toFloat(), false, paint)
        }
        // Provider logo in the middle. Faded when there's no number, so an empty ring never reads as 0%.
        val logo = context.getDrawable(logoFor(meter.provider))!!.mutate()
        // The OpenAI mark is monochrome, so it follows the theme's text color like in the app.
        if (meter.provider == AiProvider.CODEX) logo.setTint(context.getColor(R.color.widget_text))
        val inset = (size * 0.28f).toInt()
        logo.setBounds(inset, inset, size - inset, size - inset)
        logo.alpha = if (percent == null) 90 else 255
        logo.draw(canvas)
        return bitmap
    }

    private fun logoFor(provider: AiProvider) = when (provider) {
        AiProvider.CLAUDE -> R.drawable.ic_provider_claude
        AiProvider.CODEX -> R.drawable.ic_provider_codex
        AiProvider.GEMINI -> R.drawable.ic_provider_gemini
    }

    private fun receiverFor(kind: WidgetKind) = when (kind) {
        WidgetKind.SESSION -> SessionWidgetReceiver::class.java
        WidgetKind.WEEKLY -> WeeklyWidgetReceiver::class.java
    }
}

/** Draws a newly placed widget from the cache; later changes arrive through [UsageWidgets.updateAll]. */
abstract class UsageWidgetReceiver(private val kind: WidgetKind) : AppWidgetProvider() {
    @Inject lateinit var repository: UsageRepository

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                manager.updateAppWidget(ids, UsageWidgets.render(context, kind, repository.snapshots.first()))
            } finally {
                pending.finish()
            }
        }
    }
}

@AndroidEntryPoint
class SessionWidgetReceiver : UsageWidgetReceiver(WidgetKind.SESSION)

@AndroidEntryPoint
class WeeklyWidgetReceiver : UsageWidgetReceiver(WidgetKind.WEEKLY)

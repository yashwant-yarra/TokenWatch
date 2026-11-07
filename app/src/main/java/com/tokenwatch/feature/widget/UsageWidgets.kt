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
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import android.graphics.Paint
import android.view.View
import android.widget.RemoteViews
import com.tokenwatch.MainActivity
import com.tokenwatch.R
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.res.ResourcesCompat
import com.tokenwatch.core.ui.components.GlassLook
import com.tokenwatch.core.ui.components.drawGlass
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

/** Long windows (weekly or monthly) share a widget, grouped the way alert limits are. */
enum class WidgetKind(val title: String, val includes: (QuotaWindowType) -> Boolean, val providers: List<AiProvider>) {
    // Copilot has no session window, so it would always be an empty glass here.
    SESSION("Current session", { it == QuotaWindowType.ROLLING_HOURS || it == QuotaWindowType.DAILY }, AiProvider.entries - AiProvider.COPILOT),
    WEEKLY("Weekly", { it.usesWeeklyLimit }, AiProvider.entries),
}

/**
 * One glass. A null percent means not connected or not reported, shown as an empty dashed glass, never as 0%.
 * [windowLabel] is the page's own name for the window ("Monthly credits"), when there is one.
 */
data class WidgetMeter(val provider: AiProvider, val usedPercent: Double?, val windowLabel: String? = null) {
    val leftPercent: Double? get() = usedPercent?.let { (100.0 - it).coerceIn(0.0, 100.0) }
}

/** One meter per provider, always in the same order, so glasses never swap places. */
fun widgetMeters(snapshots: List<ProviderSnapshot>, kind: WidgetKind): List<WidgetMeter> =
    kind.providers.map { provider ->
        val snapshot = snapshots.firstOrNull { it.provider == provider }?.takeIf { it.isActive }
        val window = snapshot?.quotaWindows?.firstOrNull { kind.includes(it.windowType) }
        WidgetMeter(provider, window?.usedPercent, window?.label)
    }

object UsageWidgets {
    private val meterIds = listOf(R.id.meter_0, R.id.meter_1, R.id.meter_2, R.id.meter_3)

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
        val meters = widgetMeters(snapshots, kind)
        views.setViewVisibility(R.id.meter_column_3, if (meters.size > 3) View.VISIBLE else View.GONE)
        meters.forEachIndexed { i, meter ->
            val name = meter.provider.displayName
            views.setImageViewBitmap(meterIds[i], glass(context, meter, isDark))
            views.setContentDescription(
                meterIds[i],
                "$name ${(meter.windowLabel ?: kind.title).lowercase()}: " + (meter.leftPercent?.let { "${Format.percent(it)} left" } ?: "not available"),
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

    // A glass and its reading in one bitmap, drawn for the theme at render time with the app's own
    // glass drawing; a light/dark switch shows on the next usage update.
    private fun glass(context: Context, meter: WidgetMeter, isDark: Boolean): Bitmap {
        val density = context.resources.displayMetrics.density
        val bitmap = ImageBitmap((64 * density).toInt(), (56 * density).toInt())
        val canvas = ComposeCanvas(bitmap)
        val left = meter.leftPercent
        val look = GlassLook.of(
            dark = isDark,
            rim = Color(context.getColor(R.color.widget_glass)),
            ink = Color(context.getColor(R.color.widget_text)),
            alert = Color(context.getColor(R.color.widget_error)),
        )
        canvas.save()
        canvas.translate(0f, 4 * density)
        CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, canvas, Size(22 * density, 48 * density)) {
            drawGlass(
                left = left?.let { (it / 100.0).toFloat().coerceIn(0f, 1f) },
                sand = Color(ProviderColors.argb(meter.provider, isDark)),
                look = look,
                alertLeft = null,
                stale = left == null,
            )
        }
        canvas.restore()

        val android = bitmap.asAndroidBitmap()
        val draw = Canvas(android)
        val x = 28 * density
        // Provider mark above the numeral. The OpenAI and Copilot marks are monochrome, so they follow the text color.
        val logo = context.getDrawable(logoFor(meter.provider))!!.mutate()
        if (meter.provider == AiProvider.CODEX || meter.provider == AiProvider.COPILOT) logo.setTint(context.getColor(R.color.widget_text))
        logo.setBounds(x.toInt(), (4 * density).toInt(), (x + 13 * density).toInt(), (17 * density).toInt())
        logo.draw(draw)
        val engraved = ResourcesCompat.getFont(context, R.font.barlow_condensed_semibold)
        // Near the limit the numeral turns to the alert color, the same rule as the app without alert lines.
        val nearLimit = left != null && left <= 10.0
        draw.drawText(
            left?.let(Format::percent) ?: "–",
            x,
            38 * density,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = engraved
                textSize = 21 * context.resources.displayMetrics.scaledDensity
                color = context.getColor(if (nearLimit) R.color.widget_error else R.color.widget_text)
            },
        )
        draw.drawText(
            if (left == null) "n/a" else "left",
            x,
            50 * density,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 10 * context.resources.displayMetrics.scaledDensity
                color = context.getColor(R.color.widget_muted)
            },
        )
        return android
    }

    private fun logoFor(provider: AiProvider) = when (provider) {
        AiProvider.CLAUDE -> R.drawable.ic_provider_claude
        AiProvider.CODEX -> R.drawable.ic_provider_codex
        AiProvider.GEMINI -> R.drawable.ic_provider_gemini
        AiProvider.COPILOT -> R.drawable.ic_provider_copilot
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

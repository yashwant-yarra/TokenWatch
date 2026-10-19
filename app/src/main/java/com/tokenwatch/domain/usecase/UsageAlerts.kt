package com.tokenwatch.domain.usecase

import com.tokenwatch.domain.model.ProviderSnapshot
import com.tokenwatch.domain.model.QuotaWindow
import com.tokenwatch.domain.model.QuotaWindowType

/** A quota window that has just reached its alert limit. */
data class UsageAlert(val window: QuotaWindow, val limitPercent: Int) {
    val isWeekly: Boolean get() = window.windowType == QuotaWindowType.WEEKLY
}

/**
 * Windows in [new] that reached their limit since [old]: at or above it now, and below it (or not
 * read) before. Comparing with the previous read means each crossing is announced once, not on
 * every refresh. Weekly windows use [weeklyLimit]; every shorter window counts as a session.
 */
fun usageAlerts(old: ProviderSnapshot?, new: ProviderSnapshot, sessionLimit: Int, weeklyLimit: Int): List<UsageAlert> =
    new.quotaWindows.mapNotNull { window ->
        val used = window.usedPercent ?: return@mapNotNull null
        val limit = if (window.windowType == QuotaWindowType.WEEKLY) weeklyLimit else sessionLimit
        val before = old?.quotaWindows?.firstOrNull { it.windowId == window.windowId }?.usedPercent
        // A window that resets and is already over the limit on its first read after the
        // reset isn't announced again; compare reset times if that case matters.
        if (used >= limit && (before == null || before < limit)) UsageAlert(window, limit) else null
    }

/**
 * Windows that reset between [old] and [new]: the old reset time has passed by the new read, and the
 * page shows it, either as usage dropping or as a later reset time. A reset time passing on its own
 * isn't enough, because the page may not have caught up. Windows that weren't used have nothing to announce.
 */
fun quotaResets(old: ProviderSnapshot?, new: ProviderSnapshot): List<QuotaWindow> =
    new.quotaWindows.filter { window ->
        val before = old?.quotaWindows?.firstOrNull { it.windowId == window.windowId } ?: return@filter false
        val resetAt = before.resetsAt ?: return@filter false
        val usedBefore = before.usedPercent ?: return@filter false
        if (usedBefore <= 0.0 || window.observedAt.isBefore(resetAt)) return@filter false
        val dropped = window.usedPercent?.let { it < usedBefore } ?: false
        val movedOn = window.resetsAt?.isAfter(resetAt) ?: false
        dropped || movedOn
    }

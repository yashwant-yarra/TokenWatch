package com.tokenwatch.data.notify

import com.tokenwatch.domain.model.AiProvider
import com.tokenwatch.domain.model.QuotaWindow
import com.tokenwatch.domain.model.QuotaWindowType
import com.tokenwatch.domain.model.UsageSource
import com.tokenwatch.domain.usecase.UsageAlert
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class AlertMessageTest {
    private val now = Instant.parse("2026-09-18T12:00:00Z")

    private fun alert(type: QuotaWindowType, used: Double, resetsIn: Duration?, limit: Int = 75) = UsageAlert(
        QuotaWindow(
            provider = AiProvider.CLAUDE,
            windowId = "w",
            label = "w",
            windowType = type,
            usedPercent = used,
            resetsAt = resetsIn?.let(now::plus),
            observedAt = now,
            source = UsageSource.WEB_PAGE,
        ),
        limit,
    )

    @Test
    fun sessionAlertNamesThePercentLimitAndReset() {
        val message = alertMessage("Claude", alert(QuotaWindowType.ROLLING_HOURS, 81.0, Duration.ofMinutes(160)), now)!!
        assertEquals("Claude session at 81%", message.title)
        assertEquals("Passed your 75% alert · resets in 2h 40m", message.text)
    }

    @Test
    fun fullWindowSaysTheLimitIsReached() {
        val message = alertMessage("Codex", alert(QuotaWindowType.WEEKLY, 100.0, null, limit = 90), now)!!
        assertEquals("Codex weekly limit reached", message.title)
        assertEquals("Passed your 90% alert", message.text)
    }

    @Test
    fun aResetThatAlreadyPassedIsLeftOut() {
        val message = alertMessage("Claude", alert(QuotaWindowType.WEEKLY, 82.0, Duration.ofMinutes(-5)), now)!!
        assertEquals("Claude weekly usage at 82%", message.title)
        assertEquals("Passed your 75% alert", message.text)
    }
}

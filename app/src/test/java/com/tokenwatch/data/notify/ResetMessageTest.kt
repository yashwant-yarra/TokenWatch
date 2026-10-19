package com.tokenwatch.data.notify

import com.tokenwatch.domain.model.AiProvider
import com.tokenwatch.domain.model.QuotaWindow
import com.tokenwatch.domain.model.QuotaWindowType
import com.tokenwatch.domain.model.UsageSource
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class ResetMessageTest {
    private val now = Instant.parse("2026-10-19T15:02:00Z")

    private fun window(type: QuotaWindowType, used: Double?, nextReset: Duration?) = QuotaWindow(
        provider = AiProvider.CODEX,
        windowId = "w",
        label = "w",
        windowType = type,
        usedPercent = used,
        resetsAt = nextReset?.let(now::plus),
        observedAt = now,
        source = UsageSource.WEB_PAGE,
    )

    @Test
    fun sessionResetSaysWhatIsLeftAndWhenItNextResets() {
        val message = resetMessage("Codex", window(QuotaWindowType.ROLLING_HOURS, 0.0, Duration.ofHours(5)), now)
        assertEquals("Codex session reset", message.title)
        assertEquals("100% left · next reset in 5h 0m", message.text)
    }

    @Test
    fun weeklyResetWithoutANextResetTime() {
        val message = resetMessage("Claude", window(QuotaWindowType.WEEKLY, 3.0, null), now)
        assertEquals("Claude weekly limit reset", message.title)
        assertEquals("97% left", message.text)
    }

    @Test
    fun aMissingPercentIsNotInvented() {
        val message = resetMessage("Gemini", window(QuotaWindowType.ROLLING_HOURS, null, null), now)
        assertEquals("Gemini session reset", message.title)
        assertEquals("Usage is available again", message.text)
    }
}

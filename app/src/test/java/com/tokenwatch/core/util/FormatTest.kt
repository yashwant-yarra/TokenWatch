package com.tokenwatch.core.util

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test
    fun tokensAreAbbreviated() {
        assertEquals("950", Format.tokens(950))
        assertEquals("12.5K", Format.tokens(12_500))
        assertEquals("300K", Format.tokens(300_000))
        assertEquals("8.7M", Format.tokens(8_700_000))
        assertEquals("2M", Format.tokens(2_000_000))
        assertEquals("1.2B", Format.tokens(1_200_000_000))
    }

    @Test
    fun durationsReadNaturally() {
        assertEquals("1h 48m", Format.duration(Duration.ofMinutes(108)))
        assertEquals("12m", Format.duration(Duration.ofMinutes(12)))
        assertEquals("2d 3h", Format.duration(Duration.ofHours(51)))
    }

    @Test
    fun agoUsesSingularAndPlural() {
        val now = Instant.parse("2026-09-18T12:00:00Z")
        assertEquals("just now", Format.ago(now.minusSeconds(20), now))
        assertEquals("1 minute ago", Format.ago(now.minusSeconds(60), now))
        assertEquals("24 minutes ago", Format.ago(now.minusSeconds(24 * 60), now))
        assertEquals("3 days ago", Format.ago(now.minus(Duration.ofDays(3)), now))
    }
}

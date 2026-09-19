package com.tokenwatch.data.web

import com.tokenwatch.domain.model.AiProvider
import com.tokenwatch.domain.model.QuotaWindowType
import com.tokenwatch.domain.model.UsageSource
import java.time.Instant
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SanitizedQuotaPayloadParserTest {
    private val json = Json
    private val observedAt = Instant.parse("2026-09-18T12:00:00Z")

    @Test
    fun remainingPercentIsMappedToUsedPercent() {
        val payload = parseSanitizedQuotaPayload(
            json = json,
            rawJson = """{"schemaVersion":1,"planName":"Pro","windows":[{"windowId":"weekly","label":"Weekly","windowType":"WEEKLY","percent":25.5,"percentMeaning":"REMAINING","resetsAt":"2026-09-20T00:00:00Z"}]}""",
            provider = AiProvider.CLAUDE,
            observedAt = observedAt,
        )

        assertEquals("Pro", payload.planName)
        assertEquals(1, payload.windows.size)
        with(payload.windows.single()) {
            assertEquals(AiProvider.CLAUDE, provider)
            assertEquals("weekly", windowId)
            assertEquals("Weekly", label)
            assertEquals(QuotaWindowType.WEEKLY, windowType)
            assertEquals(74.5, usedPercent!!, 0.0)
            assertEquals(Instant.parse("2026-09-20T00:00:00Z"), resetsAt)
            assertEquals(observedAt, this.observedAt)
            assertEquals(UsageSource.WEB_PAGE, source)
        }
    }

    @Test
    fun usedPercentIsPreserved() {
        val payload = parseSanitizedQuotaPayload(
            json,
            """{"schemaVersion":1,"windows":[{"windowId":"five-hour","label":"5 hour","windowType":"ROLLING_HOURS","percent":12,"percentMeaning":"USED"}]}""",
            AiProvider.CODEX,
            observedAt,
        )

        assertEquals(null, payload.planName)
        assertEquals(12.0, payload.windows.single().usedPercent!!, 0.0)
        assertEquals(null, payload.windows.single().resetsAt)
    }

    @Test
    fun invalidPayloadFailsAsAWhole() {
        val invalidPayloads = listOf(
            """{"schemaVersion":2,"windows":[{"windowId":"a","label":"A","windowType":"DAILY","percent":1,"percentMeaning":"USED"}]}""",
            """{"schemaVersion":1,"windows":[]}""",
            """{"schemaVersion":1,"windows":[{"windowId":" ","label":"A","windowType":"DAILY","percent":1,"percentMeaning":"USED"}]}""",
            """{"schemaVersion":1,"windows":[{"windowId":"a","label":" ","windowType":"DAILY","percent":1,"percentMeaning":"USED"}]}""",
            """{"schemaVersion":1,"windows":[{"windowId":"a","label":"A","windowType":"DAILY","percent":101,"percentMeaning":"USED"}]}""",
            """{"schemaVersion":1,"windows":[{"windowId":"a","label":"A","windowType":"DAILY","percent":1,"percentMeaning":"USED"},{"windowId":"a","label":"Again","windowType":"WEEKLY","percent":2,"percentMeaning":"USED"}]}""",
            """{"schemaVersion":1,"windows":[{"windowId":"a","label":"A","windowType":"DAILY","percent":1,"percentMeaning":"USED","resetsAt":"tomorrow"}]}""",
            """{"schemaVersion":1,"planName":"","windows":[{"windowId":"a","label":"A","windowType":"DAILY","percent":1,"percentMeaning":"USED"}]}""",
        )

        invalidPayloads.forEach { rawJson ->
            assertThrows(rawJson, IllegalArgumentException::class.java) {
                parseSanitizedQuotaPayload(json, rawJson, AiProvider.GEMINI, observedAt)
            }
        }
    }
}

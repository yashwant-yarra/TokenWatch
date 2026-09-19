package com.tokenwatch.data.web

import com.tokenwatch.domain.model.AiProvider
import com.tokenwatch.domain.model.QuotaWindow
import com.tokenwatch.domain.model.QuotaWindowType
import com.tokenwatch.domain.model.UsageSource
import java.time.Instant
import java.time.format.DateTimeParseException
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

internal data class SanitizedQuotaPayload(
    val planName: String?,
    val windows: List<QuotaWindow>,
)

internal fun parseSanitizedQuotaPayload(
    json: Json,
    rawJson: String,
    provider: AiProvider,
    observedAt: Instant,
): SanitizedQuotaPayload {
    require(rawJson.length <= 8_192) { "payload is too large" }
    val payload = json.decodeFromString<PayloadDto>(rawJson)
    require(payload.schemaVersion == 1) { "Unsupported schemaVersion: ${payload.schemaVersion}" }
    require(payload.windows.isNotEmpty()) { "windows must not be empty" }
    require(payload.windows.size <= 8) { "too many windows" }
    require(payload.planName == null || payload.planName.isNotBlank() && payload.planName.length <= 64) {
        "planName must be 1 to 64 characters"
    }
    require(payload.windows.map { it.windowId }.toSet().size == payload.windows.size) {
        "windowId values must be unique"
    }

    return SanitizedQuotaPayload(
        planName = payload.planName,
        windows = payload.windows.map { window ->
            require(window.windowId.isNotBlank()) { "windowId must not be blank" }
            require(window.label.isNotBlank()) { "label must not be blank" }
            require(window.windowId.length <= 64) { "windowId is too long" }
            require(window.label.length <= 64) { "label is too long" }
            require(window.percent.isFinite() && window.percent in 0.0..100.0) {
                "percent must be finite and between 0 and 100"
            }
            QuotaWindow(
                provider = provider,
                windowId = window.windowId,
                label = window.label,
                windowType = window.windowType,
                usedPercent = when (window.percentMeaning) {
                    PercentMeaning.USED -> window.percent
                    PercentMeaning.REMAINING -> 100.0 - window.percent
                },
                resetsAt = window.resetsAt?.let(::parseInstant),
                observedAt = observedAt,
                source = UsageSource.WEB_PAGE,
            )
        },
    )
}

private fun parseInstant(value: String): Instant = try {
    Instant.parse(value)
} catch (exception: DateTimeParseException) {
    throw IllegalArgumentException("resetsAt must be an RFC3339 instant", exception)
}

@Serializable
private data class PayloadDto(
    val schemaVersion: Int,
    val planName: String? = null,
    val windows: List<WindowDto>,
)

@Serializable
private data class WindowDto(
    val windowId: String,
    val label: String,
    val windowType: QuotaWindowType,
    val percent: Double,
    val percentMeaning: PercentMeaning,
    val resetsAt: String? = null,
)

@Serializable
private enum class PercentMeaning { USED, REMAINING }

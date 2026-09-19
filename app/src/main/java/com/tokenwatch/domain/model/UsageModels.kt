@file:UseSerializers(InstantSerializer::class, BigDecimalSerializer::class)

package com.tokenwatch.domain.model

import java.math.BigDecimal
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

@Serializable
enum class AiProvider(val displayName: String) {
    CLAUDE("Claude"),
    CODEX("Codex"),
    GEMINI("Gemini"),
}

/** How real the data behind an integration is. Shown to the user on every provider. */
@Serializable
enum class IntegrationStatus { API_AVAILABLE, PARTIAL_SUPPORT, MOCK, NOT_YET_SUPPORTED }

@Serializable
enum class UsageSource { OFFICIAL_API, OAUTH_API, ADMIN_API, LOCAL_TELEMETRY, BILLING_EXPORT, WEB_PAGE, MANUAL, MOCK }

@Serializable
enum class ConnectionStatus { CONNECTED, EXPIRED, REVOKED, DISCONNECTED, ERROR }

/** What a provider integration can actually report. The UI renders only what is true here. */
@Serializable
data class ProviderCapabilities(
    val exactTokens: Boolean = false,
    val tokenTypeBreakdown: Boolean = false,
    val quotaWindows: Boolean = false,
    val resetTimes: Boolean = false,
    val modelBreakdown: Boolean = false,
    val cost: Boolean = false,
    val history: Boolean = false,
    val subscriptionInfo: Boolean = false,
) {
    companion object {
        val NONE = ProviderCapabilities()
    }
}

@Serializable
data class ProviderConnection(
    val provider: AiProvider,
    val status: ConnectionStatus,
    val integrationStatus: IntegrationStatus,
    val capabilities: ProviderCapabilities,
    val accountLabel: String? = null,
    val planName: String? = null,
    /** Plain-language explanation of what the integration can and can't do. */
    val integrationNote: String? = null,
    val lastSyncedAt: Instant? = null,
)

/**
 * Token counters for one period (and optionally one model). Every field is nullable:
 * a provider that doesn't report a token type leaves it null rather than zero.
 */
@Serializable
data class TokenUsageBucket(
    val provider: AiProvider,
    val periodStart: Instant,
    val periodEnd: Instant,
    val modelId: String? = null,
    val modelDisplayName: String? = null,
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val cacheReadTokens: Long? = null,
    val cacheWriteTokens: Long? = null,
    /** Usually a subset of output tokens, so it is never added into totals. */
    val reasoningTokens: Long? = null,
    val totalTokens: Long? = null,
    val source: UsageSource,
) {
    /** The provider's own total when given, otherwise the sum of the non-overlapping parts. */
    val effectiveTotal: Long?
        get() = totalTokens ?: listOfNotNull(inputTokens, outputTokens, cacheReadTokens, cacheWriteTokens)
            .takeIf { it.isNotEmpty() }
            ?.sum()
}

@Serializable
enum class QuotaWindowType { ROLLING_HOURS, DAILY, WEEKLY, MONTHLY, OTHER }

/** A quota gauge tied to a window. Providers report a list of these; nothing is hard-coded. */
@Serializable
data class QuotaWindow(
    val provider: AiProvider,
    val windowId: String,
    val label: String,
    val windowType: QuotaWindowType,
    val usedPercent: Double? = null,
    val used: Double? = null,
    val limit: Double? = null,
    val unit: String? = null,
    val resetsAt: Instant? = null,
    val observedAt: Instant,
    val source: UsageSource,
)

@Serializable
enum class CostKind {
    /** Money actually billed (API usage). */
    ACTUAL_BILLED,

    /** What the same usage would cost at API prices. Not spend for subscription users. */
    API_EQUIVALENT_ESTIMATE,
}

@Serializable
data class CostEntry(
    val provider: AiProvider,
    val periodStart: Instant,
    val periodEnd: Instant,
    val amount: BigDecimal,
    val currency: String,
    val kind: CostKind,
    val pricingSourceDate: String? = null,
)

@Serializable
sealed interface UsageFailure {
    @Serializable
    @SerialName("network")
    data object Network : UsageFailure

    @Serializable
    @SerialName("rate_limited")
    data class RateLimited(val retryAt: Instant? = null) : UsageFailure

    @Serializable
    @SerialName("auth_expired")
    data object AuthExpired : UsageFailure

    @Serializable
    @SerialName("revoked")
    data object Revoked : UsageFailure

    @Serializable
    @SerialName("unsupported")
    data class Unsupported(val detail: String) : UsageFailure

    @Serializable
    @SerialName("provider_unavailable")
    data object ProviderUnavailable : UsageFailure

    @Serializable
    @SerialName("unknown")
    data class Unknown(val message: String? = null) : UsageFailure
}

/** Everything cached about one provider. Room stores one of these per provider. */
@Serializable
data class ProviderSnapshot(
    val connection: ProviderConnection,
    val quotaWindows: List<QuotaWindow> = emptyList(),
    val tokenUsage: List<TokenUsageBucket> = emptyList(),
    val costs: List<CostEntry> = emptyList(),
    val fetchedAt: Instant? = null,
    val lastFailure: UsageFailure? = null,
) {
    val provider: AiProvider get() = connection.provider
    val hasData: Boolean get() = quotaWindows.isNotEmpty() || tokenUsage.isNotEmpty() || costs.isNotEmpty()
    val isActive: Boolean get() = connection.status != ConnectionStatus.DISCONNECTED
}

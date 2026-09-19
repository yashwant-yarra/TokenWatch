package com.tokenwatch.core.ui.components

import com.tokenwatch.core.util.Format
import com.tokenwatch.domain.model.AiProvider
import com.tokenwatch.domain.model.UsageFailure
import java.time.Duration
import java.time.Instant

fun UsageFailure.message(provider: AiProvider, now: Instant): String {
    val name = provider.displayName
    return when (this) {
        UsageFailure.Network -> "$name usage couldn't be refreshed. You're offline or the network is unreachable."
        is UsageFailure.RateLimited -> {
            val wait = retryAt?.let { Duration.between(now, it) }
            if (wait != null && !wait.isNegative) {
                "$name is limiting requests. Next refresh possible in ${Format.duration(wait)}."
            } else {
                "$name is limiting requests. Try again shortly."
            }
        }
        UsageFailure.AuthExpired -> "Your $name connection has expired. Reconnect to continue."
        UsageFailure.Revoked -> "$name access was revoked. Reconnect to continue."
        is UsageFailure.Unsupported -> detail
        UsageFailure.ProviderUnavailable -> "$name isn't responding right now. Cached data is shown."
        is UsageFailure.Unknown -> "$name usage couldn't be refreshed."
    }
}

val UsageFailure.needsReconnect: Boolean
    get() = this == UsageFailure.AuthExpired || this == UsageFailure.Revoked

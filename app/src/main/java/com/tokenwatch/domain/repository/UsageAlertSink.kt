package com.tokenwatch.domain.repository

import com.tokenwatch.domain.model.ProviderSnapshot

/** Told about every fresh usage read, so it can warn when a limit is reached. */
fun interface UsageAlertSink {
    suspend fun onUsageRead(old: ProviderSnapshot?, new: ProviderSnapshot)
}

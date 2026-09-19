package com.tokenwatch.core.util

import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Emits the current time periodically so countdowns and "updated X ago" stay fresh. */
fun clockTicker(clock: Clock, period: Duration = Duration.ofSeconds(30)): Flow<Instant> = flow {
    while (true) {
        emit(clock.instant())
        delay(period.toMillis())
    }
}

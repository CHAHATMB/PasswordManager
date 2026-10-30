package com.example.passwordmanager.data.vault

import android.os.SystemClock

private const val FREE_ATTEMPTS = 2
private const val BASE_DELAY_MILLIS = 5_000L
private const val MAX_DELAY_MILLIS = 300_000L

/** 0 for the first two failures, then 5 s doubling per failure, capped at 5 min. */
fun unlockDelayMillis(failedAttempts: Int): Long {
    if (failedAttempts <= FREE_ATTEMPTS) return 0
    // Capping the exponent keeps the shift from overflowing; 5 s × 2^6 is already past the cap.
    val doublings = (failedAttempts - FREE_ATTEMPTS - 1).coerceAtMost(6)
    return (BASE_DELAY_MILLIS shl doublings).coerceAtMost(MAX_DELAY_MILLIS)
}

/** In memory only (process-wide, so finishing the activity does not reset it). */
class UnlockThrottle {

    private var failedAttempts = 0
    private var blockedUntil = 0L

    fun remainingMillis(): Long = (blockedUntil - SystemClock.elapsedRealtime()).coerceAtLeast(0)

    fun onFailure() {
        failedAttempts++
        blockedUntil = SystemClock.elapsedRealtime() + unlockDelayMillis(failedAttempts)
    }

    fun onSuccess() {
        failedAttempts = 0
        blockedUntil = 0
    }
}

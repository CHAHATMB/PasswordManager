package com.example.passwordmanager.data.vault

import android.content.SharedPreferences

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

/** Persisted, so force-stopping the app doesn't reset the delay. */
class UnlockThrottle(
    private val prefs: SharedPreferences,
    private val now: () -> Long = System::currentTimeMillis
) {

    fun remainingMillis(): Long {
        val delay = unlockDelayMillis(prefs.getInt(KEY_FAILED_ATTEMPTS, 0))
        val sinceLastFailure = now() - prefs.getLong(KEY_LAST_FAILURE_AT, 0)
        // Wall-clock time, because it must survive process death. A clock set backwards makes
        // sinceLastFailure negative; the clamp then restarts the delay from now instead of extending it.
        return (delay - sinceLastFailure).coerceIn(0, delay)
    }

    fun onFailure() {
        // commit, not apply: a kill right after a wrong password must not lose the count.
        prefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1)
            .putLong(KEY_LAST_FAILURE_AT, now())
            .commit()
    }

    fun onSuccess() {
        prefs.edit().remove(KEY_FAILED_ATTEMPTS).remove(KEY_LAST_FAILURE_AT).commit()
    }

    companion object {
        const val PREFS_NAME = "unlock_throttle"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LAST_FAILURE_AT = "last_failure_at"
    }
}

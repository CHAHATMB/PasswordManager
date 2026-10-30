package com.example.passwordmanager.data.vault

import org.junit.Assert.assertEquals
import org.junit.Test

class UnlockThrottleTest {

    @Test
    fun firstTwoFailuresHaveNoDelay() {
        assertEquals("after 1 failure", 0L, unlockDelayMillis(1))
        assertEquals("after 2 failures", 0L, unlockDelayMillis(2))
    }

    @Test
    fun thirdFailureWaitsFiveSeconds() {
        assertEquals(5_000L, unlockDelayMillis(3))
    }

    @Test
    fun eachLaterFailureDoublesTheDelay() {
        assertEquals("after 4 failures", 10_000L, unlockDelayMillis(4))
        assertEquals("after 5 failures", 20_000L, unlockDelayMillis(5))
        assertEquals("after 8 failures", 160_000L, unlockDelayMillis(8))
    }

    @Test
    fun delayIsCappedAtFiveMinutes() {
        assertEquals("5 s × 2^6 = 320 s must be capped", 300_000L, unlockDelayMillis(9))
        assertEquals("large counts must not overflow", 300_000L, unlockDelayMillis(1_000))
    }
}

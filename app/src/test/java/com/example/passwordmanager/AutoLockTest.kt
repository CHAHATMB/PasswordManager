package com.example.passwordmanager

import com.example.passwordmanager.data.settings.AutoLockTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoLockTest {

    private val stoppedAt = 1_000_000L

    @Test
    fun immediatelyLocksWithNoTimeElapsed() {
        assertTrue(shouldLock(stoppedAt, now = stoppedAt, timeout = AutoLockTimeout.IMMEDIATELY))
    }

    @Test
    fun everyTimeoutStaysUnlockedJustBeforeItsBoundary() {
        AutoLockTimeout.entries.filter { it.millis > 0 }.forEach { timeout ->
            assertFalse("$timeout locked 1 ms early", shouldLock(stoppedAt, stoppedAt + timeout.millis - 1, timeout))
        }
    }

    @Test
    fun everyTimeoutLocksAtAndAfterItsBoundary() {
        AutoLockTimeout.entries.forEach { timeout ->
            assertTrue("$timeout did not lock at the boundary", shouldLock(stoppedAt, stoppedAt + timeout.millis, timeout))
            assertTrue("$timeout did not lock after the boundary", shouldLock(stoppedAt, stoppedAt + timeout.millis + 1, timeout))
        }
    }

    @Test
    fun appTripWithImmediatelyStaysUnlockedUntilTheCap() {
        val timeout = AutoLockTimeout.IMMEDIATELY

        assertFalse(shouldLock(stoppedAt, stoppedAt, timeout, isAppTrip = true))
        assertFalse(shouldLock(stoppedAt, stoppedAt + APP_TRIP_MAX_MILLIS - 1, timeout, isAppTrip = true))
        assertTrue(shouldLock(stoppedAt, stoppedAt + APP_TRIP_MAX_MILLIS, timeout, isAppTrip = true))
    }

    @Test
    fun appTripNeverLocksEarlierThanTheChosenTimeout() {
        val timeout = AutoLockTimeout.MINUTES_15

        assertFalse(shouldLock(stoppedAt, stoppedAt + APP_TRIP_MAX_MILLIS, timeout, isAppTrip = true))
        assertTrue(shouldLock(stoppedAt, stoppedAt + timeout.millis, timeout, isAppTrip = true))
    }
}

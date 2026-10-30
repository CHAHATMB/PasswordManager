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
}

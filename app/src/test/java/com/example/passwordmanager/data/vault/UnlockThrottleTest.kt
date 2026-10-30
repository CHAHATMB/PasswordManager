package com.example.passwordmanager.data.vault

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UnlockThrottleTest {

    private val prefs = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences(UnlockThrottle.PREFS_NAME, Context.MODE_PRIVATE)
    private var clock = 1_000_000L

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

    @Test
    fun gateOpensOnlyAfterTheDelayAndSurvivesARestart() {
        val throttle = newThrottle()
        repeat(2) { throttle.onFailure() }
        assertEquals("two failures do not block", 0L, throttle.remainingMillis())

        throttle.onFailure()
        clock += 3_000

        assertEquals(2_000L, throttle.remainingMillis())
        assertEquals("a new instance (cold start) reads the persisted state", 2_000L, newThrottle().remainingMillis())
        clock += 2_000
        assertEquals(0L, newThrottle().remainingMillis())
    }

    @Test
    fun clockSetBackwardsRestartsTheDelayInsteadOfExtendingIt() {
        val throttle = newThrottle()
        repeat(3) { throttle.onFailure() }

        clock -= 3_600_000

        assertEquals(5_000L, newThrottle().remainingMillis())
    }

    @Test
    fun successResetsTheCount() {
        val throttle = newThrottle()
        repeat(3) { throttle.onFailure() }

        throttle.onSuccess()
        throttle.onFailure()

        assertEquals("count restarts at 1 after success", 0L, newThrottle().remainingMillis())
    }

    private fun newThrottle() = UnlockThrottle(prefs, now = { clock })
}

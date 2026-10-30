package com.example.passwordmanager.util

import com.example.passwordmanager.util.SecureClipboard.Companion.CLEAR_DELAY_MILLIS
import com.example.passwordmanager.util.SecureClipboard.Companion.CLIP_LABEL
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SecureClipboardTest {

    private class FakeClipboard : ClipboardAccess {
        var label: CharSequence? = null
        var text: String? = null
        var isReadable = true
        var clearCount = 0

        override fun primaryClipLabel(): CharSequence? = if (isReadable) label else null

        override fun setSensitiveClip(label: String, text: String) {
            this.label = label
            this.text = text
        }

        override fun clear() {
            clearCount++
            label = null
            text = null
        }

        fun copyFromAnotherApp(text: String) {
            label = "Other app"
            this.text = text
        }

        override fun toString() = "FakeClipboard(label=$label, hasText=${text != null}, clears=$clearCount)"
    }

    private val clipboard = FakeClipboard()

    private fun TestScope.secureClipboard() = SecureClipboard(clipboard, backgroundScope)

    @Test
    fun copyIsClearedOnlyOnceTheDelayHasPassed() = runTest {
        val secureClipboard = secureClipboard()

        secureClipboard.copy("gh-secret-1")
        advanceTimeBy(CLEAR_DELAY_MILLIS - 1)
        runCurrent()
        println("Just before the delay: $clipboard")
        assertEquals(CLIP_LABEL, clipboard.label)
        assertEquals("gh-secret-1", clipboard.text)

        advanceTimeBy(1)
        runCurrent()
        println("At the delay: $clipboard")
        assertNull(clipboard.text)
        assertEquals(1, clipboard.clearCount)
    }

    @Test
    fun newerCopyRestartsTheTimer() = runTest {
        val secureClipboard = secureClipboard()

        secureClipboard.copy("first")
        advanceTimeBy(20_000)
        secureClipboard.copy("second")
        advanceTimeBy(CLEAR_DELAY_MILLIS - 1)
        runCurrent()
        println("${CLEAR_DELAY_MILLIS - 1} ms after the second copy: $clipboard")
        assertEquals("second", clipboard.text)
        assertEquals(0, clipboard.clearCount)

        advanceTimeBy(1)
        runCurrent()
        assertNull(clipboard.text)
        assertEquals(1, clipboard.clearCount)
    }

    @Test
    fun anotherAppsNewerClipIsLeftAlone() = runTest {
        val secureClipboard = secureClipboard()

        secureClipboard.copy("gh-secret-1")
        clipboard.copyFromAnotherApp("https://example.com")
        advanceTimeBy(CLEAR_DELAY_MILLIS)
        runCurrent()

        println("After the delay with another app's clip: $clipboard")
        assertEquals("https://example.com", clipboard.text)
        assertEquals(0, clipboard.clearCount)
    }

    @Test
    fun unreadableClipboardIsClearedAnyway() = runTest {
        val secureClipboard = secureClipboard()

        secureClipboard.copy("gh-secret-1")
        clipboard.isReadable = false
        advanceTimeBy(CLEAR_DELAY_MILLIS)
        runCurrent()

        println("After the delay while unreadable: $clipboard")
        assertNull(clipboard.text)
        assertEquals(1, clipboard.clearCount)
    }

    @Test
    fun clearNowClearsAPendingCopyAndCancelsItsTimer() = runTest {
        val secureClipboard = secureClipboard()

        secureClipboard.copy("gh-secret-1")
        advanceTimeBy(5_000)
        secureClipboard.clearNow()
        assertNull(clipboard.text)

        clipboard.copyFromAnotherApp("later clip")
        clipboard.isReadable = false
        advanceTimeBy(CLEAR_DELAY_MILLIS)
        runCurrent()

        println("After clearNow and the original delay: $clipboard")
        assertEquals("later clip", clipboard.text)
        assertEquals(1, clipboard.clearCount)
    }

    @Test
    fun clearNowWithoutAPendingCopyDoesNothing() = runTest {
        val secureClipboard = secureClipboard()
        clipboard.copyFromAnotherApp("user's clip")
        clipboard.isReadable = false

        secureClipboard.clearNow()

        println("clearNow with nothing pending: $clipboard")
        assertEquals("user's clip", clipboard.text)
        assertEquals(0, clipboard.clearCount)
    }
}

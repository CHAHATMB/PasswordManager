package com.example.passwordmanager.data.backup

import com.example.passwordmanager.data.Credential
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ImportFileTest {

    @Test
    fun fileAtTheLimitIsReadWhole() {
        val bytes = ByteArray(20_000) { it.toByte() }

        assertArrayEquals(bytes, ByteArrayInputStream(bytes).readImportFile(limit = bytes.size))
    }

    @Test
    fun fileOneByteOverTheLimitIsTooLarge() {
        assertThrows(BackupException.FileTooLarge::class.java) {
            ByteArrayInputStream(ByteArray(20_001)).readImportFile(limit = 20_000)
        }
    }

    @Test
    fun defaultLimitIsFiveMegabytesAndStopsReadingPastIt() {
        val stream = EndlessStream()

        assertThrows(BackupException.FileTooLarge::class.java) { stream.readImportFile() }
        assertEquals(5L * 1024 * 1024 + 1, stream.served)
    }

    @Test
    fun readFailurePartwayWipesTheBufferAndPropagates() {
        val stream = FailingStream(ByteArray(100) { 7 })

        assertThrows(IOException::class.java) { stream.readImportFile(limit = 1000) }
        assertEquals(0, stream.lastBuffer.count { it != 0.toByte() })
    }

    @Test
    fun rowsWithoutWebsiteOrPasswordAreCountedInvalid() {
        val valid = credential(website = "github.com", password = "secret")
        val parsed = listOf(valid, credential(website = " ", password = "secret"), credential(website = "a.com", password = ""))
            .toParsedImport()

        assertEquals(listOf(valid), parsed.credentials)
        assertEquals(2, parsed.invalid)
    }

    private fun credential(website: String, password: String) =
        Credential(website = website, username = "alice", password = password, dataCreate = 1, note = "")

    /** Serves [bytes], then fails; keeps the buffer it was handed so the test can check it was wiped. */
    private class FailingStream(private val bytes: ByteArray) : InputStream() {
        var lastBuffer = ByteArray(0)
        private var served = false

        override fun read(): Int = throw IOException("disconnected")

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            lastBuffer = buffer
            if (served) throw IOException("disconnected")
            served = true
            bytes.copyInto(buffer, offset)
            return bytes.size
        }
    }

    /** Serves zero bytes forever and counts how many it handed out. */
    private class EndlessStream : InputStream() {
        var served = 0L

        override fun read(): Int = 0.also { served++ }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = length.also { served += it }
    }
}

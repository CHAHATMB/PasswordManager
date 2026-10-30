package com.example.passwordmanager.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SqlCipherRawKeyTest {

    @Test
    fun rawKeyIsHexLiteralOfTheVaultKey() {
        val key = ByteArray(32) { if (it == 0) 0xAB.toByte() else it.toByte() }

        val rawKey = String(CredentialDatabase.sqlCipherRawKey(key), Charsets.US_ASCII)

        val expectedHex = "ab" + (1 until 32).joinToString("") { "%02x".format(it) }
        assertEquals(67, rawKey.length)
        assertEquals("x'$expectedHex'", rawKey)
    }
}

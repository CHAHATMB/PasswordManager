package com.example.passwordmanager.util.totp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Base32Test {

    // RFC 4648 section 10.
    private val vectors = listOf(
        "" to "",
        "f" to "MY======",
        "fo" to "MZXQ====",
        "foo" to "MZXW6===",
        "foob" to "MZXW6YQ=",
        "fooba" to "MZXW6YTB",
        "foobar" to "MZXW6YTBOI======"
    )

    @Test
    fun encodesRfcVectors() {
        vectors.forEach { (plain, encoded) ->
            assertEquals("encode(\"$plain\")", encoded, Base32.encode(plain.encodeToByteArray()))
        }
    }

    @Test
    fun decodesRfcVectors() {
        vectors.forEach { (plain, encoded) ->
            assertEquals("decode(\"$encoded\")", plain, Base32.decode(encoded)?.decodeToString())
        }
    }

    @Test
    fun decodeAcceptsLowercaseSpacesHyphensAndMissingPadding() {
        val expected = "foobar".encodeToByteArray()
        listOf("mzxw6ytboi", "MZXW 6YTB OI", "mzxw-6ytb-oi==", " MzXw6YtBoI====== ").forEach { text ->
            val decoded = Base32.decode(text)
            println("decode(\"$text\") -> ${decoded?.decodeToString()}")
            assertArrayEquals(text, expected, decoded)
        }
    }

    @Test
    fun decodeTreatsTabsAndLineBreaksLikeSpaces() {
        val expected = "foobar".encodeToByteArray()
        listOf("MZXW\t6YTB\tOI", "MZXW\n6YTB\r\nOI\n", "\tmzxw 6ytb\u00A0oi").forEach { text ->
            val decoded = Base32.decode(text)
            println("decode(${text.map { it.code }}) -> ${decoded?.decodeToString()}")
            assertArrayEquals(text, expected, decoded)
        }
    }

    @Test
    fun decodesTheCommonExampleKey() {
        val expected = "Hello!".encodeToByteArray() + byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        assertArrayEquals(expected, Base32.decode("JBSWY3DPEHPK3PXP"))
    }

    @Test
    fun decodeRejectsCharactersOutsideTheAlphabet() {
        listOf("ABC1", "ABC0", "ABC8", "ABC9", "ABC!", "MZ=XW", "MZXW_6", "MZXW.6").forEach { text ->
            assertNull("decode(\"$text\")", Base32.decode(text))
        }
    }

    @Test
    fun encodeThenDecodeRoundTripsEveryByteValue() {
        val bytes = ByteArray(256) { it.toByte() }
        assertArrayEquals(bytes, Base32.decode(Base32.encode(bytes)))
    }
}

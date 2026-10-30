package com.example.passwordmanager.util.totp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class ProtobufReaderTest {

    @Test
    fun readsVarintsOfEveryLength() {
        listOf(
            byteArrayOf(0x00) to 0L,
            byteArrayOf(0x01) to 1L,
            byteArrayOf(0xAC.toByte(), 0x02) to 300L,
            byteArrayOf(0xC0.toByte(), 0xC4.toByte(), 0x07) to 123456L,
            ByteArray(9) { 0xFF.toByte() } + byteArrayOf(0x01) to -1L
        ).forEach { (bytes, expected) ->
            val value = ProtobufReader(bytes).readVarint()
            println("varint ${bytes.toHex()} -> $value")
            assertEquals(bytes.toHex(), expected, value)
        }
    }

    @Test
    fun varintLongerThanTenBytesIsRejected() {
        val bytes = ByteArray(11) { 0x80.toByte() }
        assertThrows(ProtobufException::class.java) { ProtobufReader(bytes).readVarint() }
    }

    @Test
    fun readsTagsAndLengthDelimitedFields() {
        // Field 2, wire type 2, length 3, "bob".
        val reader = ProtobufReader(byteArrayOf(0x12, 0x03) + "bob".encodeToByteArray())
        assertEquals(2 to WIRE_LENGTH_DELIMITED, reader.readTag())
        assertArrayEquals("bob".encodeToByteArray(), reader.readBytes())
        assertFalse(reader.hasMore)
    }

    @Test
    fun skipsEveryScalarWireType() {
        val bytes = byteArrayOf(0x08, 0x96.toByte(), 0x01) + // field 1 varint
            byteArrayOf(0x11) + ByteArray(8) + // field 2 fixed64
            byteArrayOf(0x1A, 0x02, 0x41, 0x42) + // field 3 length-delimited
            byteArrayOf(0x25) + ByteArray(4) + // field 4 fixed32
            byteArrayOf(0x28, 0x07) // field 5 varint, read
        val reader = ProtobufReader(bytes)
        repeat(4) {
            val (field, wireType) = reader.readTag()
            println("skip field $field, wire type $wireType")
            reader.skip(wireType)
        }
        assertEquals(5 to WIRE_VARINT, reader.readTag())
        assertEquals(7L, reader.readVarint())
        assertFalse(reader.hasMore)
    }

    @Test
    fun truncatedOrInvalidInputThrows() {
        val cases = listOf<Pair<String, (ProtobufReader) -> Unit>>(
            "empty varint" to { it.readVarint() },
            "unterminated varint" to { it.readVarint() },
            "length past the end" to { it.readBytes() },
            "huge length" to { it.readBytes() },
            "truncated fixed64" to { it.skip(WIRE_FIXED64) },
            "truncated fixed32" to { it.skip(WIRE_FIXED32) },
            "group wire type" to { it.skip(3) },
            "field number 0" to { it.readTag() }
        )
        val inputs = listOf(
            byteArrayOf(),
            byteArrayOf(0x80.toByte()),
            byteArrayOf(0x05, 0x41),
            byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x0F),
            ByteArray(7),
            ByteArray(3),
            byteArrayOf(),
            byteArrayOf(0x02)
        )
        cases.zip(inputs).forEach { (case, input) ->
            println("expect ProtobufException: ${case.first}")
            assertThrows(case.first, ProtobufException::class.java) { case.second(ProtobufReader(input)) }
        }
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}

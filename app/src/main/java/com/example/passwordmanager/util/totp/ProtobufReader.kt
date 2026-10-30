package com.example.passwordmanager.util.totp

internal const val WIRE_VARINT = 0
internal const val WIRE_FIXED64 = 1
internal const val WIRE_LENGTH_DELIMITED = 2
internal const val WIRE_FIXED32 = 5

private const val MAX_VARINT_BYTES = 10
private const val FIXED64_BYTES = 8
private const val FIXED32_BYTES = 4

/** Thrown for input that is not well-formed protobuf: truncated, oversized lengths or unknown wire types. */
internal class ProtobufException(message: String) : Exception(message)

/** Just enough of the protobuf wire format to read Google's migration payload. Every read is bounds-checked. */
internal class ProtobufReader(private val bytes: ByteArray) {

    private var position = 0

    val hasMore: Boolean get() = position < bytes.size

    /** Returns `(field number, wire type)`. */
    fun readTag(): Pair<Int, Int> {
        val tag = readVarint()
        val field = tag ushr 3
        if (field < 1 || field > Int.MAX_VALUE) throw ProtobufException("Invalid field number")
        return field.toInt() to (tag and 0x7).toInt()
    }

    fun readVarint(): Long {
        var result = 0L
        for (index in 0 until MAX_VARINT_BYTES) {
            val byte = readByte()
            result = result or ((byte and 0x7f).toLong() shl (7 * index))
            if (byte and 0x80 == 0) return result
        }
        throw ProtobufException("Varint longer than $MAX_VARINT_BYTES bytes")
    }

    fun readBytes(): ByteArray {
        val length = readVarint()
        if (length < 0 || length > bytes.size - position) throw ProtobufException("Length past the end of the input")
        val start = position
        position += length.toInt()
        return bytes.copyOfRange(start, position)
    }

    fun skip(wireType: Int) {
        when (wireType) {
            WIRE_VARINT -> readVarint()
            WIRE_FIXED64 -> advance(FIXED64_BYTES)
            WIRE_LENGTH_DELIMITED -> readBytes().fill(0)
            WIRE_FIXED32 -> advance(FIXED32_BYTES)
            else -> throw ProtobufException("Unsupported wire type $wireType")
        }
    }

    private fun readByte(): Int {
        if (position >= bytes.size) throw ProtobufException("Unexpected end of input")
        return bytes[position++].toInt() and 0xff
    }

    private fun advance(count: Int) {
        if (count > bytes.size - position) throw ProtobufException("Unexpected end of input")
        position += count
    }
}

package com.example.passwordmanager.util.totp

/** RFC 4648 Base32, as used for authenticator setup keys. */
object Base32 {

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private const val PADDING = '='

    /** Encodes with `=` padding to a multiple of 8 characters. */
    fun encode(bytes: ByteArray): String {
        val output = StringBuilder((bytes.size + 4) / 5 * 8)
        var buffer = 0
        var bits = 0
        for (byte in bytes) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xff)
            bits += 8
            while (bits >= 5) {
                output.append(ALPHABET[(buffer ushr (bits - 5)) and 0x1f])
                bits -= 5
            }
        }
        if (bits > 0) output.append(ALPHABET[(buffer shl (5 - bits)) and 0x1f])
        while (output.length % 8 != 0) output.append(PADDING)
        return output.toString()
    }

    /**
     * Decodes ignoring case, whitespace (spaces, tabs, line breaks) and hyphens, with or without padding. Returns null for any other
     * character. Bits left over after the last full byte are dropped, as authenticator apps do.
     */
    fun decode(text: String): ByteArray? {
        val normalized = text.filterNot { it.isWhitespace() || it == '-' }.uppercase().trimEnd(PADDING)
        val output = ByteArray(normalized.length * 5 / 8)
        var buffer = 0
        var bits = 0
        var size = 0
        for (char in normalized) {
            val value = ALPHABET.indexOf(char)
            if (value < 0) return null
            buffer = (buffer shl 5) or value
            bits += 5
            if (bits >= 8) {
                output[size++] = (buffer ushr (bits - 8)).toByte()
                bits -= 8
            }
        }
        return output
    }
}

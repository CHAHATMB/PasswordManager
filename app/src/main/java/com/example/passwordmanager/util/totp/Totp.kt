package com.example.passwordmanager.util.totp

import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

enum class TotpAlgorithm(val macName: String) {
    SHA1("HmacSHA1"),
    SHA256("HmacSHA256"),
    SHA512("HmacSHA512");

    companion object {
        /** Case-insensitive; null for unsupported names such as MD5. */
        fun fromName(name: String): TotpAlgorithm? = entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}

val DEFAULT_TOTP_ALGORITHM = TotpAlgorithm.SHA1
const val DEFAULT_TOTP_DIGITS = 6
const val DEFAULT_TOTP_PERIOD = 30
val TOTP_DIGITS = listOf(6, 8)
val TOTP_PERIOD_RANGE = 1..300
val TOTP_SECRET_BYTES_RANGE = 1..128

/** RFC 4226 HOTP and RFC 6238 TOTP (T0 = 0). */
object Totp {

    fun generate(secret: ByteArray, unixSeconds: Long, algorithm: TotpAlgorithm, digits: Int, period: Int): String =
        hotp(secret, Math.floorDiv(unixSeconds, period.toLong()), algorithm, digits)

    /** From [period] right at a boundary down to 1 just before the next one. */
    fun secondsRemaining(unixSeconds: Long, period: Int): Int = period - Math.floorMod(unixSeconds, period.toLong()).toInt()

    fun hotp(secret: ByteArray, counter: Long, algorithm: TotpAlgorithm, digits: Int): String {
        val mac = Mac.getInstance(algorithm.macName)
        mac.init(SecretKeySpec(secret, algorithm.macName))
        val hash = mac.doFinal(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(counter).array())
        // Dynamic truncation, RFC 4226 section 5.3.
        val offset = hash.last().toInt() and 0x0f
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)
        var modulus = 1
        repeat(digits) { modulus *= 10 }
        return (binary % modulus).toString().padStart(digits, '0')
    }
}

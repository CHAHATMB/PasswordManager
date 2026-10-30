package com.example.passwordmanager.util.totp

import org.junit.Assert.assertEquals
import org.junit.Test

class TotpTest {

    private val sha1Key = "12345678901234567890".encodeToByteArray()
    private val sha256Key = "12345678901234567890123456789012".encodeToByteArray()
    private val sha512Key = "1234567890123456789012345678901234567890123456789012345678901234".encodeToByteArray()

    @Test
    fun hotpMatchesRfc4226AppendixD() {
        val expected = listOf("755224", "287082", "359152", "969429", "338314", "254676", "287922", "162583", "399871", "520489")
        expected.forEachIndexed { counter, code ->
            assertEquals("counter $counter", code, Totp.hotp(sha1Key, counter.toLong(), TotpAlgorithm.SHA1, 6))
        }
    }

    @Test
    fun totpMatchesRfc6238AppendixB() {
        // time to (SHA1, SHA256, SHA512), 8 digits, period 30.
        val vectors = listOf(
            59L to Triple("94287082", "46119246", "90693936"),
            1111111109L to Triple("07081804", "68084774", "25091201"),
            1111111111L to Triple("14050471", "67062674", "99943326"),
            1234567890L to Triple("89005924", "91819424", "93441116"),
            2000000000L to Triple("69279037", "90698825", "38618901"),
            20000000000L to Triple("65353130", "77737706", "47863826")
        )
        vectors.forEach { (time, codes) ->
            val actual = Triple(
                Totp.generate(sha1Key, time, TotpAlgorithm.SHA1, 8, 30),
                Totp.generate(sha256Key, time, TotpAlgorithm.SHA256, 8, 30),
                Totp.generate(sha512Key, time, TotpAlgorithm.SHA512, 8, 30)
            )
            println("t=$time -> $actual")
            assertEquals("t=$time", codes, actual)
        }
    }

    @Test
    fun base32ExampleKeyAt59SecondsIs996554() {
        val secret = checkNotNull(Base32.decode("JBSWY3DPEHPK3PXP"))
        assertEquals("996554", Totp.generate(secret, 59, TotpAlgorithm.SHA1, 6, 30))
    }

    @Test
    fun codeChangesOnlyAtPeriodBoundaries() {
        val code = { time: Long -> Totp.generate(sha1Key, time, TotpAlgorithm.SHA1, 6, 30) }
        assertEquals(code(30), code(59))
        assertEquals(Totp.hotp(sha1Key, 2, TotpAlgorithm.SHA1, 6), code(60))
    }

    @Test
    fun secondsRemainingAtBoundaries() {
        assertEquals(30, Totp.secondsRemaining(0, 30))
        assertEquals(1, Totp.secondsRemaining(29, 30))
        assertEquals(30, Totp.secondsRemaining(30, 30))
        assertEquals(1, Totp.secondsRemaining(59, 60))
        assertEquals(60, Totp.secondsRemaining(120, 60))
    }

    @Test
    fun algorithmNamesAreCaseInsensitiveAndMd5IsRejected() {
        assertEquals(TotpAlgorithm.SHA256, TotpAlgorithm.fromName("sha256"))
        assertEquals(TotpAlgorithm.SHA512, TotpAlgorithm.fromName("Sha512"))
        assertEquals(null, TotpAlgorithm.fromName("MD5"))
    }
}

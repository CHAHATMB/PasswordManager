package com.example.passwordmanager.util

import java.security.SecureRandom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordStrengthTest {

    @Test
    fun emptyPasswordIsWeak() {
        assertEquals(PasswordStrength.Weak, PasswordStrength.of(""))
    }

    @Test
    fun commonLowercaseWordIsWeak() {
        assertEquals(PasswordStrength.Weak, PasswordStrength.of("password"))
    }

    @Test
    fun shortRangeRepeatsAddNothing() {
        assertEquals(PasswordStrength.Weak, PasswordStrength.of("aaaaaaaa"))
        assertEquals(PasswordStrength.Weak, PasswordStrength.of("aaaaaaaaaaaa"))
        assertEquals(PasswordStrength.Weak, PasswordStrength.of("abababababab"))
    }

    @Test
    fun sequencesAddNothing() {
        assertEquals("ascending", PasswordStrength.Weak, PasswordStrength.of("abcdefgh"))
        assertEquals("ascending", PasswordStrength.Weak, PasswordStrength.of("abcdefghijkl"))
        assertEquals("descending", PasswordStrength.Weak, PasswordStrength.of("987654321098"))
    }

    @Test
    fun twoCharacterStepsAreNotPenalised() {
        // Contains the steps 12, 54 and 76 but no run of three.
        assertNotEquals(PasswordStrength.Weak, PasswordStrength.of("58120374961548302769"))
    }

    @Test
    fun longRandomDigitsAreNotWeak() {
        val digits = "2491477505289385805377015107564782692923256729024604752301486909"
        assertNotEquals(PasswordStrength.Weak, PasswordStrength.of(digits))
    }

    @Test
    fun randomTwentyLowercaseIsGoodOrStrong() {
        for (password in listOf("woqjrfqijrhtohkhbkva", "xxbhnxufdjulsbimrukp", "udigsgbaikiisdubsltn")) {
            val strength = PasswordStrength.of(password)
            assertTrue("$password scored $strength", strength == PasswordStrength.Good || strength == PasswordStrength.Strong)
        }
    }

    @Test
    fun tenLowercaseAndDigitsAreFair() {
        assertEquals(PasswordStrength.Fair, PasswordStrength.of("qmzv7xk2wt"))
    }

    @Test
    fun mixedTwelveCharacterPasswordIsGood() {
        assertEquals(PasswordStrength.Good, PasswordStrength.of("Kx9#mP2\$vL7q"))
    }

    @Test
    fun generatedTwentyCharacterPasswordIsStrong() {
        val generator = PasswordGenerator(SecureRandom.getInstance("SHA1PRNG").apply { setSeed(42L) })
        repeat(100) {
            val password = generator.generate(GeneratorOptions())
            assertEquals("generated #$it", PasswordStrength.Strong, PasswordStrength.of(password))
        }
    }

    @Test
    fun fromBitsUsesTheFortySixtyEightyBands() {
        val cases = listOf(
            0.0 to PasswordStrength.Weak,
            39.99 to PasswordStrength.Weak,
            40.0 to PasswordStrength.Fair,
            59.99 to PasswordStrength.Fair,
            60.0 to PasswordStrength.Good,
            79.99 to PasswordStrength.Good,
            80.0 to PasswordStrength.Strong
        )
        for ((bits, expected) in cases) {
            assertEquals("$bits bits", expected, PasswordStrength.fromBits(bits))
        }
    }
}

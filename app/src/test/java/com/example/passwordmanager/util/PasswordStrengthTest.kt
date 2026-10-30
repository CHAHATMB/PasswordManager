package com.example.passwordmanager.util

import java.security.SecureRandom
import org.junit.Assert.assertEquals
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
    fun repeatedCharactersAddNothing() {
        assertEquals(PasswordStrength.Weak, PasswordStrength.of("aaaaaaaaaaaa"))
    }

    @Test
    fun sequencesAddNothing() {
        assertEquals("ascending", PasswordStrength.Weak, PasswordStrength.of("abcdefghijkl"))
        assertEquals("descending", PasswordStrength.Weak, PasswordStrength.of("987654321098"))
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
}

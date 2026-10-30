package com.example.passwordmanager.util

import java.security.SecureRandom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordGeneratorTest {

    private val generator = PasswordGenerator(SecureRandom.getInstance("SHA1PRNG").apply { setSeed(SEED) })

    @Test
    fun outputHasTheRequestedLength() {
        for (length in listOf(MIN_GENERATED_LENGTH, DEFAULT_GENERATED_LENGTH, MAX_GENERATED_LENGTH)) {
            assertEquals("length $length", length, generator.generate(GeneratorOptions(length = length)).length)
        }
    }

    @Test
    fun everySelectedClassAppearsEvenAtMinimumLength() {
        repeat(ITERATIONS) {
            val password = generator.generate(GeneratorOptions(length = MIN_GENERATED_LENGTH))
            for (characterClass in CharacterClass.entries) {
                assertTrue(
                    "$characterClass missing in a ${password.length}-character password",
                    password.any { it in characterClass.characters }
                )
            }
        }
    }

    @Test
    fun unselectedClassesNeverAppear() {
        val options = GeneratorOptions(classes = setOf(CharacterClass.Uppercase, CharacterClass.Lowercase))
        val allowed = CharacterClass.Uppercase.characters + CharacterClass.Lowercase.characters
        repeat(ITERATIONS) {
            val unexpected = generator.generate(options).filterNot { it in allowed }
            assertEquals("unexpected characters: '$unexpected'", "", unexpected)
        }
    }

    @Test
    fun avoidAmbiguousExcludesAmbiguousCharactersAndKeepsEveryClass() {
        val options = GeneratorOptions(length = MAX_GENERATED_LENGTH, avoidAmbiguous = true)
        repeat(ITERATIONS) {
            val password = generator.generate(options)
            val ambiguous = password.filter { it in AMBIGUOUS_CHARACTERS }
            assertEquals("ambiguous characters: '$ambiguous'", "", ambiguous)
            for (characterClass in CharacterClass.entries) {
                assertTrue("$characterClass missing", password.any { it in characterClass.characters })
            }
        }
    }

    @Test
    fun consecutivePasswordsDiffer() {
        assertNotEquals(generator.generate(GeneratorOptions()), generator.generate(GeneratorOptions()))
    }

    @Test(expected = GeneratorOptionsException.NoCharacterClass::class)
    fun noSelectedClassIsRejected() {
        generator.generate(GeneratorOptions(classes = emptySet()))
    }

    @Test
    fun lengthOutsideTheRangeIsRejected() {
        for (length in listOf(MIN_GENERATED_LENGTH - 1, MAX_GENERATED_LENGTH + 1)) {
            try {
                generator.generate(GeneratorOptions(length = length))
                throw AssertionError("length $length was accepted")
            } catch (expected: GeneratorOptionsException.LengthOutOfRange) {
                println("length $length rejected: ${expected.message}")
            }
        }
    }

    private companion object {
        const val SEED = 42L
        const val ITERATIONS = 1_000
    }
}

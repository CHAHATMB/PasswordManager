package com.example.passwordmanager.util

import java.security.SecureRandom
import kotlin.math.log2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PassphraseGeneratorTest {

    private val words = ('a'..'p').map { "$it$it$it" }
    private val generator = PassphraseGenerator(SecureRandom.getInstance("SHA1PRNG").apply { setSeed(SEED) }, words)

    @Test
    fun outputHasTheRequestedWordCount() {
        for (count in listOf(MIN_PASSPHRASE_WORDS, DEFAULT_PASSPHRASE_WORDS, MAX_PASSPHRASE_WORDS)) {
            val phrase = generator.generate(PassphraseOptions(wordCount = count))
            assertEquals(phrase, count, phrase.split(Separator.Hyphen.value).size)
        }
    }

    @Test
    fun wordCountOutsideTheRangeIsRejected() {
        for (count in listOf(MIN_PASSPHRASE_WORDS - 1, MAX_PASSPHRASE_WORDS + 1)) {
            assertThrows("count $count", GeneratorOptionsException.WordCountOutOfRange::class.java) {
                generator.generate(PassphraseOptions(wordCount = count))
            }
        }
    }

    @Test
    fun wordsAreDrawnUniformly() {
        val counts = words.associateWith { 0 }.toMutableMap()
        repeat(UNIFORMITY_DRAWS / MIN_PASSPHRASE_WORDS) {
            generator.generate(PassphraseOptions(wordCount = MIN_PASSPHRASE_WORDS)).split("-").forEach {
                counts[it] = counts.getValue(it) + 1
            }
        }
        val expected = UNIFORMITY_DRAWS.toDouble() / words.size
        val chiSquare = counts.values.sumOf { (it - expected) * (it - expected) / expected }
        assertTrue("missing words in $counts", counts.values.all { it > 0 })
        assertTrue("chi-square $chiSquare over $CHI_SQUARE_LIMIT, counts $counts", chiSquare < CHI_SQUARE_LIMIT)
    }

    @Test
    fun eachDrawSelectsThatIndex() {
        val draws = listOf(3, 0, 15, 7)
        val phrase = PassphraseGenerator(SequenceRandom(draws), words).generate(PassphraseOptions(wordCount = draws.size))
        assertEquals(draws.map { words[it] }, phrase.split("-"))
    }

    @Test
    fun separatorAppearsOnlyBetweenWords() {
        for (separator in Separator.entries) {
            val options = PassphraseOptions(wordCount = MAX_PASSPHRASE_WORDS, separator = separator)
            repeat(ITERATIONS) {
                val phrase = generator.generate(options)
                val occurrences = phrase.count { it == separator.value.single() }
                assertEquals("'$phrase' with $separator", MAX_PASSPHRASE_WORDS - 1, occurrences)
                assertTrue("'$phrase' with $separator", phrase.split(separator.value).all { it in words })
            }
        }
    }

    @Test
    fun capitaliseUppercasesOnlyTheFirstLetterOfEveryWord() {
        repeat(ITERATIONS) {
            val phrase = generator.generate(PassphraseOptions(capitalise = true))
            phrase.split("-").forEach { word ->
                assertTrue("'$word' in '$phrase'", word.first().isUpperCase())
                assertTrue("'$word' in '$phrase'", word.replaceFirstChar { it.lowercaseChar() } in words)
            }
        }
    }

    @Test
    fun withoutCapitaliseEverythingIsLowercase() {
        repeat(ITERATIONS) {
            val phrase = generator.generate(PassphraseOptions())
            assertEquals(phrase.lowercase(), phrase)
        }
    }

    @Test
    fun addDigitPutsOneDigitAfterOneWordAtEveryPosition() {
        val digitsSeen = mutableSetOf<Char>()
        val positionsSeen = mutableSetOf<Int>()
        repeat(ITERATIONS) {
            val phrase = generator.generate(PassphraseOptions(addDigit = true))
            assertEquals("'$phrase'", 1, phrase.count { it.isDigit() })
            val parts = phrase.split("-")
            val position = parts.indexOfFirst { it.last().isDigit() }
            assertTrue("digit not at a word end in '$phrase'", position >= 0)
            assertTrue("'$phrase'", parts[position].dropLast(1) in words)
            digitsSeen += parts[position].last()
            positionsSeen += position
        }
        assertEquals(('0'..'9').toSet(), digitsSeen)
        assertEquals((0 until DEFAULT_PASSPHRASE_WORDS).toSet(), positionsSeen)
    }

    @Test
    fun withoutAddDigitThereAreNoDigits() {
        repeat(ITERATIONS) {
            val phrase = generator.generate(PassphraseOptions())
            assertTrue("'$phrase'", phrase.none { it.isDigit() })
        }
    }

    @Test
    fun consecutivePhrasesDiffer() {
        assertNotEquals(generator.generate(PassphraseOptions()), generator.generate(PassphraseOptions()))
    }

    @Test
    fun entropyIsWordCountTimesBitsPerWord() {
        assertEquals(77.55, PassphraseOptions(wordCount = 6).entropyBits(EFF_WORDLIST_SIZE), 0.01)
        assertEquals(51.70, PassphraseOptions(wordCount = 4).entropyBits(EFF_WORDLIST_SIZE), 0.01)
    }

    @Test
    fun digitAddsItsValueAndPositionBits() {
        val withDigit = PassphraseOptions(wordCount = 6, addDigit = true).entropyBits(EFF_WORDLIST_SIZE)
        assertEquals(6 * log2(EFF_WORDLIST_SIZE.toDouble()) + log2(10.0) + log2(6.0), withDigit, 1e-9)
    }

    @Test
    fun capitaliseAndSeparatorAddNoBits() {
        val plain = PassphraseOptions().entropyBits(EFF_WORDLIST_SIZE)
        for (separator in Separator.entries) {
            val styled = PassphraseOptions(separator = separator, capitalise = true).entropyBits(EFF_WORDLIST_SIZE)
            assertEquals("$separator", plain, styled, 0.0)
        }
    }

    @Test
    fun passphraseLevelsMatchTheWordCounts() {
        assertEquals(PasswordStrength.Fair, PasswordStrength.fromBits(PassphraseOptions(wordCount = 4).entropyBits(EFF_WORDLIST_SIZE)))
        assertEquals(PasswordStrength.Good, PasswordStrength.fromBits(PassphraseOptions(wordCount = 6).entropyBits(EFF_WORDLIST_SIZE)))
        assertEquals(PasswordStrength.Strong, PasswordStrength.fromBits(PassphraseOptions(wordCount = 7).entropyBits(EFF_WORDLIST_SIZE)))
    }

    /** Returns [draws] in order from nextInt(bound), so a test can pin each pick. */
    private class SequenceRandom(draws: List<Int>) : SecureRandom() {
        private val remaining = ArrayDeque(draws)
        override fun nextInt(bound: Int): Int = remaining.removeFirst().also { require(it in 0 until bound) }
    }

    private companion object {
        const val SEED = 42L
        const val ITERATIONS = 1_000
        const val UNIFORMITY_DRAWS = 160_000
        // 99.9th percentile of chi-square with 15 degrees of freedom (16 words).
        const val CHI_SQUARE_LIMIT = 37.70
    }
}

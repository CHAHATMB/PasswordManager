package com.example.passwordmanager.util

import java.security.SecureRandom
import kotlin.math.log2

const val MIN_PASSPHRASE_WORDS = 4
const val MAX_PASSPHRASE_WORDS = 10
const val DEFAULT_PASSPHRASE_WORDS = 6

private const val DIGIT_COUNT = 10

enum class Separator(val value: String) {
    Hyphen("-"),
    Space(" "),
    Period("."),
    Underscore("_")
}

data class PassphraseOptions(
    val wordCount: Int = DEFAULT_PASSPHRASE_WORDS,
    val separator: Separator = Separator.Hyphen,
    val capitalise: Boolean = false,
    val addDigit: Boolean = false
)

/**
 * Bits for a uniform pick of [PassphraseOptions.wordCount] words from a list of [listSize], plus a
 * random digit after a random word. Capitalisation and the separator are the user's choice, not
 * random, so they add nothing.
 */
fun PassphraseOptions.entropyBits(listSize: Int): Double {
    val wordBits = wordCount * log2(listSize.toDouble())
    return if (addDigit) wordBits + log2(DIGIT_COUNT.toDouble()) + log2(wordCount.toDouble()) else wordBits
}

class PassphraseGenerator(private val random: SecureRandom, private val words: List<String>) {

    // Never filter or re-draw by content: that would shrink the space below entropyBits.
    fun generate(options: PassphraseOptions): String {
        if (options.wordCount !in MIN_PASSPHRASE_WORDS..MAX_PASSPHRASE_WORDS) {
            throw GeneratorOptionsException.WordCountOutOfRange(options.wordCount)
        }
        val picked = MutableList(options.wordCount) { words[random.nextInt(words.size)] }
        if (options.capitalise) picked.replaceAll { word -> word.replaceFirstChar { it.uppercaseChar() } }
        if (options.addDigit) {
            val position = random.nextInt(picked.size)
            picked[position] = picked[position] + random.nextInt(DIGIT_COUNT)
        }
        return picked.joinToString(options.separator.value)
    }
}

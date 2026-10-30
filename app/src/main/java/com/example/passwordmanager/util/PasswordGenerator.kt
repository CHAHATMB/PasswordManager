package com.example.passwordmanager.util

import java.security.SecureRandom

const val MIN_GENERATED_LENGTH = 8
const val MAX_GENERATED_LENGTH = 64
const val DEFAULT_GENERATED_LENGTH = 20

const val AMBIGUOUS_CHARACTERS = "0Ool1I|"

enum class CharacterClass(val characters: String) {
    Uppercase("ABCDEFGHIJKLMNOPQRSTUVWXYZ"),
    Lowercase("abcdefghijklmnopqrstuvwxyz"),
    Digits("0123456789"),
    Symbols("!@#$%^&*()-_=+[]{};:,.?/|~")
}

data class GeneratorOptions(
    val length: Int = DEFAULT_GENERATED_LENGTH,
    val classes: Set<CharacterClass> = CharacterClass.entries.toSet(),
    val avoidAmbiguous: Boolean = false
)

sealed class GeneratorOptionsException(message: String) : IllegalArgumentException(message) {
    class NoCharacterClass : GeneratorOptionsException("Select at least one character class")
    class LengthOutOfRange(length: Int) :
        GeneratorOptionsException("Length $length is outside $MIN_GENERATED_LENGTH..$MAX_GENERATED_LENGTH")
    class WordCountOutOfRange(wordCount: Int) :
        GeneratorOptionsException("Word count $wordCount is outside $MIN_PASSPHRASE_WORDS..$MAX_PASSPHRASE_WORDS")
}

class PasswordGenerator(private val random: SecureRandom) {

    fun generate(options: GeneratorOptions): String {
        if (options.classes.isEmpty()) throw GeneratorOptionsException.NoCharacterClass()
        if (options.length !in MIN_GENERATED_LENGTH..MAX_GENERATED_LENGTH) {
            throw GeneratorOptionsException.LengthOutOfRange(options.length)
        }
        val pools = options.classes.sorted().map { characterClass ->
            if (options.avoidAmbiguous) characterClass.characters.filterNot { it in AMBIGUOUS_CHARACTERS }
            else characterClass.characters
        }
        val allCharacters = pools.joinToString("")
        // One character from each pool first, so every selected class appears; the shuffle then
        // hides which positions those are.
        val password = CharArray(options.length) { index ->
            pick(pools.getOrElse(index) { allCharacters })
        }
        shuffle(password)
        return String(password)
    }

    // SecureRandom.nextInt(bound) rejects out-of-range draws, so picks have no modulo bias.
    private fun pick(characters: String): Char = characters[random.nextInt(characters.length)]

    private fun shuffle(characters: CharArray) {
        for (index in characters.lastIndex downTo 1) {
            val other = random.nextInt(index + 1)
            val swapped = characters[index]
            characters[index] = characters[other]
            characters[other] = swapped
        }
    }
}

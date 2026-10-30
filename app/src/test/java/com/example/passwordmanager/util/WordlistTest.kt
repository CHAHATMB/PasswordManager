package com.example.passwordmanager.util

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WordlistTest {

    @Test
    fun bundledEffListHasUniqueLowercaseWords() {
        // Gradle runs unit tests from the module directory.
        val words = File("src/main/res/raw/eff_large_wordlist.txt").inputStream().use(::parseWordlist)

        assertEquals(EFF_WORDLIST_SIZE, words.size)
        assertEquals(words.size, words.toSet().size)
        val separators = Separator.entries.map { it.value.single() }
        val invalid = words.filter { word ->
            word.isBlank() || word != word.lowercase() || word.any { it.isWhitespace() || it in separators && it != '-' }
        }
        assertTrue("invalid words: $invalid", invalid.isEmpty())
        // The unmodified EFF list has these four hyphenated words; a Hyphen phrase can show an extra '-'.
        assertEquals(setOf("drop-down", "felt-tip", "t-shirt", "yo-yo"), words.filter { '-' in it }.toSet())
    }

    @Test
    fun wrongWordCountIsRejected() {
        assertThrows(WordlistException::class.java) { parse(syntheticLines(EFF_WORDLIST_SIZE - 1)) }
        assertThrows(WordlistException::class.java) { parse(syntheticLines(EFF_WORDLIST_SIZE + 1)) }
    }

    @Test
    fun blankLinesAreSkipped() {
        assertEquals(EFF_WORDLIST_SIZE, parse(syntheticLines(EFF_WORDLIST_SIZE) + listOf("", "  ")).size)
    }

    @Test
    fun duplicateWordIsRejected() {
        val lines = syntheticLines(EFF_WORDLIST_SIZE - 1) + "99999\tw0"
        val error = assertThrows(WordlistException::class.java) { parse(lines) }
        assertTrue(error.message, error.message!!.contains("Duplicate"))
    }

    @Test
    fun blankWordIsRejected() {
        for (badLine in listOf("99999\t", "99999\t  ", "99999")) {
            val lines = syntheticLines(EFF_WORDLIST_SIZE - 1) + badLine
            val error = assertThrows("line '$badLine'", WordlistException::class.java) { parse(lines) }
            assertTrue(error.message, error.message!!.contains("Blank"))
        }
    }

    private fun syntheticLines(count: Int): List<String> = List(count) { "$it\tw$it" }

    private fun parse(lines: List<String>): List<String> =
        lines.joinToString("\n").byteInputStream().use(::parseWordlist)
}

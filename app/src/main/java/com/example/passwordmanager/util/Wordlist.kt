package com.example.passwordmanager.util

import java.io.IOException
import java.io.InputStream

const val EFF_WORDLIST_SIZE = 7776

class WordlistException(message: String) : IOException(message)

/** Reads an EFF Diceware list: one `<dice digits>\t<word>` entry per line. */
fun parseWordlist(input: InputStream): List<String> {
    val seen = HashSet<String>()
    val words = input.bufferedReader().readLines()
        .filter { it.isNotBlank() }
        .map { line ->
            val word = line.split('\t').getOrNull(1).orEmpty()
            if (word.isBlank()) throw WordlistException("Blank word in line '$line'")
            if (!seen.add(word)) throw WordlistException("Duplicate word '$word'")
            word
        }
    if (words.size != EFF_WORDLIST_SIZE) {
        throw WordlistException("Expected $EFF_WORDLIST_SIZE words, found ${words.size}")
    }
    return words
}

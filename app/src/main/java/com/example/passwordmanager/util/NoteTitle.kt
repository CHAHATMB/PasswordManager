package com.example.passwordmanager.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Longest body text taken into a derived title, in code points, including the ellipsis. */
const val NOTE_TITLE_TEXT_MAX_CHARS = 24
const val NOTE_TITLE_MAX_CHARS = 200
const val NOTE_BODY_MAX_CHARS = 100_000
private const val NOTE_TITLE_FALLBACK = "Note"
private const val NOTE_TITLE_SEPARATOR = " · "
private const val ELLIPSIS = "…"
private const val NOTE_TITLE_DATE_PATTERN = "d MMM yyyy"
// \p{Z} adds no-break and other Unicode spaces, matching Kotlin's isWhitespace used by trim().
private val WHITESPACE = Regex("[\\s\\p{Z}]+")

/**
 * The first non-blank line of [body] with whitespace collapsed, cut to [NOTE_TITLE_TEXT_MAX_CHARS] with an
 * ellipsis, then the date: `Wifi router admin · 30 Oct 2026`. A blank body gives `Note · 30 Oct 2026`.
 */
fun deriveNoteTitle(body: String, date: LocalDate, locale: Locale): String {
    val firstLine = body.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.replace(WHITESPACE, " ")
    val text = firstLine?.let { shorten(it) } ?: NOTE_TITLE_FALLBACK
    return text + NOTE_TITLE_SEPARATOR + date.format(DateTimeFormatter.ofPattern(NOTE_TITLE_DATE_PATTERN, locale))
}

/** [text] cut to at most [maxChars] UTF-16 units without splitting a surrogate pair. */
fun limitLength(text: String, maxChars: Int): String {
    if (text.length <= maxChars) return text
    val end = if (Character.isHighSurrogate(text[maxChars - 1])) maxChars - 1 else maxChars
    return text.substring(0, end)
}

// Counts code points, so an emoji is one character and is never split.
private fun shorten(text: String): String {
    if (text.codePointCount(0, text.length) <= NOTE_TITLE_TEXT_MAX_CHARS) return text
    val end = text.offsetByCodePoints(0, NOTE_TITLE_TEXT_MAX_CHARS - ELLIPSIS.length)
    return text.substring(0, end).trimEnd() + ELLIPSIS
}

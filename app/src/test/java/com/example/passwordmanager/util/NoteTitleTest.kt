package com.example.passwordmanager.util

import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteTitleTest {

    private val date = LocalDate.of(2026, 10, 30)

    private fun derive(body: String) = deriveNoteTitle(body, date, Locale.US).also { println("${body.take(40).debug()} -> $it") }

    @Test
    fun firstLineAndDateFormTheTitle() {
        assertEquals("Wifi router admin · 30 Oct 2026", derive("Wifi router admin\npassword is on the box"))
    }

    @Test
    fun emptyOrBlankBodyGivesTheFallback() {
        listOf("", "   ", "\n\n\t \r\n").forEach { assertEquals("Note · 30 Oct 2026", derive(it)) }
    }

    @Test
    fun leadingBlankLinesAreSkipped() {
        assertEquals("Bank · 30 Oct 2026", derive("\n   \n\t\nBank\nsecond"))
        assertEquals("Windows line · 30 Oct 2026", derive("\r\n\r\nWindows line\r\nnext"))
    }

    @Test
    fun whitespaceInsideTheLineIsCollapsedAndTrimmed() {
        assertEquals("a b c · 30 Oct 2026", derive("  a \t  b\u00A0 c  \nrest"))
    }

    @Test
    fun exactly24CharactersAreKeptAnd25AreCutWithAnEllipsis() {
        val exactly24 = "abcdefghijklmnopqrstuvwx"
        assertEquals("$exactly24 · 30 Oct 2026", derive(exactly24))
        assertEquals("abcdefghijklmnopqrstuvw… · 30 Oct 2026", derive(exactly24 + "y"))
        assertEquals(24, derive(exactly24 + "y").substringBefore(" · ").length)
    }

    @Test
    fun longTextIsCutWithoutATrailingSpaceBeforeTheEllipsis() {
        assertEquals("Wifi router admin login… · 30 Oct 2026", derive("Wifi router admin login is on the box"))
        // Character 23 is a space.
        assertEquals("abcdefghijklmnopqrstuv… · 30 Oct 2026", derive("abcdefghijklmnopqrstuv wxyz"))
    }

    @Test
    fun emojiCountAsOneCharacterAndAreNeverSplit() {
        val emoji = "\uD83D\uDD11" // 🔑, a surrogate pair
        val text = emoji.repeat(30)
        val title = derive(text).substringBefore(" · ")
        assertEquals(emoji.repeat(23) + "…", title)
        assertEquals(24, title.codePointCount(0, title.length))
        // Cut lands right after an emoji when ASCII comes first.
        assertEquals("a".repeat(22) + emoji + "…", derive("a".repeat(22) + emoji.repeat(5)).substringBefore(" · "))
        assertEquals("$emoji Keys · 30 Oct 2026", derive("$emoji Keys"))
    }

    @Test
    fun dateUsesDayMonthYearForTheGivenLocale() {
        assertEquals("x · 1 Jan 2027", deriveNoteTitle("x", LocalDate.of(2027, 1, 1), Locale.US))
        assertEquals("x · 9 Dez. 2026", deriveNoteTitle("x", LocalDate.of(2026, 12, 9), Locale.GERMANY))
    }

    @Test
    fun limitLengthNeverSplitsASurrogatePair() {
        val emoji = "\uD83D\uDD11"
        assertEquals("abc", limitLength("abc", 5))
        assertEquals("abcde", limitLength("abcdefgh", 5))
        assertEquals("abcd", limitLength("abcd$emoji", 5))
        assertEquals("abc$emoji", limitLength("abc${emoji}x", 5))
    }

    private fun String.debug() = replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
}

package com.example.passwordmanager.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebsiteCatalogTest {

    private val entries = WebsiteCatalog.entries
    private val host = Regex("^([a-z0-9]([a-z0-9-]*[a-z0-9])?\\.)+[a-z]{2,}$")

    private fun suggest(query: String, limit: Int = MAX_SUGGESTIONS) =
        WebsiteCatalog.suggest(query, limit).also { result -> println("'$query' -> ${result.map { it.domain }}") }

    private fun domains(query: String) = suggest(query).map { it.domain }

    @Test
    fun catalogHasBetween200And300Entries() {
        println("catalog size ${entries.size}")
        assertTrue("size ${entries.size}", entries.size in 200..300)
    }

    @Test
    fun namesAreNonBlank() {
        entries.forEach { assertTrue("blank name: $it", it.name.isNotBlank()) }
    }

    @Test
    fun domainsAreUnique() {
        val duplicates = entries.groupBy { it.domain }.filterValues { it.size > 1 }.keys
        assertTrue("duplicate domains: $duplicates", duplicates.isEmpty())
    }

    @Test
    fun domainsAreBareLowercaseHosts() {
        entries.forEach {
            assertTrue("not a lowercase host: $it", host.matches(it.domain))
            assertTrue("www prefix: $it", !it.domain.startsWith("www."))
        }
    }

    @Test
    fun aliasesAreLowercaseNonBlankAndUniquePerEntry() {
        entries.forEach {
            it.aliases.forEach { alias ->
                assertTrue("blank alias: $it", alias.isNotBlank())
                assertEquals("alias not lowercase: $it", alias.lowercase(), alias)
            }
            assertEquals("repeated alias: $it", it.aliases.distinct().size, it.aliases.size)
        }
    }

    @Test
    fun blankQueryGivesNothing() {
        listOf("", "   ", "\t\n", "https://", "www.").forEach { assertEquals(emptyList<Website>(), suggest(it)) }
    }

    @Test
    fun gitSuggestsGitHubFirst() {
        assertEquals("github.com", domains("git").first())
    }

    @Test
    fun caseSchemeAndWwwAreIgnored() {
        val expected = domains("git")
        listOf("GIT", "  Git ", "https://git", "http://git", "www.git", "HTTPS://WWW.git").forEach {
            assertEquals(it, expected, domains(it))
        }
        assertEquals("github.com", WebsiteCatalog.normalize(" HTTPS://www.GitHub.com"))
    }

    @Test
    fun aliasesMatch() {
        assertEquals("instagram.com", domains("insta").first())
        assertEquals("onlinesbi.sbi.bank.in", domains("sbi").first())
        assertEquals("primevideo.com", domains("prime").first())
        assertEquals("x.com", domains("twitt").first())
    }

    @Test
    fun domainPrefixMatches() {
        assertEquals(listOf("onlinesbi.sbi.bank.in"), domains("onlinesb"))
    }

    @Test
    fun laterWordOfTheNameMatchesAtItsStart() {
        assertEquals(listOf("primevideo.com"), domains("video"))
        assertTrue(domains("pay").contains("pay.google.com"))
    }

    @Test
    fun substringsDoNotMatch() {
        assertEquals(emptyList<String>(), domains("ithub"))
        assertEquals(emptyList<String>(), domains("zzqx"))
    }

    @Test
    fun exactMatchRanksAbovePrefix() {
        // Vimeo comes earlier in the catalog and prefix-matches, but Vi is exact.
        assertEquals("myvi.in", domains("vi").first())
        assertEquals("amazon.com", domains("amazon").first())
    }

    @Test
    fun namePrefixRanksAboveDomainPrefix() {
        // Gmail (mail.google.com) comes earlier in the catalog but only its domain starts with "mail".
        assertEquals(listOf("mailchimp.com", "mail.google.com"), domains("mail").take(2))
    }

    @Test
    fun tiesKeepCatalogOrder() {
        assertEquals(listOf("amazon.com", "primevideo.com", "amazon.in"), domains("amazon").take(3))
    }

    @Test
    fun eachEntryAppearsOnce() {
        listOf("a", "go", "bank", "x", "amazon").forEach {
            val result = domains(it)
            assertEquals(it, result.distinct(), result)
        }
    }

    @Test
    fun resultIsCappedAtTheLimit() {
        assertEquals(MAX_SUGGESTIONS, suggest("a").size)
        assertEquals(2, suggest("a", limit = 2).size)
    }
}

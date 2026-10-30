package com.example.passwordmanager.util

const val MAX_SUGGESTIONS = 5

private val SCHEME_PREFIXES = listOf("https://", "http://")
private const val WWW_PREFIX = "www."
private val NAME_WORD_SEPARATORS = charArrayOf(' ', '-', '.')

/** A popular service: display [name], bare lowercase [domain], and lowercase search [aliases]. */
data class Website(val name: String, val domain: String, val aliases: List<String> = emptyList())

object WebsiteCatalog {

    val entries: List<Website> = WEBSITE_CATALOG_ENTRIES

    /**
     * Up to [limit] entries matching [query] by exact term, then name, domain or alias prefix, then the start
     * of a later word in the name. Catalog order breaks ties; there is no substring or fuzzy matching.
     */
    fun suggest(query: String, limit: Int = MAX_SUGGESTIONS): List<Website> {
        val term = normalize(query)
        if (term.isEmpty()) return emptyList()
        return entries
            .mapNotNull { website -> tierOf(website, term)?.let { tier -> tier to website } }
            .sortedBy { it.first }
            .take(limit)
            .map { it.second }
    }

    /** [query] trimmed and lowercased, without a leading `http(s)://` or `www.`. */
    fun normalize(query: String): String {
        var term = query.trim().lowercase()
        SCHEME_PREFIXES.firstOrNull { term.startsWith(it) }?.let { term = term.removePrefix(it) }
        return term.removePrefix(WWW_PREFIX)
    }

    private fun tierOf(website: Website, term: String): Int? {
        val name = website.name.lowercase()
        return when {
            name == term || website.domain == term || term in website.aliases -> 0
            name.startsWith(term) -> 1
            website.domain.startsWith(term) -> 2
            website.aliases.any { it.startsWith(term) } -> 3
            name.split(*NAME_WORD_SEPARATORS).drop(1).any { it.startsWith(term) } -> 4
            else -> null
        }
    }
}

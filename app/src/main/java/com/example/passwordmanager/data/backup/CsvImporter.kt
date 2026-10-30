package com.example.passwordmanager.data.backup

import com.example.passwordmanager.data.Credential
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.UncheckedIOException
import java.net.URI
import java.net.URISyntaxException
import java.nio.charset.CharacterCodingException
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord

private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
private const val BITWARDEN_COLUMN_PREFIX = "login_"
private const val BITWARDEN_LOGIN_TYPE = "login"

private enum class CsvLayout(val requiredColumns: List<String>) {
    /** Google Password Manager and Chrome; older Chrome exports have no `note` column. */
    Google(listOf("name", "url", "username", "password")),
    Bitwarden(listOf("type", "name", "login_uri", "login_username", "login_password"))
}

/** Reads Google Password Manager, Chrome and Bitwarden CSV exports, matching columns by header name. */
object CsvImporter {

    private val format = CSVFormat.RFC4180.builder()
        .setHeader()
        .setSkipHeaderRecord(true)
        .setIgnoreHeaderCase(true)
        .build()

    /** [now] becomes the creation time of every imported credential. */
    fun parse(file: ByteArray, now: Long): ParsedImport {
        val start = if (file.size >= UTF8_BOM.size && file.copyOf(UTF8_BOM.size).contentEquals(UTF8_BOM)) UTF8_BOM.size else 0
        // A decoder (not a Charset) so invalid UTF-8, such as a binary file, fails instead of turning into U+FFFD.
        val reader = InputStreamReader(ByteArrayInputStream(file, start, file.size - start), Charsets.UTF_8.newDecoder())
        try {
            CSVParser(reader, format).use { parser ->
                val layout = layoutOf(parser.headerNames)
                return parser.mapNotNull { it.toCredential(layout, now) }.toParsedImport()
            }
        } catch (e: CharacterCodingException) {
            throw BackupException.NotText(e)
        } catch (e: UncheckedIOException) {
            val cause = e.cause
            if (cause is CharacterCodingException) throw BackupException.NotText(cause)
            throw BackupException.Malformed("File is not a valid CSV file", e)
        } catch (e: IOException) {
            throw BackupException.Malformed("File is not a valid CSV file", e)
        } catch (e: IllegalArgumentException) {
            // commons-csv rejects headers with empty or duplicate names this way.
            throw BackupException.Malformed("File is not a valid CSV file", e)
        }
    }

    private fun layoutOf(headerNames: List<String>): CsvLayout {
        val columns = headerNames.map { it.trim().lowercase() }.toSet()
        val layout = if (columns.any { it.startsWith(BITWARDEN_COLUMN_PREFIX) }) CsvLayout.Bitwarden else CsvLayout.Google
        val missing = layout.requiredColumns.filterNot { it in columns }
        if (missing.isNotEmpty()) throw BackupException.MissingColumns(missing)
        return layout
    }

    /** Null for rows that aren't logins (Bitwarden notes, cards and identities). */
    private fun CSVRecord.toCredential(layout: CsvLayout, now: Long): Credential? = when (layout) {
        CsvLayout.Google -> Credential(
            website = websiteOf(value("url"), value("name")),
            username = value("username"),
            password = value("password"),
            dataCreate = now,
            note = value("note")
        )
        CsvLayout.Bitwarden -> if (value("type").trim() != BITWARDEN_LOGIN_TYPE) null else Credential(
            // Bitwarden puts every URI of a login in this one cell, separated by commas.
            website = websiteOf(value("login_uri").substringBefore(','), value("name")),
            username = value("login_username"),
            password = value("login_password"),
            dataCreate = now,
            isFav = value("favorite").trim().let { it == "1" || it.equals("true", ignoreCase = true) },
            note = value("notes")
        )
    }

    /** Empty when the column is absent or this row is shorter than the header. */
    private fun CSVRecord.value(column: String): String = if (isSet(column)) get(column) else ""

    /** The URL's host (`https://github.com/login` → `github.com`), else the URL as written, else the name. */
    private fun websiteOf(url: String, name: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return name.trim()
        val host = try {
            URI(trimmed).host
        } catch (e: URISyntaxException) {
            null
        }
        return host ?: trimmed
    }
}

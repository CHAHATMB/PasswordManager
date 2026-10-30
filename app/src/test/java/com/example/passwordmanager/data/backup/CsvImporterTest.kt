package com.example.passwordmanager.data.backup

import com.example.passwordmanager.data.Credential
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CsvImporterTest {

    private val now = 1_700_000_000_000

    @Test
    fun googleExportKeepsQuotedCommasQuotesAndNewlinesExactly() {
        val parsed = CsvImporter.parse(fixture("gpm.csv"), now)

        assertEquals(
            listOf(
                Credential(website = "github.com", username = "alice", password = "gh,secret\"1", dataCreate = now, note = "line one\nline two, with comma"),
                Credential(website = "www.example.com", username = "bob@example.com", password = "ex-pass-2", dataCreate = now, note = ""),
                // Android app rows use the package as the website; values are never trimmed or evaluated.
                Credential(website = "com.example.app", username = "dave", password = "app pass 4 ", dataCreate = now, note = "=SUM(A1)")
            ),
            parsed.credentials
        )
        assertEquals(1, parsed.invalid)
    }

    @Test
    fun chromeExportWithBomCrlfAndNoNoteColumn() {
        val parsed = CsvImporter.parse(fixture("chrome.csv"), now)

        assertEquals(
            listOf(
                Credential(website = "gitlab.com", username = "erin", password = "gl-pass,5", dataCreate = now, note = ""),
                Credential(website = "Intranet", username = "frank", password = "intranet-6", dataCreate = now, note = "")
            ),
            parsed.credentials
        )
        assertEquals(0, parsed.invalid)
    }

    @Test
    fun bitwardenExportImportsLoginsWithFavoritesAndSkipsOtherTypes() {
        val parsed = CsvImporter.parse(fixture("bitwarden.csv"), now)

        assertEquals(
            listOf(
                Credential(website = "github.com", username = "alice", password = "gh-secret-1", dataCreate = now, isFav = true, note = "multi\nline \"note\""),
                Credential(website = "example.com", username = "bob", password = "ex,pass-2", dataCreate = now, isFav = false, note = "")
            ),
            parsed.credentials
        )
        assertEquals(1, parsed.invalid)
    }

    @Test
    fun headersMatchByNameInAnyOrderAndCase() {
        val csv = "Password,Note,Username,URL,Name\nsecret,hi,zoe,https://zoe.example/,Zoe\n"

        val credential = CsvImporter.parse(csv.encodeToByteArray(), now).credentials.single()

        assertEquals(Credential(website = "zoe.example", username = "zoe", password = "secret", dataCreate = now, note = "hi"), credential)
    }

    @Test
    fun blankLinesAreIgnoredNotCountedInvalid() {
        val csv = "name,url,username,password\n\n,https://a.example/,ann,secret\n\n\n"

        val parsed = CsvImporter.parse(csv.encodeToByteArray(), now)

        assertEquals(listOf("a.example"), parsed.credentials.map { it.website })
        assertEquals(0, parsed.invalid)
    }

    @Test
    fun headerNamesWithSurroundingSpacesStillMatch() {
        val csv = "name, url ,username , password\nA,https://a.example/,ann,secret\n"

        val credential = CsvImporter.parse(csv.encodeToByteArray(), now).credentials.single()

        assertEquals(Credential(website = "a.example", username = "ann", password = "secret", dataCreate = now, note = ""), credential)
    }

    @Test
    fun rowWithNoUrlAndNoNameIsInvalid() {
        val parsed = CsvImporter.parse("name,url,username,password\n,,ann,secret\n".encodeToByteArray(), now)

        assertEquals(emptyList<Credential>(), parsed.credentials)
        assertEquals(1, parsed.invalid)
    }

    @Test
    fun missingRequiredColumnsAreReported() {
        val google = assertThrows(BackupException.MissingColumns::class.java) {
            CsvImporter.parse("name,url,user,pass\na,b,c,d\n".encodeToByteArray(), now)
        }
        val bitwarden = assertThrows(BackupException.MissingColumns::class.java) {
            CsvImporter.parse("type,name,login_uri,login_password\nlogin,a,b,c\n".encodeToByteArray(), now)
        }
        val empty = assertThrows(BackupException.MissingColumns::class.java) {
            CsvImporter.parse(ByteArray(0), now)
        }

        assertEquals(listOf("username", "password"), google.columns)
        assertEquals(listOf("login_username"), bitwarden.columns)
        assertEquals(listOf("name", "url", "username", "password"), empty.columns)
    }

    @Test
    fun backupJsonChosenAsCsvIsMalformed() {
        // Its quotes sit mid-field, which RFC 4180 does not allow.
        assertThrows(BackupException.Malformed::class.java) {
            CsvImporter.parse("{\"format\":\"pm-export\",\"version\":1}".encodeToByteArray(), now)
        }
    }

    @Test
    fun binaryFileIsNotText() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0xFF.toByte(), 0xFE.toByte())

        assertThrows(BackupException.NotText::class.java) { CsvImporter.parse(png, now) }
    }

    @Test
    fun unterminatedQuoteIsMalformed() {
        val csv = "name,url,username,password\na,https://a.example/,b,\"never closed\n"

        assertThrows(BackupException.Malformed::class.java) { CsvImporter.parse(csv.encodeToByteArray(), now) }
    }

    private fun fixture(name: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/csv/$name")) { "Missing fixture $name" }.use { it.readBytes() }
}

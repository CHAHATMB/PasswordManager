package com.example.passwordmanager.data.backup

import com.example.passwordmanager.data.crypto.Pbkdf2KeyDerivation
import com.example.passwordmanager.data.crypto.KdfParams
import java.util.Base64
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCryptoTest {

    private val crypto = BackupCrypto(Pbkdf2KeyDerivation())
    // Within the Argon2 bounds that parse() enforces; the PBKDF2 stand-in reads iterations only.
    private val params = KdfParams(memoryKib = 0, iterations = 10, parallelism = 1)
    private val password = "export password 1"
    private val items = listOf(
        BackupItem("github.com", "alice", "gh-secret-1", "line one\nline two, \"quoted\"", favorite = true, created = 1),
        BackupItem("example.com", "bob@example.com", "p@ss,word", "", favorite = false, created = 2)
    )

    @Test
    fun exportThenImportRestoresEqualItems() {
        val file = crypto.encrypt(items, password.toCharArray(), params)

        val restored = crypto.decrypt(crypto.parse(file), password.toCharArray())

        assertEquals(items, restored)
    }

    @Test
    fun fileIsPmExportJsonWithoutAnyPlaintextValues() {
        val text = crypto.encrypt(items, password.toCharArray(), params).decodeToString()

        assertTrue(text, text.contains("\"format\":\"pm-export\""))
        listOf("github.com", "alice", "gh-secret-1", "line one", "bob@example.com", "p@ss,word").forEach {
            assertFalse("File leaks '$it'", text.contains(it))
        }
    }

    @Test
    fun wrongPasswordThrowsWrongPassword() {
        val backup = crypto.parse(crypto.encrypt(items, password.toCharArray(), params))

        assertThrows(BackupException.WrongPassword::class.java) {
            crypto.decrypt(backup, "export password 2".toCharArray())
        }
    }

    @Test
    fun changingAnyKdfFieldIvOrCiphertextFailsDecryption() {
        val file = crypto.encrypt(items, password.toCharArray(), params)
        // The PBKDF2 test KDF ignores memory and parallelism, so those two cases fail only through the AAD.
        val tampered = listOf<(BackupFile) -> BackupFile>(
            { it.copy(kdf = it.kdf.copy(memoryKiB = it.kdf.memoryKiB + 1)) },
            { it.copy(kdf = it.kdf.copy(parallelism = it.kdf.parallelism + 1)) },
            { it.copy(kdf = it.kdf.copy(iterations = it.kdf.iterations - 1)) },
            { it.copy(kdf = it.kdf.copy(salt = it.kdf.salt.flipFirstBit())) },
            { it.copy(iv = it.iv.flipFirstBit()) },
            { it.copy(data = it.data.flipFirstBit()) }
        )

        tampered.forEachIndexed { index, change ->
            val backup = crypto.parse(file.edit(change))
            assertThrows("Case $index", BackupException.WrongPassword::class.java) {
                crypto.decrypt(backup, password.toCharArray())
            }
        }
    }

    @Test
    fun twoExportsOfTheSameDataUseDifferentSaltIvAndCiphertext() {
        val first = crypto.encrypt(items, password.toCharArray(), params).toBackupFile()
        val second = crypto.encrypt(items, password.toCharArray(), params).toBackupFile()

        assertNotEquals(first.kdf.salt, second.kdf.salt)
        assertNotEquals(first.iv, second.iv)
        assertNotEquals(first.data, second.data)
    }

    @Test
    fun invalidJsonIsMalformed() {
        assertThrows(BackupException.Malformed::class.java) {
            crypto.parse("{\"format\":\"pm-export\",".encodeToByteArray())
        }
    }

    @Test
    fun otherFormatsAreUnknown() {
        listOf("{\"format\":\"bitwarden\",\"version\":1}", "{\"encrypted\":false,\"items\":[]}", "[1,2]", "\"pm-export\"")
            .forEach { text ->
                assertThrows(text, BackupException.UnknownFormat::class.java) { crypto.parse(text.encodeToByteArray()) }
            }
    }

    @Test
    fun otherVersionsAreUnsupported() {
        val file = crypto.encrypt(items, password.toCharArray(), params)

        val thrown = assertThrows(BackupException.UnsupportedVersion::class.java) {
            crypto.parse(file.edit { it.copy(version = 2) })
        }
        assertEquals(2, thrown.version)
    }

    @Test
    fun missingFieldsUnknownCipherOrHugeKdfParamsAreMalformed() {
        val file = crypto.encrypt(items, password.toCharArray(), params)
        val invalid = listOf(
            "{\"format\":\"pm-export\",\"version\":1}".encodeToByteArray(),
            file.edit { it.copy(cipher = "AES-128-CBC") },
            file.edit { it.copy(kdf = it.kdf.copy(memoryKiB = 4 * 1024 * 1024)) },
            file.edit { it.copy(kdf = it.kdf.copy(iterations = 0)) },
            file.edit { it.copy(iv = "not base64!") },
            file.edit { it.copy(iv = Base64.getEncoder().encodeToString(ByteArray(8))) }
        )

        invalid.forEachIndexed { index, bytes ->
            assertThrows("Case $index", BackupException.Malformed::class.java) { crypto.parse(bytes) }
        }
    }

    private fun ByteArray.toBackupFile(): BackupFile = Json.decodeFromString(decodeToString())

    private fun ByteArray.edit(change: (BackupFile) -> BackupFile): ByteArray =
        Json.encodeToString(change(toBackupFile())).encodeToByteArray()

    private fun String.flipFirstBit(): String {
        val bytes = Base64.getDecoder().decode(this)
        bytes[0] = (bytes[0].toInt() xor 1).toByte()
        return Base64.getEncoder().encodeToString(bytes)
    }
}

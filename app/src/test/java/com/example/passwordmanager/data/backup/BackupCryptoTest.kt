package com.example.passwordmanager.data.backup

import com.example.passwordmanager.data.crypto.IV_SIZE_BYTES
import com.example.passwordmanager.data.crypto.Pbkdf2KeyDerivation
import com.example.passwordmanager.data.crypto.KdfParams
import com.example.passwordmanager.data.crypto.SALT_SIZE_BYTES
import com.example.passwordmanager.data.crypto.aesGcmCipher
import com.example.passwordmanager.data.crypto.derivePasswordKey
import java.util.Base64
import javax.crypto.Cipher
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCryptoTest {

    private val crypto = BackupCrypto(Pbkdf2KeyDerivation())
    // Within the Argon2 bounds that parse() enforces; the PBKDF2 stand-in reads iterations only.
    private val params = KdfParams(memoryKib = 16, iterations = 10, parallelism = 1)
    private val password = "export password 1"
    private val items = listOf(
        BackupItem("github.com", "alice", "gh-secret-1", "line one\nline two, \"quoted\"", favorite = true, created = 1),
        BackupItem("example.com", "bob@example.com", "p@ss,word", "", favorite = false, created = 2)
    )
    private val totp = listOf(
        BackupTotp("Example", "alice@example.com", "JBSWY3DPEHPK3PXP", "SHA1", digits = 6, period = 30, created = 3),
        BackupTotp("ACME Co", "bob", "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", "SHA256", digits = 8, period = 60, created = 4)
    )
    private val notes = listOf(
        BackupNote("Wifi router admin · 30 Oct 2026", "Wifi router admin\npassword is on the box", created = 5, updated = 6),
        BackupNote("Keys \uD83D\uDD11", "\"quoted\" and \\ back\tslash", created = 7, updated = 7)
    )
    private val content = BackupContent(items, totp, notes)

    @Test
    fun exportThenImportRestoresEqualItems() {
        val file = crypto.encrypt(content, password.toCharArray(), params)

        val restored = crypto.decrypt(crypto.parse(file), password.toCharArray())

        println("Restored ${restored.credentials.size} credentials, ${restored.totp} and ${restored.notes}")
        assertEquals(content, restored)
        assertEquals(2, crypto.parse(file).header.version)
    }

    @Test
    fun version1FileWithAnArrayPayloadStillDecrypts() {
        val restored = crypto.decrypt(crypto.parse(version1File(items)), password.toCharArray())

        assertEquals(BackupContent(items, totp = emptyList(), notes = emptyList()), restored)
    }

    @Test
    fun version2FileWithoutNotesStillDecryptsToNoNotes() {
        // As the 09 and 10 builds wrote it: no `notes` field.
        val payload = Json.encodeToString(mapOf("credentials" to Json.encodeToJsonElement(items), "totp" to Json.encodeToJsonElement(totp)))
        println("Old payload: ${payload.take(80)}…")

        val restored = crypto.decrypt(crypto.parse(encryptedFile(BACKUP_VERSION, payload)), password.toCharArray())

        assertEquals(BackupContent(items, totp, notes = emptyList()), restored)
    }

    @Test
    fun backupTotpEntriesAreValidatedLikeScannedAccounts() {
        val valid = totp[1].toTotpAccountOrNull()
        assertEquals(listOf("ACME Co", "bob", "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"), listOf(valid?.issuer, valid?.accountName, valid?.secret))
        assertEquals(4L, valid?.createdAt)
        listOf(
            totp[0].copy(secret = "ABC1!"),
            totp[0].copy(secret = ""),
            totp[0].copy(algorithm = "MD5"),
            totp[0].copy(digits = 7),
            totp[0].copy(period = 0)
        ).forEach { entry ->
            assertEquals(entry.toString(), null, entry.toTotpAccountOrNull())
        }
    }

    @Test
    fun relabellingAVersion2FileAsVersion1FailsDecryption() {
        val file = crypto.encrypt(content, password.toCharArray(), params).edit { it.copy(version = 1) }

        assertThrows(BackupException.WrongPassword::class.java) {
            crypto.decrypt(crypto.parse(file), password.toCharArray())
        }
    }

    @Test
    fun fileIsPmExportJsonWithoutAnyPlaintextValues() {
        val text = crypto.encrypt(content, password.toCharArray(), params).decodeToString()

        assertTrue(text, text.contains("\"format\":\"pm-export\""))
        listOf("github.com", "alice", "gh-secret-1", "line one", "bob@example.com", "p@ss,word", "JBSWY3DPEHPK3PXP", "ACME", "Wifi router", "on the box").forEach {
            assertFalse("File leaks '$it'", text.contains(it))
        }
    }

    @Test
    fun wrongPasswordThrowsWrongPassword() {
        val backup = crypto.parse(crypto.encrypt(content, password.toCharArray(), params))

        assertThrows(BackupException.WrongPassword::class.java) {
            crypto.decrypt(backup, "export password 2".toCharArray())
        }
    }

    @Test
    fun changingAnyKdfFieldIvOrCiphertextFailsDecryption() {
        val file = crypto.encrypt(content, password.toCharArray(), params)
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
        val first = crypto.encrypt(content, password.toCharArray(), params).toBackupFile()
        val second = crypto.encrypt(content, password.toCharArray(), params).toBackupFile()

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
        val file = crypto.encrypt(content, password.toCharArray(), params)

        val thrown = assertThrows(BackupException.UnsupportedVersion::class.java) {
            crypto.parse(file.edit { it.copy(version = 3) })
        }
        assertEquals(3, thrown.version)
    }

    @Test
    fun missingFieldsUnknownCipherOrOutOfRangeKdfParamsAreMalformed() {
        val file = crypto.encrypt(content, password.toCharArray(), params)
        val invalid = listOf(
            "{\"format\":\"pm-export\",\"version\":1}".encodeToByteArray(),
            file.edit { it.copy(cipher = "AES-128-CBC") },
            file.edit { it.copy(kdf = it.kdf.copy(memoryKiB = 128 * 1024 + 1)) },
            file.edit { it.copy(kdf = it.kdf.copy(memoryKiB = 0)) },
            file.edit { it.copy(kdf = it.kdf.copy(memoryKiB = 8 * 4 - 1, parallelism = 4)) },
            file.edit { it.copy(kdf = it.kdf.copy(iterations = 0)) },
            file.edit { it.copy(iv = "not base64!") },
            file.edit { it.copy(iv = Base64.getEncoder().encodeToString(ByteArray(8))) }
        )

        invalid.forEachIndexed { index, bytes ->
            assertThrows("Case $index", BackupException.Malformed::class.java) { crypto.parse(bytes) }
        }
    }

    /** A file as the 08 build wrote it: version 1, the payload a bare array of items. */
    private fun version1File(items: List<BackupItem>): ByteArray =
        encryptedFile(BACKUP_VERSION_CREDENTIALS_ONLY, Json.encodeToString(items))

    /** A file with [payload] encrypted exactly as [BackupCrypto.encrypt] does, labelled [version]. */
    private fun encryptedFile(version: Int, payload: String): ByteArray {
        val encoder = Base64.getEncoder()
        val salt = ByteArray(SALT_SIZE_BYTES) { it.toByte() }
        val iv = ByteArray(IV_SIZE_BYTES) { it.toByte() }
        val kdf = BackupKdf(BACKUP_KDF_TYPE, params.memoryKib, params.iterations, params.parallelism, encoder.encodeToString(salt))
        val header = BackupHeader(BACKUP_FORMAT, version, kdf, BACKUP_CIPHER, encoder.encodeToString(iv))
        val key = Pbkdf2KeyDerivation().derivePasswordKey(password.toCharArray(), salt, params)
        val ciphertext = aesGcmCipher(Cipher.ENCRYPT_MODE, key, iv, Json.encodeToString(header).encodeToByteArray())
            .doFinal(payload.encodeToByteArray())
        val file = BackupFile(header.format, header.version, kdf, header.cipher, header.iv, encoder.encodeToString(ciphertext))
        return Json.encodeToString(file).encodeToByteArray()
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

package com.example.passwordmanager.data.backup

import com.example.passwordmanager.data.crypto.IV_SIZE_BYTES
import com.example.passwordmanager.data.crypto.KdfParams
import com.example.passwordmanager.data.crypto.KeyDerivation
import com.example.passwordmanager.data.crypto.SALT_SIZE_BYTES
import com.example.passwordmanager.data.crypto.aesGcmCipher
import com.example.passwordmanager.data.crypto.derivePasswordKey
import com.example.passwordmanager.data.crypto.randomBytes
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull

// Bounds for KDF parameters read from a file, so a crafted file can't exhaust memory or time.
// The app writes at most 64 MiB; Argon2 needs at least 8 KiB per lane.
private const val MAX_KDF_MEMORY_KIB = 131072
private const val MIN_KDF_MEMORY_KIB_PER_LANE = 8
private const val MAX_KDF_ITERATIONS = 20
private const val MAX_KDF_PARALLELISM = 16

/** A backup file whose shape has been checked; [decrypt][BackupCrypto.decrypt] still needs the password. */
class EncryptedBackup(
    val header: BackupHeader,
    val kdfParams: KdfParams,
    val salt: ByteArray,
    val iv: ByteArray,
    val ciphertext: ByteArray
)

class BackupCrypto(
    private val keyDerivation: KeyDerivation,
    private val random: SecureRandom = SecureRandom()
) {

    /** Returns the backup file's bytes, with a fresh salt and IV. */
    fun encrypt(content: BackupContent, password: CharArray, params: KdfParams): ByteArray {
        val salt = random.randomBytes(SALT_SIZE_BYTES)
        val iv = random.randomBytes(IV_SIZE_BYTES)
        val header = BackupHeader(
            format = BACKUP_FORMAT,
            version = BACKUP_VERSION,
            kdf = BackupKdf(BACKUP_KDF_TYPE, params.memoryKib, params.iterations, params.parallelism, encoder.encodeToString(salt)),
            cipher = BACKUP_CIPHER,
            iv = encoder.encodeToString(iv)
        )
        val plaintext = Json.encodeToString(content).encodeToByteArray()
        val key = keyDerivation.derivePasswordKey(password, salt, params)
        val ciphertext = try {
            aesGcmCipher(Cipher.ENCRYPT_MODE, key, iv, header.aad()).doFinal(plaintext)
        } finally {
            key.fill(0)
            plaintext.fill(0)
        }
        val file = BackupFile(header.format, header.version, header.kdf, header.cipher, header.iv, encoder.encodeToString(ciphertext))
        return Json.encodeToString(file).encodeToByteArray()
    }

    /** Checks the format, version and fields, before any expensive key derivation. */
    fun parse(file: ByteArray): EncryptedBackup {
        val root = try {
            Json.parseToJsonElement(file.decodeToString())
        } catch (e: SerializationException) {
            throw BackupException.Malformed("Backup file is not valid JSON", e)
        }
        if (root !is JsonObject || (root["format"] as? JsonPrimitive)?.content != BACKUP_FORMAT) {
            throw BackupException.UnknownFormat()
        }
        val version = (root["version"] as? JsonPrimitive)?.intOrNull
        if (version != BACKUP_VERSION && version != BACKUP_VERSION_CREDENTIALS_ONLY) {
            throw BackupException.UnsupportedVersion(version)
        }
        val backup = try {
            Json.decodeFromJsonElement<BackupFile>(root)
        } catch (e: SerializationException) {
            throw BackupException.Malformed("Backup file has missing or invalid fields", e)
        } catch (e: IllegalArgumentException) {
            throw BackupException.Malformed("Backup file has missing or invalid fields", e)
        }
        val kdf = backup.kdf
        if (backup.cipher != BACKUP_CIPHER || kdf.type != BACKUP_KDF_TYPE) {
            throw BackupException.Malformed("Unsupported cipher or key derivation")
        }
        if (kdf.parallelism !in 1..MAX_KDF_PARALLELISM ||
            kdf.memoryKiB !in MIN_KDF_MEMORY_KIB_PER_LANE * kdf.parallelism..MAX_KDF_MEMORY_KIB ||
            kdf.iterations !in 1..MAX_KDF_ITERATIONS
        ) {
            throw BackupException.Malformed("Key derivation parameters are out of range")
        }
        val encrypted = try {
            EncryptedBackup(
                header = backup.header(),
                kdfParams = KdfParams(kdf.memoryKiB, kdf.iterations, kdf.parallelism),
                salt = decoder.decode(kdf.salt),
                iv = decoder.decode(backup.iv),
                ciphertext = decoder.decode(backup.data)
            )
        } catch (e: IllegalArgumentException) {
            throw BackupException.Malformed("Backup file field is not valid Base64", e)
        }
        if (encrypted.salt.size != SALT_SIZE_BYTES || encrypted.iv.size != IV_SIZE_BYTES) {
            throw BackupException.Malformed("Invalid salt or IV length")
        }
        return encrypted
    }

    /** Throws [BackupException.WrongPassword] when the password is wrong or any part of the file was changed. */
    fun decrypt(backup: EncryptedBackup, password: CharArray): BackupContent {
        val key = keyDerivation.derivePasswordKey(password, backup.salt, backup.kdfParams)
        val plaintext = try {
            aesGcmCipher(Cipher.DECRYPT_MODE, key, backup.iv, backup.header.aad()).doFinal(backup.ciphertext)
        } catch (e: AEADBadTagException) {
            throw BackupException.WrongPassword(e)
        } finally {
            key.fill(0)
        }
        try {
            val json = plaintext.decodeToString()
            return if (backup.header.version == BACKUP_VERSION_CREDENTIALS_ONLY) {
                BackupContent(credentials = Json.decodeFromString<List<BackupItem>>(json))
            } else {
                Json.decodeFromString<BackupContent>(json)
            }
        } catch (e: SerializationException) {
            throw BackupException.Malformed("Backup contents are invalid", e)
        } catch (e: IllegalArgumentException) {
            throw BackupException.Malformed("Backup contents are invalid", e)
        } finally {
            plaintext.fill(0)
        }
    }

    private fun BackupHeader.aad(): ByteArray = Json.encodeToString(this).encodeToByteArray()

    private companion object {
        val encoder: Base64.Encoder = Base64.getEncoder()
        val decoder: Base64.Decoder = Base64.getDecoder()
    }
}

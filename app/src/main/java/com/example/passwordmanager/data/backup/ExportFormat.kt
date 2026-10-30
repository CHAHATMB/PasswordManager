package com.example.passwordmanager.data.backup

import com.example.passwordmanager.data.Credential
import kotlinx.serialization.Serializable

const val BACKUP_FORMAT = "pm-export"
const val BACKUP_VERSION = 1
const val BACKUP_CIPHER = "AES-256-GCM"
const val BACKUP_KDF_TYPE = "argon2id"

@Serializable
data class BackupKdf(
    val type: String,
    val memoryKiB: Int,
    val iterations: Int,
    val parallelism: Int,
    val salt: String
)

/** Every field of [BackupFile] except `data`. Its JSON encoding is the AAD, so none can be altered. */
@Serializable
data class BackupHeader(
    val format: String,
    val version: Int,
    val kdf: BackupKdf,
    val cipher: String,
    val iv: String
)

@Serializable
data class BackupFile(
    val format: String,
    val version: Int,
    val kdf: BackupKdf,
    val cipher: String,
    val iv: String,
    val data: String
) {
    fun header() = BackupHeader(format, version, kdf, cipher, iv)
}

@Serializable
data class BackupItem(
    val website: String,
    val username: String,
    val password: String,
    val note: String,
    val favorite: Boolean,
    val created: Long
)

fun Credential.toBackupItem() = BackupItem(website, username, password, note, isFav, dataCreate)

fun BackupItem.toCredential() =
    Credential(website = website, username = username, password = password, dataCreate = created, isFav = favorite, note = note)

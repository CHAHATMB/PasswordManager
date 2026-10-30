package com.example.passwordmanager.data.backup

import com.example.passwordmanager.data.Credential
import com.example.passwordmanager.data.totp.TotpAccount
import com.example.passwordmanager.data.totp.toTotpAccount
import com.example.passwordmanager.util.totp.OtpAuthAccount
import com.example.passwordmanager.util.totp.OtpAuthResult
import com.example.passwordmanager.util.totp.TotpAlgorithm
import kotlinx.serialization.Serializable

const val BACKUP_FORMAT = "pm-export"
const val BACKUP_VERSION = 2
/** Payload is a bare JSON array of [BackupItem]. Still imported. */
const val BACKUP_VERSION_CREDENTIALS_ONLY = 1
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

@Serializable
data class BackupTotp(
    val issuer: String,
    val accountName: String,
    val secret: String,
    val algorithm: String,
    val digits: Int,
    val period: Int,
    val created: Long
) {
    override fun toString() = "BackupTotp(issuer=$issuer, accountName=$accountName)"
}

/** The encrypted payload of a version 2 backup. */
@Serializable
data class BackupContent(
    val credentials: List<BackupItem>,
    val totp: List<BackupTotp> = emptyList()
)

fun TotpAccount.toBackupTotp() = BackupTotp(issuer, accountName, secret, algorithm.name, digits, period, createdAt)

/** Null when the entry fails the same checks as a scanned or typed account. */
fun BackupTotp.toTotpAccountOrNull(): TotpAccount? {
    val algorithm = TotpAlgorithm.fromName(algorithm) ?: return null
    val result = OtpAuthAccount.create(issuer, accountName, secret, algorithm, digits, period)
    return (result as? OtpAuthResult.Totp)?.account?.toTotpAccount(created)
}

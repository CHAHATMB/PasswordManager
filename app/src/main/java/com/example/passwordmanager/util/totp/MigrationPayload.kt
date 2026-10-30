package com.example.passwordmanager.util.totp

import java.net.URLDecoder
import java.util.Base64

private const val MIGRATION_SCHEME = "otpauth-migration://"
private const val DATA_PARAMETER = "data"
private const val UTF_8 = "UTF-8"
// Scanned text is untrusted. A QR code holds at most ~3 KB, so a real transfer URI is far shorter.
private const val MAX_MIGRATION_URI_LENGTH = 8192
// Google splits exports into batches of about 10 accounts per QR code.
private const val MAX_ACCOUNTS_PER_PAYLOAD = 100
// Google's export has no period field: every account uses 30 seconds.
private const val MIGRATION_PERIOD = DEFAULT_TOTP_PERIOD

// Field numbers of the reverse-engineered MigrationPayload and OtpParameters messages.
private const val PAYLOAD_OTP_PARAMETERS = 1
private const val PAYLOAD_BATCH_SIZE = 3
private const val PAYLOAD_BATCH_INDEX = 4
private const val PAYLOAD_BATCH_ID = 5
private const val OTP_SECRET = 1
private const val OTP_NAME = 2
private const val OTP_ISSUER = 3
private const val OTP_ALGORITHM = 4
private const val OTP_DIGITS = 5
private const val OTP_TYPE = 6

private const val ALGORITHM_UNSPECIFIED = 0L
private const val ALGORITHM_SHA1 = 1L
private const val ALGORITHM_SHA256 = 2L
private const val ALGORITHM_SHA512 = 3L
private const val DIGITS_UNSPECIFIED = 0L
private const val DIGITS_SIX = 1L
private const val DIGITS_EIGHT = 2L
private const val TYPE_TOTP = 2L
private const val SIX_DIGITS = 6
private const val EIGHT_DIGITS = 8

sealed interface MigrationResult {
    /**
     * One QR code of a Google Authenticator export. [skipped] counts entries that are not supported
     * (HOTP, MD5, unknown type) or invalid. [batchIndex] is 0-based, out of [batchSize] codes.
     */
    class Batch(
        val accounts: List<OtpAuthAccount>,
        val skipped: Int,
        val batchIndex: Int,
        val batchSize: Int,
        val batchId: Int
    ) : MigrationResult

    /** Not an `otpauth-migration://` URI at all. */
    data object NotMigrationUri : MigrationResult

    /** The scheme is right but the data is missing, oversized, not Base64 or not a valid payload. */
    data object MalformedPayload : MigrationResult
}

/** Google Authenticator's "Transfer accounts" QR code: `otpauth-migration://offline?data=<Base64 protobuf>`. */
object MigrationPayload {

    fun parse(text: String): MigrationResult {
        val uri = text.trim()
        if (!uri.startsWith(MIGRATION_SCHEME, ignoreCase = true)) return MigrationResult.NotMigrationUri
        if (uri.length > MAX_MIGRATION_URI_LENGTH) return MigrationResult.MalformedPayload
        val data = uri.substringAfter('?', "").split('&')
            .firstOrNull { it.substringBefore('=') == DATA_PARAMETER }
            ?.substringAfter('=')
            ?: return MigrationResult.MalformedPayload
        val bytes = try {
            decodeBase64(data)
        } catch (e: IllegalArgumentException) {
            return MigrationResult.MalformedPayload
        }
        return try {
            if (bytes.isEmpty()) MigrationResult.MalformedPayload else readPayload(bytes)
        } catch (e: ProtobufException) {
            MigrationResult.MalformedPayload
        } finally {
            bytes.fill(0)
        }
    }

    /** Percent-encoded standard Base64; also accepts raw `+`, the URL-safe alphabet and missing padding. */
    private fun decodeBase64(data: String): ByteArray {
        // URLDecoder reads a raw `+` as a space; Base64 has no spaces, so turn them back.
        val base64 = URLDecoder.decode(data, UTF_8)
            .replace(' ', '+')
            .replace('-', '+')
            .replace('_', '/')
            .trimEnd('=')
        return Base64.getDecoder().decode(base64)
    }

    private fun readPayload(bytes: ByteArray): MigrationResult.Batch {
        val reader = ProtobufReader(bytes)
        val accounts = mutableListOf<OtpAuthAccount>()
        var entries = 0
        var batchSize = 1L
        var batchIndex = 0L
        var batchId = 0L
        while (reader.hasMore) {
            val (field, wireType) = reader.readTag()
            when {
                field == PAYLOAD_OTP_PARAMETERS && wireType == WIRE_LENGTH_DELIMITED -> {
                    if (++entries > MAX_ACCOUNTS_PER_PAYLOAD) throw ProtobufException("Too many accounts")
                    readAccount(reader.readBytes())?.let(accounts::add)
                }
                field == PAYLOAD_BATCH_SIZE && wireType == WIRE_VARINT -> batchSize = reader.readVarint()
                field == PAYLOAD_BATCH_INDEX && wireType == WIRE_VARINT -> batchIndex = reader.readVarint()
                field == PAYLOAD_BATCH_ID && wireType == WIRE_VARINT -> batchId = reader.readVarint()
                else -> reader.skip(wireType)
            }
        }
        // Older exports leave the batch fields unset; keep "code k of n" meaningful for odd values too.
        val size = batchSize.coerceIn(1L, MAX_ACCOUNTS_PER_PAYLOAD.toLong()).toInt()
        return MigrationResult.Batch(
            accounts = accounts,
            skipped = entries - accounts.size,
            batchIndex = batchIndex.coerceIn(0L, size - 1L).toInt(),
            batchSize = size,
            batchId = batchId.toInt()
        )
    }

    /** Null for entries Vaultly can't use. */
    private fun readAccount(bytes: ByteArray): OtpAuthAccount? {
        val reader = ProtobufReader(bytes)
        var secret = ByteArray(0)
        var name = ""
        var issuer = ""
        var algorithm = ALGORITHM_UNSPECIFIED
        var digits = DIGITS_UNSPECIFIED
        var type = 0L
        try {
            while (reader.hasMore) {
                val (field, wireType) = reader.readTag()
                when {
                    field == OTP_SECRET && wireType == WIRE_LENGTH_DELIMITED -> {
                        secret.fill(0)
                        secret = reader.readBytes()
                    }
                    field == OTP_NAME && wireType == WIRE_LENGTH_DELIMITED -> name = reader.readBytes().decodeToString()
                    field == OTP_ISSUER && wireType == WIRE_LENGTH_DELIMITED -> issuer = reader.readBytes().decodeToString()
                    field == OTP_ALGORITHM && wireType == WIRE_VARINT -> algorithm = reader.readVarint()
                    field == OTP_DIGITS && wireType == WIRE_VARINT -> digits = reader.readVarint()
                    field == OTP_TYPE && wireType == WIRE_VARINT -> type = reader.readVarint()
                    else -> reader.skip(wireType)
                }
            }
            if (type != TYPE_TOTP) return null
            val totpAlgorithm = when (algorithm) {
                ALGORITHM_UNSPECIFIED, ALGORITHM_SHA1 -> TotpAlgorithm.SHA1
                ALGORITHM_SHA256 -> TotpAlgorithm.SHA256
                ALGORITHM_SHA512 -> TotpAlgorithm.SHA512
                else -> return null
            }
            val digitCount = when (digits) {
                DIGITS_UNSPECIFIED, DIGITS_SIX -> SIX_DIGITS
                DIGITS_EIGHT -> EIGHT_DIGITS
                else -> return null
            }
            // Same label rule as otpauth URIs: the name may be "Issuer:account", and an explicit issuer wins.
            val labelIssuer = if (name.contains(':')) name.substringBefore(':') else ""
            val result = OtpAuthAccount.create(
                issuer = issuer.ifBlank { labelIssuer },
                accountName = name.substringAfter(':'),
                secret = Base32.encode(secret),
                algorithm = totpAlgorithm,
                digits = digitCount,
                period = MIGRATION_PERIOD
            )
            return (result as? OtpAuthResult.Totp)?.account
        } finally {
            secret.fill(0)
            bytes.fill(0)
        }
    }
}

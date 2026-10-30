package com.example.passwordmanager.presentation.Authenticator

import android.net.Uri
import com.example.passwordmanager.data.totp.TotpAccount
import com.example.passwordmanager.util.totp.DEFAULT_TOTP_ALGORITHM
import com.example.passwordmanager.util.totp.DEFAULT_TOTP_DIGITS
import com.example.passwordmanager.util.totp.DEFAULT_TOTP_PERIOD
import com.example.passwordmanager.util.totp.MigrationResult
import com.example.passwordmanager.util.totp.OtpAuthAccount
import com.example.passwordmanager.util.totp.OtpAuthResult
import com.example.passwordmanager.util.totp.TOTP_PERIOD_RANGE
import com.example.passwordmanager.util.totp.TotpAlgorithm

data class TotpCodeItem(val account: TotpAccount, val code: String, val secondsRemaining: Int)

data class AuthenticatorState(
    val items: List<TotpCodeItem> = emptyList(),
    val unixSeconds: Long = 0,
    /** False until the first database emission, so an empty list isn't mistaken for no accounts. */
    val isLoaded: Boolean = false
)

/** Input of the manual entry form. [period] is the raw text of its field. */
data class TotpDraft(
    val issuer: String = "",
    val accountName: String = "",
    val secret: String = "",
    val algorithm: TotpAlgorithm = DEFAULT_TOTP_ALGORITHM,
    val digits: Int = DEFAULT_TOTP_DIGITS,
    val period: String = DEFAULT_TOTP_PERIOD.toString(),
    val isAdvancedOpen: Boolean = false,
    /** Errors show only after the first Save, not while the user is still typing. */
    val isSubmitted: Boolean = false
) {
    val accountNameError: String? get() = if (accountName.isBlank()) "Enter the account name" else null

    val periodError: String? get() = if (period.trim().toIntOrNull() in TOTP_PERIOD_RANGE) null else "Enter 1 to 300 seconds"

    val secretError: String?
        get() = when (OtpAuthAccount.create(issuer, accountName, secret, algorithm, digits, DEFAULT_TOTP_PERIOD)) {
            OtpAuthResult.MissingSecret -> "Enter the setup key"
            OtpAuthResult.InvalidSecret -> "Not a valid setup key: use letters A–Z and digits 2–7"
            else -> null
        }

    /** The account to save, or null while any field has an error. */
    fun toAccountOrNull(): OtpAuthAccount? {
        if (accountNameError != null) return null
        val period = period.trim().toIntOrNull() ?: return null
        val result = OtpAuthAccount.create(issuer, accountName, secret, algorithm, digits, period)
        return (result as? OtpAuthResult.Totp)?.account
    }
}

enum class ScanError { NotAuthenticator, CounterBased, InvalidTransferCode, InvalidCode, NoQrCodeInImage, CameraPermissionDenied }

/** Where a QR code came from, so "Scan next code" of a multi-code transfer uses the same way. */
enum class ScanSource { Camera, Image }

/** Outcome of importing one Google Authenticator transfer code. [batchIndex] is 0-based. */
data class MigrationImportSummary(
    val imported: Int,
    val duplicates: Int,
    val unsupported: Int,
    val batchIndex: Int,
    val batchSize: Int
) {
    val hasNextCode: Boolean get() = batchIndex + 1 < batchSize
}

/** The add/edit/delete step on screen. Held in the ViewModel so it survives a configuration change. */
sealed interface AuthenticatorDialog {
    data class ManualEntry(val draft: TotpDraft) : AuthenticatorDialog
    /** Reading a picked image. */
    data object Scanning : AuthenticatorDialog
    data class ConfirmScanned(val account: TotpAccount) : AuthenticatorDialog
    /** How to export from Google Authenticator, with buttons to scan. */
    data object GoogleImportHelp : AuthenticatorDialog
    data class ImportPreview(val batch: MigrationResult.Batch, val source: ScanSource) : AuthenticatorDialog
    data class Imported(val summary: MigrationImportSummary, val source: ScanSource) : AuthenticatorDialog
    data class Edit(val account: TotpAccount, val issuer: String, val accountName: String) : AuthenticatorDialog
    data class Delete(val account: TotpAccount) : AuthenticatorDialog
    data class Failed(val error: ScanError) : AuthenticatorDialog
}

sealed interface AuthenticatorEvent {
    data object OpenManualEntry : AuthenticatorEvent
    data class ChangeDraft(val draft: TotpDraft) : AuthenticatorEvent
    data object SaveDraft : AuthenticatorEvent
    /** [text] is null when the scanner was closed without a result. */
    data class QrScanned(val text: String?, val isCameraPermissionMissing: Boolean) : AuthenticatorEvent
    data class ImagePicked(val uri: Uri?) : AuthenticatorEvent
    data object SaveScanned : AuthenticatorEvent
    data object OpenGoogleImport : AuthenticatorEvent
    data object ImportMigration : AuthenticatorEvent
    data class CopyCode(val account: TotpAccount) : AuthenticatorEvent
    data class StartEdit(val account: TotpAccount) : AuthenticatorEvent
    data class ChangeEdit(val issuer: String, val accountName: String) : AuthenticatorEvent
    data object SaveEdit : AuthenticatorEvent
    data class StartDelete(val account: TotpAccount) : AuthenticatorEvent
    data object ConfirmDelete : AuthenticatorEvent
    data object Dismiss : AuthenticatorEvent
}

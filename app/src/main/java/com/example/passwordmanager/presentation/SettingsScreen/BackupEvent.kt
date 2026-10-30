package com.example.passwordmanager.presentation.SettingsScreen

import android.net.Uri
import com.example.passwordmanager.data.backup.ImportResult

sealed interface BackupEvent {
    data object StartExport : BackupEvent
    data class VerifyMasterPassword(val password: String) : BackupEvent
    data class CreateBackup(val password: String, val confirmation: String) : BackupEvent
    data object SavePickerOpened : BackupEvent
    /** [uri] is null when the user backed out of the picker. */
    data class SaveBackup(val uri: Uri?) : BackupEvent
    data class OpenBackup(val uri: Uri?) : BackupEvent
    data class ImportBackup(val password: String) : BackupEvent
    data object StartCsvImport : BackupEvent
    data class ImportCsv(val uri: Uri?) : BackupEvent
    data object Dismiss : BackupEvent
}

sealed interface BackupDialog {
    data class MasterPassword(val error: BackupError? = null) : BackupDialog
    data class ExportPassword(val error: BackupError? = null) : BackupDialog
    /** The encrypted backup is ready; the UI opens the save picker once. */
    data object ChooseSaveLocation : BackupDialog
    data class BackupPassword(val error: BackupError? = null) : BackupDialog
    data object CsvWarning : BackupDialog
    data object Working : BackupDialog
    data object Exported : BackupDialog
    data class Imported(val result: ImportResult) : BackupDialog
    data class Failed(val error: BackupError) : BackupDialog
}

sealed interface BackupError {
    data object WrongMasterPassword : BackupError
    data object PasswordTooShort : BackupError
    data object PasswordMismatch : BackupError
    data object WrongBackupPassword : BackupError
    data object FileTooLarge : BackupError
    data object NotABackup : BackupError
    data object UnsupportedVersion : BackupError
    data object DamagedBackup : BackupError
    data object NotText : BackupError
    data object InvalidCsv : BackupError
    data class MissingColumns(val columns: List<String>) : BackupError
    data object Unreadable : BackupError
    data object Unexpected : BackupError
}

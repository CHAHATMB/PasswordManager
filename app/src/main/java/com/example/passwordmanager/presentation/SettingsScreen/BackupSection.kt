package com.example.passwordmanager.presentation.SettingsScreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.example.passwordmanager.data.backup.MAX_IMPORT_FILE_MEGABYTES
import com.example.passwordmanager.presentation.MIN_MASTER_PASSWORD_LENGTH
import com.example.passwordmanager.presentation.components.ConfirmDialog
import com.example.passwordmanager.presentation.components.PasswordTextField
import com.example.passwordmanager.presentation.components.SectionHeader
import com.example.passwordmanager.ui.theme.Spacing

private val secureDialog = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn)

/** Export and import entries for the Settings screen, with their dialogs. [onPick] opens a file picker. */
@Composable
fun BackupSection(dialog: BackupDialog?, onEvent: (BackupEvent) -> Unit, onPick: (BackupPicker) -> Unit) {

    SectionHeader(text = "Backup")
    OutlinedButton(onClick = { onEvent(BackupEvent.StartExport) }, modifier = Modifier.fillMaxWidth()) {
        Text(text = "Export encrypted backup")
    }
    OutlinedButton(onClick = { onPick(BackupPicker.OpenBackup) }, modifier = Modifier.fillMaxWidth()) {
        Text(text = "Import backup")
    }
    OutlinedButton(onClick = { onEvent(BackupEvent.StartCsvImport) }, modifier = Modifier.fillMaxWidth()) {
        Text(text = "Import from CSV")
    }

    val dismiss = { onEvent(BackupEvent.Dismiss) }
    when (dialog) {
        null -> Unit
        is BackupDialog.MasterPassword -> PasswordDialog(
            title = "Export encrypted backup",
            message = "Enter your master password to continue.",
            error = dialog.error,
            confirmLabel = "Continue",
            withConfirmation = false,
            onConfirm = { password, _ -> onEvent(BackupEvent.VerifyMasterPassword(password)) },
            onDismiss = dismiss
        )
        is BackupDialog.ExportPassword -> PasswordDialog(
            title = "Choose an export password",
            message = "It protects the backup file and is needed to restore it. Use at least " +
                "$MIN_MASTER_PASSWORD_LENGTH characters. It cannot be recovered.",
            error = dialog.error,
            confirmLabel = "Export",
            withConfirmation = true,
            onConfirm = { password, confirmation -> onEvent(BackupEvent.CreateBackup(password, confirmation)) },
            onDismiss = dismiss
        )
        BackupDialog.ChooseSaveLocation -> LaunchedEffect(Unit) {
            onEvent(BackupEvent.SavePickerOpened)
            onPick(BackupPicker.SaveBackup)
        }
        is BackupDialog.BackupPassword -> PasswordDialog(
            title = "Import backup",
            message = "Enter the export password of this backup file.",
            error = dialog.error,
            confirmLabel = "Import",
            withConfirmation = false,
            onConfirm = { password, _ -> onEvent(BackupEvent.ImportBackup(password)) },
            onDismiss = dismiss
        )
        BackupDialog.CsvWarning -> ConfirmDialog(
            title = "Import from CSV",
            message = "A CSV export is a plaintext file: anyone who can open it can read every " +
                "password in it. Delete the file from your device after the import.",
            confirmLabel = "Choose file",
            onConfirm = {
                dismiss()
                onPick(BackupPicker.OpenCsv)
            },
            onDismiss = dismiss
        )
        BackupDialog.Working -> AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                securePolicy = SecureFlagPolicy.SecureOn
            ),
            confirmButton = {},
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.l), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Text(text = "Working…")
                }
            }
        )
        BackupDialog.Exported -> MessageDialog(
            title = "Backup saved",
            message = "Keep the export password safe: without it the backup cannot be restored.",
            onDismiss = dismiss
        )
        is BackupDialog.Imported -> MessageDialog(
            title = "Import finished",
            message = with(dialog.result) {
                "Imported: $imported\nSkipped as duplicate: $duplicates\nInvalid (no website or password): $invalid"
            },
            onDismiss = dismiss
        )
        is BackupDialog.Failed -> MessageDialog(title = "Nothing was imported or exported", message = dialog.error.message(), onDismiss = dismiss)
    }
}

@Composable
private fun PasswordDialog(
    title: String,
    message: String,
    error: BackupError?,
    confirmLabel: String,
    withConfirmation: Boolean,
    onConfirm: (password: String, confirmation: String) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = secureDialog,
        title = { Text(text = title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(text = message)
                PasswordTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Password",
                    modifier = Modifier.fillMaxWidth()
                )
                if (withConfirmation) {
                    PasswordTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it },
                        label = "Confirm password",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                error?.let { Text(text = it.message(), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = password.isNotEmpty(), onClick = { onConfirm(password, confirmation) }) {
                Text(text = confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(text = "Cancel") } }
    )
}

@Composable
private fun MessageDialog(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = secureDialog,
        title = { Text(text = title) },
        text = { Text(text = message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(text = "OK") } }
    )
}

private fun BackupError.message(): String = when (this) {
    BackupError.WrongMasterPassword -> "Wrong master password."
    BackupError.PasswordTooShort -> "Use at least $MIN_MASTER_PASSWORD_LENGTH characters."
    BackupError.PasswordMismatch -> "The passwords don't match."
    BackupError.WrongBackupPassword -> "Wrong export password, or the file was modified."
    BackupError.FileTooLarge -> "The file is larger than $MAX_IMPORT_FILE_MEGABYTES MB."
    BackupError.NotABackup -> "This is not a Vaultly backup file."
    BackupError.UnsupportedVersion -> "This backup was made by a newer version of the app."
    BackupError.DamagedBackup -> "This backup file is damaged."
    BackupError.NotText -> "This is not a CSV file: it is not UTF-8 text."
    BackupError.InvalidCsv -> "This is not a valid CSV file."
    is BackupError.MissingColumns -> "The CSV file has no ${columns.joinToString()} column. " +
        "Use an export from Google Password Manager, Chrome or Bitwarden."
    BackupError.Unreadable -> "The file could not be read or written."
    BackupError.Unexpected -> "Something went wrong. Please try again."
}

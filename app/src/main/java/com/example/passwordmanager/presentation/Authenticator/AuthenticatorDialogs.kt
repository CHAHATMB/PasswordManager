package com.example.passwordmanager.presentation.Authenticator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.example.passwordmanager.presentation.components.AppTextField
import com.example.passwordmanager.presentation.components.ConfirmDialog
import com.example.passwordmanager.presentation.components.PasswordTextField
import com.example.passwordmanager.presentation.isTotpEditValid
import com.example.passwordmanager.ui.theme.Spacing
import com.example.passwordmanager.util.totp.MigrationResult
import com.example.passwordmanager.util.totp.TOTP_DIGITS
import com.example.passwordmanager.util.totp.TotpAlgorithm

private val secureDialog = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn)

/** The add, confirm, edit, delete and error steps for [dialog]. */
@Composable
fun AuthenticatorDialogs(
    dialog: AuthenticatorDialog?,
    unixSeconds: Long,
    onEvent: (AuthenticatorEvent) -> Unit,
    onScanQrCode: () -> Unit,
    onScanImage: () -> Unit
) {
    val dismiss = { onEvent(AuthenticatorEvent.Dismiss) }
    when (dialog) {
        null -> Unit
        AuthenticatorDialog.GoogleImportHelp -> AlertDialog(
            onDismissRequest = dismiss,
            properties = secureDialog,
            title = { Text(text = "Import from Google Authenticator") },
            text = {
                Text(
                    text = "In Google Authenticator: ⋮ → Transfer accounts → Export accounts, then scan the code. " +
                        "If it shows several codes, scan them one after another."
                )
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        dismiss()
                        onScanImage()
                    }) { Text(text = "From image") }
                    TextButton(onClick = {
                        dismiss()
                        onScanQrCode()
                    }) { Text(text = "Scan code") }
                }
            },
            dismissButton = { TextButton(onClick = dismiss) { Text(text = "Cancel") } }
        )
        is AuthenticatorDialog.ImportPreview -> MigrationPreviewDialog(
            batch = dialog.batch,
            onImport = { onEvent(AuthenticatorEvent.ImportMigration) },
            onDismiss = dismiss
        )
        is AuthenticatorDialog.Imported -> {
            val summary = dialog.summary
            AlertDialog(
                onDismissRequest = dismiss,
                properties = secureDialog,
                title = {
                    Text(
                        text = if (summary.batchSize > 1) {
                            "Code ${summary.batchIndex + 1} of ${summary.batchSize} imported"
                        } else {
                            "Import finished"
                        }
                    )
                },
                text = { Text(text = summary.message()) },
                confirmButton = {
                    if (summary.hasNextCode) {
                        TextButton(onClick = {
                            dismiss()
                            if (dialog.source == ScanSource.Camera) onScanQrCode() else onScanImage()
                        }) { Text(text = "Scan next code") }
                    } else {
                        TextButton(onClick = dismiss) { Text(text = "OK") }
                    }
                },
                dismissButton = if (summary.hasNextCode) {
                    { TextButton(onClick = dismiss) { Text(text = "Done") } }
                } else {
                    null
                }
            )
        }
        is AuthenticatorDialog.ManualEntry -> ManualEntrySheet(draft = dialog.draft, onEvent = onEvent)
        AuthenticatorDialog.Scanning -> AlertDialog(
            onDismissRequest = dismiss,
            properties = secureDialog,
            confirmButton = {},
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.l), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Text(text = "Reading the QR code…")
                }
            }
        )
        is AuthenticatorDialog.ConfirmScanned -> {
            val account = dialog.account
            AlertDialog(
                onDismissRequest = dismiss,
                properties = secureDialog,
                title = { Text(text = "Add this account?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(text = account.title, style = MaterialTheme.typography.titleMedium)
                        if (account.issuer.isNotBlank() && account.accountName.isNotBlank()) Text(text = account.accountName)
                        Text(
                            text = groupedCode(account.codeAt(unixSeconds)),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                confirmButton = { TextButton(onClick = { onEvent(AuthenticatorEvent.SaveScanned) }) { Text(text = "Save") } },
                dismissButton = { TextButton(onClick = dismiss) { Text(text = "Cancel") } }
            )
        }
        is AuthenticatorDialog.Edit -> AlertDialog(
            onDismissRequest = dismiss,
            properties = secureDialog,
            title = { Text(text = "Edit account") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    AppTextField(
                        value = dialog.issuer,
                        onValueChange = { onEvent(AuthenticatorEvent.ChangeEdit(it, dialog.accountName)) },
                        label = "Issuer"
                    )
                    AppTextField(
                        value = dialog.accountName,
                        onValueChange = { onEvent(AuthenticatorEvent.ChangeEdit(dialog.issuer, it)) },
                        label = "Account name"
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = isTotpEditValid(dialog.issuer, dialog.accountName),
                    onClick = { onEvent(AuthenticatorEvent.SaveEdit) }
                ) { Text(text = "Save") }
            },
            dismissButton = { TextButton(onClick = dismiss) { Text(text = "Cancel") } }
        )
        is AuthenticatorDialog.Delete -> ConfirmDialog(
            title = "Remove ${dialog.account.title}?",
            message = "Removing this account can lock you out of ${dialog.account.title} unless 2FA is disabled there first.",
            confirmLabel = "Remove",
            onConfirm = { onEvent(AuthenticatorEvent.ConfirmDelete) },
            onDismiss = dismiss
        )
        is AuthenticatorDialog.Failed -> AlertDialog(
            onDismissRequest = dismiss,
            properties = secureDialog,
            title = { Text(text = "Account not added") },
            text = { Text(text = dialog.error.message()) },
            confirmButton = {
                if (dialog.error == ScanError.CameraPermissionDenied) {
                    // Stacked: side by side, the two labels don't fit next to Cancel on a phone.
                    Column(horizontalAlignment = Alignment.End) {
                        TextButton(onClick = {
                            dismiss()
                            onScanImage()
                        }) { Text(text = "Select from gallery") }
                        TextButton(onClick = { onEvent(AuthenticatorEvent.OpenManualEntry) }) { Text(text = "Enter setup key") }
                    }
                } else {
                    TextButton(onClick = dismiss) { Text(text = "OK") }
                }
            },
            dismissButton = if (dialog.error == ScanError.CameraPermissionDenied) {
                { TextButton(onClick = dismiss) { Text(text = "Cancel") } }
            } else {
                null
            }
        )
    }
}

@Composable
private fun MigrationPreviewDialog(batch: MigrationResult.Batch, onImport: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = secureDialog,
        title = { Text(text = if (batch.accounts.size == 1) "Import 1 account?" else "Import ${batch.accounts.size} accounts?") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.s)
            ) {
                if (batch.batchSize > 1) Text(text = "Code ${batch.batchIndex + 1} of ${batch.batchSize}")
                batch.accounts.forEach { account ->
                    Column {
                        Text(text = account.issuer.ifBlank { account.accountName }, style = MaterialTheme.typography.titleSmall)
                        if (account.issuer.isNotBlank() && account.accountName.isNotBlank()) {
                            Text(text = account.accountName, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                if (batch.skipped > 0) {
                    Text(
                        text = "${batch.skipped} more can't be imported: counter-based or unsupported codes.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onImport) { Text(text = "Import") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(text = "Cancel") } }
    )
}

private fun MigrationImportSummary.message(): String {
    val parts = mutableListOf("Imported $imported", "$duplicates already in Vaultly")
    if (unsupported > 0) parts += "$unsupported not supported (counter-based or invalid)"
    return parts.joinToString(", ") + "."
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualEntrySheet(draft: TotpDraft, onEvent: (AuthenticatorEvent) -> Unit) {
    val change = { updated: TotpDraft -> onEvent(AuthenticatorEvent.ChangeDraft(updated)) }
    val showErrors = draft.isSubmitted
    ModalBottomSheet(
        onDismissRequest = { onEvent(AuthenticatorEvent.Dismiss) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        properties = ModalBottomSheetDefaults.properties(securePolicy = SecureFlagPolicy.SecureOn)
    ) {
        Column(modifier = Modifier.imePadding()) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.l),
                verticalArrangement = Arrangement.spacedBy(Spacing.m)
            ) {
                Text(text = "Enter setup key", style = MaterialTheme.typography.titleLarge)
                AppTextField(
                    value = draft.issuer,
                    onValueChange = { change(draft.copy(issuer = it)) },
                    label = "Issuer (optional)",
                    modifier = Modifier.fillMaxWidth()
                )
                AppTextField(
                    value = draft.accountName,
                    onValueChange = { change(draft.copy(accountName = it)) },
                    label = "Account name",
                    modifier = Modifier.fillMaxWidth(),
                    error = if (showErrors) draft.accountNameError else null
                )
                PasswordTextField(
                    value = draft.secret,
                    onValueChange = { change(draft.copy(secret = it)) },
                    label = "Setup key",
                    modifier = Modifier.fillMaxWidth(),
                    error = if (showErrors) draft.secretError else null
                )
                TextButton(onClick = { change(draft.copy(isAdvancedOpen = !draft.isAdvancedOpen)) }) {
                    Text(text = "Advanced")
                    Icon(
                        imageVector = if (draft.isAdvancedOpen) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null
                    )
                }
                if (draft.isAdvancedOpen) {
                    Text(text = "Algorithm", style = MaterialTheme.typography.labelLarge)
                    Choice(
                        options = TotpAlgorithm.entries,
                        selected = draft.algorithm,
                        label = { it.name },
                        onSelect = { change(draft.copy(algorithm = it)) }
                    )
                    Text(text = "Digits", style = MaterialTheme.typography.labelLarge)
                    Choice(
                        options = TOTP_DIGITS,
                        selected = draft.digits,
                        label = { it.toString() },
                        onSelect = { change(draft.copy(digits = it)) }
                    )
                    AppTextField(
                        value = draft.period,
                        onValueChange = { change(draft.copy(period = it)) },
                        label = "Period (seconds)",
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        error = if (showErrors) draft.periodError else null
                    )
                }
            }
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.l),
                onClick = { onEvent(AuthenticatorEvent.SaveDraft) }
            ) {
                Text(text = "Save")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Choice(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
            ) {
                Text(text = label(option))
            }
        }
    }
}

private fun ScanError.message(): String = when (this) {
    ScanError.NotAuthenticator -> "This QR code is not an authenticator code."
    ScanError.CounterBased -> "Counter-based (HOTP) codes are not supported."
    ScanError.InvalidTransferCode -> "This transfer code couldn't be read. Nothing was imported."
    ScanError.InvalidCode -> "This authenticator QR code has an invalid setup key or settings."
    ScanError.NoQrCodeInImage -> "No QR code was found in this image."
    ScanError.CameraPermissionDenied ->
        "Scanning needs access to the camera. You can allow it in Settings, or select a QR image or enter the setup key instead."
}

package com.example.passwordmanager.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.example.passwordmanager.ui.theme.Spacing

/** Asks for a password, optionally twice. Dialogs are separate windows, so it sets FLAG_SECURE itself. */
@Composable
fun PasswordDialog(
    title: String,
    message: String,
    error: String?,
    confirmLabel: String,
    withConfirmation: Boolean,
    onConfirm: (password: String, confirmation: String) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
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
                error?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }
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

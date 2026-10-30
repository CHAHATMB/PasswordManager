package com.example.passwordmanager.presentation.LockScreen

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.example.passwordmanager.presentation.MIN_MASTER_PASSWORD_LENGTH
import com.example.passwordmanager.presentation.components.PasswordStrengthIndicator
import com.example.passwordmanager.presentation.components.PasswordTextField
import com.example.passwordmanager.util.PasswordStrength

@Composable
fun SetupScreen(
    state: LockState,
    onSetup: (password: String, confirmation: String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }

    LockScreenLayout(
        headline = "Welcome to Vaultly",
        message = "Create a master password. It encrypts everything in your vault and can't be recovered, " +
            "so keep it somewhere safe.",
        state = state
    ) {
        Text(
            text = "Use at least $MIN_MASTER_PASSWORD_LENGTH characters.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        PasswordTextField(
            value = password,
            onValueChange = { password = it },
            label = "Master password",
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isWorking
        )
        if (password.isNotEmpty()) {
            val strength = remember(password) { PasswordStrength.of(password) }
            PasswordStrengthIndicator(strength = strength, modifier = Modifier.fillMaxWidth())
            if (strength == PasswordStrength.Weak) {
                Text(
                    text = "This password is easy to guess. Use a longer one with mixed characters.",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        PasswordTextField(
            value = confirmation,
            onValueChange = { confirmation = it },
            label = "Confirm master password",
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isWorking
        )
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isWorking,
            // Not cleared here: a failed setup keeps the fields, and success leaves this screen.
            onClick = { onSetup(password, confirmation) }
        ) {
            Text(text = "Create vault")
        }
    }
}

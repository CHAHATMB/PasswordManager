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
import com.example.passwordmanager.presentation.components.PasswordTextField

@Composable
fun SetupScreen(
    state: LockState,
    onSetup: (password: String, confirmation: String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }

    LockScreenLayout(title = "Create master password", state = state) {
        Text(
            text = "Use at least $MIN_MASTER_PASSWORD_LENGTH characters. Your master password cannot be " +
                "recovered: if you forget it, your saved passwords are lost.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        PasswordTextField(
            value = password,
            onValueChange = { password = it },
            placeholder = "Master password",
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isWorking
        )
        PasswordTextField(
            value = confirmation,
            onValueChange = { confirmation = it },
            placeholder = "Confirm master password",
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isWorking
        )
        Button(
            enabled = !state.isWorking,
            onClick = {
                onSetup(password, confirmation)
                password = ""
                confirmation = ""
            }
        ) {
            Text(text = "Create vault")
        }
    }
}

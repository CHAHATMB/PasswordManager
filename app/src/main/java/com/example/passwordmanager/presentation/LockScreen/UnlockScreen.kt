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
import com.example.passwordmanager.presentation.components.PasswordTextField

@Composable
fun UnlockScreen(
    state: LockState,
    onUnlock: (password: String) -> Unit
) {
    var password by remember { mutableStateOf("") }

    LockScreenLayout(
        headline = "Welcome back",
        message = "Your vault is locked and encrypted on this device. Enter your master password to continue.",
        state = state
    ) {
        PasswordTextField(
            value = password,
            onValueChange = { password = it },
            label = "Master password",
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isWorking
        )
        if (state.retryInSeconds > 0) {
            Text(
                text = "Too many wrong attempts. Try again in ${state.retryInSeconds} s",
                color = MaterialTheme.colorScheme.error
            )
        }
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isWorking && password.isNotEmpty() && state.retryInSeconds == 0,
            onClick = {
                onUnlock(password)
                password = ""
            }
        ) {
            Text(text = "Unlock")
        }
    }
}

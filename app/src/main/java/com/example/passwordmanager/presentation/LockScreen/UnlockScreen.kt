package com.example.passwordmanager.presentation.LockScreen

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
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

    LockScreenLayout(title = "Unlock vault", state = state) {
        PasswordTextField(
            value = password,
            onValueChange = { password = it },
            placeholder = "Master password",
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isWorking
        )
        Button(
            enabled = !state.isWorking && password.isNotEmpty(),
            onClick = {
                onUnlock(password)
                password = ""
            }
        ) {
            Text(text = "Unlock")
        }
    }
}

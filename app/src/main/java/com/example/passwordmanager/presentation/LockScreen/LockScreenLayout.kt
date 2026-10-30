package com.example.passwordmanager.presentation.LockScreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.passwordmanager.presentation.MIN_MASTER_PASSWORD_LENGTH

@Composable
fun LockScreenLayout(
    title: String,
    state: LockState,
    content: @Composable ColumnScope.() -> Unit
) {
    // heightIn(min) gives the scrolling Column room to centre in; verticalScroll alone removes it.
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .fillMaxWidth()
                .heightIn(min = maxHeight)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            content()
            state.error?.let {
                Text(text = it.message(), color = MaterialTheme.colorScheme.error)
            }
            if (state.isWorking) {
                CircularProgressIndicator()
            }
        }
    }
}

private fun LockError.message(): String = when (this) {
    LockError.PasswordTooShort -> "Master password must be at least $MIN_MASTER_PASSWORD_LENGTH characters"
    LockError.PasswordMismatch -> "Passwords do not match"
    LockError.WrongPassword -> "Wrong master password"
    LockError.CorruptVault -> "Vault data is corrupted and cannot be opened"
    LockError.BiometricInvalidated ->
        "Biometrics disabled because your fingerprints changed. Use your master password."
    LockError.BiometricFailed ->
        "Fingerprint unlock stopped working and was turned off. Use your master password."
    LockError.Unexpected -> "Something went wrong. Please try again."
}

package com.example.passwordmanager.presentation.AddScreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.passwordmanager.data.Credential
import com.example.passwordmanager.presentation.CredentialEvent
import com.example.passwordmanager.presentation.components.AppTextField
import com.example.passwordmanager.presentation.components.AppTopBar
import com.example.passwordmanager.presentation.components.GeneratorSheet
import com.example.passwordmanager.presentation.components.PasswordStrengthIndicator
import com.example.passwordmanager.presentation.components.PasswordTextField
import com.example.passwordmanager.presentation.isCredentialFormValid
import com.example.passwordmanager.ui.theme.Spacing

/** Adds a new credential, or edits [editing] in place when it is set. */
@Composable
fun AddCredentialScreen(
    onEvent: (CredentialEvent)->Unit,
    onSaved: () -> Unit,
    editing: Credential? = null
) {
    Scaffold (
        topBar = { AppTopBar(title = if (editing == null) "Add" else "Edit") }
    ){
        TextFields(it, editing, onEvent, onSaved)
    }
}

@Composable
private fun TextFields(
    it: PaddingValues,
    editing: Credential?,
    onEvent: (CredentialEvent) -> Unit,
    onSaved: () -> Unit,
) {
    // Plain remember (not saveable) so typed passwords never land in the saved-state Bundle.
    var website by remember { mutableStateOf(editing?.website.orEmpty()) }
    var username by remember { mutableStateOf(editing?.username.orEmpty()) }
    var password by remember { mutableStateOf(editing?.password.orEmpty()) }
    var note by remember { mutableStateOf(editing?.note.orEmpty()) }
    var isGeneratorOpen by remember { mutableStateOf(false) }

    if (isGeneratorOpen) {
        GeneratorSheet(onUse = { password = it }, onDismiss = { isGeneratorOpen = false })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(it)
            .imePadding()
    ) {
        // Only the fields scroll, so Save stays visible above the keyboard.
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m)
        ) {
            AppTextField(
                value = website,
                onValueChange = { website = it },
                label = "Website",
                modifier = Modifier.fillMaxWidth()
            )
            AppTextField(
                value = username,
                onValueChange = { username = it },
                label = "Username",
                modifier = Modifier.fillMaxWidth()
            )
            PasswordTextField(
                value = password,
                onValueChange = { password = it },
                label = "Password",
                modifier = Modifier.fillMaxWidth(),
                onGenerate = { isGeneratorOpen = true }
            )
            if (password.isNotEmpty()) PasswordStrengthIndicator(password = password)
            AppTextField(
                value = note,
                onValueChange = { note = it },
                label = "Note",
                modifier = Modifier.fillMaxWidth(),
                singleLine = false
            )
        }

        Button(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.l),
            enabled = isCredentialFormValid(website, password),
            onClick = {
                onEvent(
                    if (editing == null) {
                        CredentialEvent.SaveCredential(
                            website = website,
                            username = username,
                            password = password,
                            note = note
                        )
                    } else {
                        CredentialEvent.UpdateCredential(
                            editing.copy(website = website, username = username, password = password, note = note)
                        )
                    }
                )
                onSaved()
            }) {
            Text(text = "Save")
        }
    }
}

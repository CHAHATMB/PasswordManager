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
import com.example.passwordmanager.presentation.CredentialDraft
import com.example.passwordmanager.presentation.CredentialEvent
import com.example.passwordmanager.presentation.components.AppTextField
import com.example.passwordmanager.presentation.components.AppTopBar
import com.example.passwordmanager.presentation.components.GeneratorSheet
import com.example.passwordmanager.presentation.components.PasswordStrengthIndicator
import com.example.passwordmanager.presentation.components.PasswordTextField
import com.example.passwordmanager.presentation.isCredentialFormValid
import com.example.passwordmanager.ui.theme.Spacing

/** Adds a new credential, or edits [editing] in place when it is set. The input lives in local state. */
@Composable
fun AddCredentialScreen(
    onEvent: (CredentialEvent)->Unit,
    loadPassphraseWords: suspend () -> List<String>,
    onSaved: () -> Unit,
    editing: Credential? = null
) {
    // Plain remember (not saveable) so typed passwords never land in the saved-state Bundle.
    var draft by remember { mutableStateOf(CredentialDraft(editing)) }
    AddCredentialScreen(
        draft = draft,
        onDraftChange = { draft = it },
        onEvent = onEvent,
        loadPassphraseWords = loadPassphraseWords,
        onSaved = onSaved,
        editing = editing
    )
}

/** Adds a new credential, or edits [editing] in place when it is set. */
@Composable
fun AddCredentialScreen(
    draft: CredentialDraft,
    onDraftChange: (CredentialDraft) -> Unit,
    onEvent: (CredentialEvent)->Unit,
    loadPassphraseWords: suspend () -> List<String>,
    onSaved: () -> Unit,
    editing: Credential? = null
) {
    Scaffold (
        topBar = { AppTopBar(title = if (editing == null) "Add" else "Edit") }
    ){
        TextFields(it, draft, onDraftChange, editing, onEvent, loadPassphraseWords, onSaved)
    }
}

@Composable
private fun TextFields(
    it: PaddingValues,
    draft: CredentialDraft,
    onDraftChange: (CredentialDraft) -> Unit,
    editing: Credential?,
    onEvent: (CredentialEvent) -> Unit,
    loadPassphraseWords: suspend () -> List<String>,
    onSaved: () -> Unit,
) {
    val (website, username, password, note) = draft

    if (draft.isGeneratorOpen) {
        GeneratorSheet(
            loadPassphraseWords = loadPassphraseWords,
            onUse = { onDraftChange(draft.copy(password = it)) },
            onDismiss = { onDraftChange(draft.copy(isGeneratorOpen = false)) }
        )
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
                onValueChange = { onDraftChange(draft.copy(website = it)) },
                label = "Website",
                modifier = Modifier.fillMaxWidth()
            )
            AppTextField(
                value = username,
                onValueChange = { onDraftChange(draft.copy(username = it)) },
                label = "Username",
                modifier = Modifier.fillMaxWidth()
            )
            PasswordTextField(
                value = password,
                onValueChange = { onDraftChange(draft.copy(password = it)) },
                label = "Password",
                modifier = Modifier.fillMaxWidth(),
                onGenerate = { onDraftChange(draft.copy(isGeneratorOpen = true)) }
            )
            if (password.isNotEmpty()) PasswordStrengthIndicator(password = password)
            AppTextField(
                value = note,
                onValueChange = { onDraftChange(draft.copy(note = it)) },
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

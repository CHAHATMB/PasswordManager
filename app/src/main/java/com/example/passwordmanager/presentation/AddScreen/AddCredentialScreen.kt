package com.example.passwordmanager.presentation.AddScreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.passwordmanager.data.Credential
import com.example.passwordmanager.presentation.CredentialEvent
import com.example.passwordmanager.presentation.components.GeneratorSheet
import com.example.passwordmanager.presentation.components.PasswordStrengthIndicator
import com.example.passwordmanager.presentation.components.PasswordTextField
import com.example.passwordmanager.presentation.isCredentialFormValid

/** Adds a new credential, or edits [editing] in place when it is set. */
@Composable
fun AddCredentialScreen(
    onEvent: (CredentialEvent)->Unit,
    onSaved: () -> Unit,
    editing: Credential? = null
) {
    Scaffold (
        topBar = {
            Row (modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
                .background(Color.Transparent)
                .padding(16.dp)
            ){
                Text(text = if (editing == null) "Add" else "Edit",
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        modifier = Modifier.background(MaterialTheme.colorScheme.background)
//            .heightIn(min = 600.dp, max = 700.dp)
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
            .verticalScroll(rememberScrollState())
    ) {

        OutlinedTextField(
            value = website,
            onValueChange = {
                website = it
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(corner = CornerSize(8.dp)),

            placeholder = {
                Text(text = "Website")
            },
            textStyle = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
        )
        OutlinedTextField(
            value = username, onValueChange = {
                username = it
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            placeholder = {
                Text(text = "Username")
            },
            textStyle = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        )
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PasswordTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = "Password",
                modifier = Modifier.fillMaxWidth(),
                onGenerate = { isGeneratorOpen = true }
            )
            if (password.isNotEmpty()) PasswordStrengthIndicator(password = password)
        }
        OutlinedTextField(
            value = note, onValueChange = {
                note = it
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            placeholder = {
                Text(text = "Note")
            },
            textStyle = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        )

        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Button(modifier = Modifier
                .padding(horizontal = 16.dp)
                .background(MaterialTheme.colorScheme.primary)
                .clip(
                    RoundedCornerShape(corner = CornerSize(4.dp))
                ),
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
}

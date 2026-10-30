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
import com.example.passwordmanager.presentation.CredentialEvent
import com.example.passwordmanager.presentation.components.PasswordTextField
import com.example.passwordmanager.presentation.isCredentialFormValid

@Composable
fun AddCredentialScreen(
    onEvent: (CredentialEvent)->Unit,
    onSaved: () -> Unit
) {
    Scaffold (
        topBar = {
            Row (modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
                .background(Color.Transparent)
                .padding(16.dp)
            ){
                Text(text = "Add",
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        modifier = Modifier.background(MaterialTheme.colorScheme.background)
//            .heightIn(min = 600.dp, max = 700.dp)
    ){
        TextFields(it, onEvent, onSaved)
    }
}

@Composable
private fun TextFields(
    it: PaddingValues,
    onEvent: (CredentialEvent) -> Unit,
    onSaved: () -> Unit,
) {
    // Plain remember (not saveable) so typed passwords never land in the saved-state Bundle.
    var website by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

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
        PasswordTextField(
            value = password,
            onValueChange = { password = it },
            placeholder = "Password",
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )
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
                        CredentialEvent.SaveCredential(
                            website = website,
                            username = username,
                            password = password,
                            note = note
                        )
                    )
                    onSaved()
                }) {
                Text(text = "Save")
            }
        }

    }
}

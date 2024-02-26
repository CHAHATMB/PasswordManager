package com.example.passwordmanager.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination

@Composable
fun AddCredentialScreen(
    state: CredentialState,
    navController: NavController,
    onEvent: (CredentialEvent)->Unit
) {
    Scaffold (
        topBar = {
            Row (modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
                .background(MaterialTheme.colorScheme.onPrimary)
                .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ){
                Text(text = "Password Manager",
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = { onEvent(CredentialEvent.SortCredentail) }) {
                    Icon(imageVector = Icons.Rounded.Sort, contentDescription = null, modifier = Modifier.size(35.dp), tint = MaterialTheme.colorScheme.secondary)
                }
            }
        },
        modifier = Modifier.background(MaterialTheme.colorScheme.background)
    ){
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(it)) {
            OutlinedTextField(value = state.website.value, onValueChange = {
                    state.website.value = it
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = {
                    Text(text = "Website")
                },
            textStyle = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            )
            OutlinedTextField(value = state.username.value, onValueChange = {
                state.username.value = it
            },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = {
                    Text(text = "Username")
                },
                textStyle = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            )
            OutlinedTextField(value = state.password.value, onValueChange = {
                state.password.value = it
            },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = {
                    Text(text = "Password")
                },
                textStyle = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            )
            OutlinedTextField(value = state.note.value, onValueChange = {
                state.note.value = it
            },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = {
                    Text(text = "Note")
                },
                textStyle = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            )
            Row(modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.End){
                Button(modifier = Modifier.padding(horizontal=16.dp).background(MaterialTheme.colorScheme.primary).clip(
                    RoundedCornerShape(corner = CornerSize(4.dp))
                ),
                    onClick = {
                        onEvent(
                            CredentialEvent.SaveCredential(
                                website = state.website.value,
                                username = state.username.value,
                                password = state.password.value,
                                note = state.note.value))
                        navController.navigate(NavigationItem.Home.route){
                            popUpTo(navController.graph.findStartDestination().id){
                                this@navigate.launchSingleTop = true
                            }
                        }
                    }) {
                    Text(text = "Add")
                }
            }

        }
    }
}
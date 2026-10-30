package com.example.passwordmanager.presentation.HomeScreen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.passwordmanager.data.Credential
import com.example.passwordmanager.presentation.CredentialEvent
import com.example.passwordmanager.presentation.CredentialRoutes
import com.example.passwordmanager.presentation.CredentialState
import com.example.passwordmanager.presentation.NavigationItem
import com.example.passwordmanager.presentation.components.showCopiedMessage

const val PASSWORD_MASK = "••••••••"

@Composable
fun CredentialScreen(
    state: CredentialState,
    searchText: String,
    navController: NavController,
    onEvent: (CredentialEvent)->Unit,
    onLock: () -> Unit
) {
    Scaffold (
        topBar = {
            Column {
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
                    IconButton(onClick = { onEvent(CredentialEvent.SortCredential) }) {
                        Icon(imageVector = Icons.Rounded.Sort, contentDescription = "Sort by date or website", modifier = Modifier.size(35.dp), tint = MaterialTheme.colorScheme.secondary)
                    }
                    IconButton(onClick = { navController.navigate(NavigationItem.Settings.route) }) {
                        Icon(imageVector = Icons.Rounded.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.secondary)
                    }
                    IconButton(onClick = onLock) {
                        Icon(imageVector = Icons.Rounded.Lock, contentDescription = "Lock", tint = MaterialTheme.colorScheme.secondary)
                    }
                }
                SearchField(text = searchText, onTextChange = { onEvent(CredentialEvent.OnSearchTextChange(it)) })
            }
        }
    ){
        // Padding (not contentPadding) so the list is clipped below the search field instead of
        // scrolling under it.
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .padding(8.dp),
            verticalArrangement =  Arrangement.spacedBy(16.dp)
        ){
            items(state.credentials, key = { credential -> credential.id }) { credential ->
                CredentialItem(
                    credential = credential,
                    onEvent = onEvent,
                    onClick = { navController.navigate(CredentialRoutes.detail(credential.id)) }
                )
            }
        }
    }
}

@Composable
private fun SearchField(text: String, onTextChange: (String) -> Unit) {
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        placeholder = { Text(text = "Search website or username") },
        leadingIcon = { Icon(imageVector = Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (text.isNotEmpty()) {
                IconButton(onClick = { onTextChange("") }) {
                    Icon(imageVector = Icons.Rounded.Clear, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(8.dp)
    )
}

@Composable
fun CredentialItem(credential: Credential, onEvent: (CredentialEvent) -> Unit, onClick: () -> Unit) {
    var isPasswordRevealed by remember(credential.id) { mutableStateOf(false) }
    val context = LocalContext.current
    Row(modifier= Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(10.dp))
        .clickable(onClick = onClick)
        .background(MaterialTheme.colorScheme.secondaryContainer)
        .padding(12.dp)
    ){
        Column(modifier=Modifier.weight(1f)){
            Text(text = credential.website, fontSize=18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(modifier= Modifier.height(8.dp))
            Text(text = credential.username, fontSize=18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(modifier= Modifier.height(8.dp))
            Text(
                text = if (isPasswordRevealed) credential.password else PASSWORD_MASK,
                fontSize=18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        Row(){
            IconButton(onClick = {
                onEvent(CredentialEvent.CopyPassword(credential))
                showCopiedMessage(context, "Password")
            }) {
                Icon(imageVector = Icons.Rounded.ContentCopy, contentDescription = "Copy password", modifier = Modifier.size(24.dp))
            }
            IconButton(onClick = { isPasswordRevealed = !isPasswordRevealed }) {
                Icon(
                    imageVector = if (isPasswordRevealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = if (isPasswordRevealed) "Hide password" else "Show password",
                    modifier = Modifier.size(24.dp)
                )
            }
            IconButton(onClick = {
                onEvent(CredentialEvent.FavoriteCredential(credential))
            }) {
                if( credential.isFav ) {
                    Icon(imageVector = Icons.Rounded.Favorite, contentDescription = "Remove from favourites", modifier = Modifier.size(24.dp))
                } else {
                    Icon(imageVector = Icons.Rounded.FavoriteBorder, contentDescription = "Add to favourites", modifier = Modifier.size(24.dp))
                }
            }

            IconButton(onClick = { onEvent(CredentialEvent.DeleteCredential(credential)) }) {
                Icon(imageVector = Icons.Rounded.Delete, contentDescription="Delete", modifier = Modifier.size(24.dp))
            }
        }

    }
}

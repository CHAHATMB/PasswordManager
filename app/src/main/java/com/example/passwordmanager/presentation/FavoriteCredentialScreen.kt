package com.example.passwordmanager.presentation

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController


@Composable
fun FavoriteCredentialScreen(
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
        }
    ){
        LazyColumn(contentPadding = it,
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement =  Arrangement.spacedBy(16.dp)
        ){
            items(state.credentials.size){
                FavCredentialItem( state=state, index=it, onEvent = onEvent)
            }
        }
    }
}

@Composable
fun FavCredentialItem(state: CredentialState, index: Int, onEvent: (CredentialEvent) -> Unit) {
    if( state.credentials.get(index = index).isFav ) {
        CredentialItem(state = state, index = index, lockState = false, onEvent = onEvent )
    }
}

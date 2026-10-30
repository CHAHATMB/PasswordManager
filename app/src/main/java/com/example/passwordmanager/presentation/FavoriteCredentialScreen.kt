package com.example.passwordmanager.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.example.passwordmanager.presentation.HomeScreen.CredentialList
import com.example.passwordmanager.presentation.components.AppTopBar
import com.example.passwordmanager.presentation.components.EmptyState


@Composable
fun FavoriteCredentialScreen(
    state: CredentialState,
    navController: NavController,
    onEvent: (CredentialEvent)->Unit
) {
    Scaffold (
        topBar = {
            AppTopBar(
                title = "Favorites",
                actions = {
                    IconButton(onClick = { onEvent(CredentialEvent.SortCredential) }) {
                        Icon(imageVector = Icons.AutoMirrored.Rounded.Sort, contentDescription = "Sort by date or website")
                    }
                }
            )
        }
    ){
        Box(modifier = Modifier.padding(it)) {
            if (!state.isLoaded) {
                Unit
            } else if (state.credentials.isEmpty()) {
                EmptyState(
                    icon = Icons.Rounded.FavoriteBorder,
                    title = "No favourites yet",
                    message = "Tap the heart on a password to pin it here."
                )
            } else {
                CredentialList(credentials = state.credentials, navController = navController, onEvent = onEvent)
            }
        }
    }
}

package com.example.passwordmanager.presentation.HomeScreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.example.passwordmanager.data.Credential
import com.example.passwordmanager.data.CredentialSort
import com.example.passwordmanager.presentation.CredentialEvent
import com.example.passwordmanager.presentation.CredentialRoutes
import com.example.passwordmanager.presentation.CredentialState
import com.example.passwordmanager.presentation.NavigationItem
import com.example.passwordmanager.presentation.components.AppTopBar
import com.example.passwordmanager.presentation.components.EmptyState
import com.example.passwordmanager.presentation.components.ItemRow
import com.example.passwordmanager.presentation.components.SearchField
import com.example.passwordmanager.presentation.components.showCopiedMessage
import com.example.passwordmanager.ui.theme.Spacing
import kotlinx.coroutines.delay

const val PASSWORD_MASK = "••••••••"
private const val PASSWORD_REVEAL_TIMEOUT_MS = 30_000L

@Composable
fun CredentialScreen(
    state: CredentialState,
    searchText: String,
    favoritesOnly: Boolean,
    sort: CredentialSort,
    navController: NavController,
    onEvent: (CredentialEvent)->Unit,
    onLock: () -> Unit
) {
    Scaffold (
        topBar = {
            Column {
                AppTopBar(
                    title = "Vaultly",
                    actions = {
                        IconButton(onClick = { navController.navigate(NavigationItem.Settings.route) }) {
                            Icon(imageVector = Icons.Rounded.Settings, contentDescription = "Settings")
                        }
                        IconButton(onClick = onLock) {
                            Icon(imageVector = Icons.Rounded.Lock, contentDescription = "Lock")
                        }
                    }
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.l),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SearchField(
                        text = searchText,
                        onTextChange = { onEvent(CredentialEvent.OnSearchTextChange(it)) },
                        placeholder = "Search website or username",
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { onEvent(CredentialEvent.ToggleFavoritesOnly) }) {
                        Icon(
                            imageVector = if (favoritesOnly) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = if (favoritesOnly) "Show all passwords" else "Show only favourites",
                            tint = if (favoritesOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    SortMenu(sort = sort, onSortChange = { onEvent(CredentialEvent.SetSort(it)) })
                }
            }
        }
    ){
        // Padding (not contentPadding) so the list is clipped below the search field instead of
        // scrolling under it.
        Box(modifier = Modifier.padding(it)) {
            if (!state.isLoaded) {
                Unit
            } else if (state.credentials.isEmpty()) {
                if (favoritesOnly && searchText.isBlank()) {
                    EmptyState(
                        icon = Icons.Rounded.FavoriteBorder,
                        title = "No favourites yet",
                        message = "Add one from a password's ⋮ menu."
                    )
                } else if (searchText.isBlank()) {
                    EmptyState(
                        icon = Icons.Rounded.Shield,
                        title = "Your vault is empty",
                        message = "Tap + to save your first password."
                    )
                } else {
                    EmptyState(
                        icon = Icons.Rounded.SearchOff,
                        title = "No results",
                        message = "Nothing matches “$searchText”."
                    )
                }
            } else {
                CredentialList(credentials = state.credentials, navController = navController, onEvent = onEvent)
            }
        }
    }
}

@Composable
private fun SortMenu(sort: CredentialSort, onSortChange: (CredentialSort) -> Unit) {
    var isOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { isOpen = true }) {
            Icon(imageVector = Icons.AutoMirrored.Rounded.Sort, contentDescription = "Sort")
        }
        DropdownMenu(expanded = isOpen, onDismissRequest = { isOpen = false }) {
            CredentialSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text = option.label) },
                    trailingIcon = {
                        if (option == sort) Icon(imageVector = Icons.Rounded.Check, contentDescription = "Selected")
                    },
                    onClick = {
                        isOpen = false
                        onSortChange(option)
                    }
                )
            }
        }
    }
}

private val CredentialSort.label
    get() = when (this) {
        CredentialSort.CREATED -> "Date added"
        CredentialSort.WEBSITE -> "Name (A–Z)"
        CredentialSort.MODIFIED -> "Recently modified"
    }

@Composable
private fun CredentialList(credentials: List<Credential>, navController: NavController, onEvent: (CredentialEvent) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.s)
    ) {
        items(credentials, key = { credential -> credential.id }) { credential ->
            CredentialItem(
                credential = credential,
                onEvent = onEvent,
                onClick = { navController.navigate(CredentialRoutes.detail(credential.id)) }
            )
        }
    }
}

@Composable
private fun CredentialItem(credential: Credential, onEvent: (CredentialEvent) -> Unit, onClick: () -> Unit) {
    var isPasswordRevealed by remember(credential.id) { mutableStateOf(false) }
    LaunchedEffect(isPasswordRevealed) {
        if (isPasswordRevealed) {
            delay(PASSWORD_REVEAL_TIMEOUT_MS)
            isPasswordRevealed = false
        }
    }
    var isMenuOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    ItemRow(
        title = credential.website,
        subtitle = listOf(credential.username, if (isPasswordRevealed) credential.password else PASSWORD_MASK)
            .filter { it.isNotEmpty() }
            .joinToString("\n"),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        IconButton(onClick = {
            onEvent(CredentialEvent.CopyPassword(credential))
            showCopiedMessage(context, "Password")
        }) {
            Icon(imageVector = Icons.Rounded.ContentCopy, contentDescription = "Copy password")
        }
        IconButton(onClick = { isPasswordRevealed = !isPasswordRevealed }) {
            Icon(
                imageVector = if (isPasswordRevealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                contentDescription = if (isPasswordRevealed) "Hide password" else "Show password"
            )
        }
        Box {
            IconButton(onClick = { isMenuOpen = true }) {
                Icon(imageVector = Icons.Rounded.MoreVert, contentDescription = "More actions")
            }
            DropdownMenu(expanded = isMenuOpen, onDismissRequest = { isMenuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(text = if (credential.isFav) "Remove from favourites" else "Add to favourites") },
                    leadingIcon = {
                        Icon(
                            imageVector = if (credential.isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        isMenuOpen = false
                        onEvent(CredentialEvent.FavoriteCredential(credential))
                    }
                )
                DropdownMenuItem(
                    text = { Text(text = "Delete") },
                    leadingIcon = { Icon(imageVector = Icons.Rounded.Delete, contentDescription = null) },
                    onClick = {
                        isMenuOpen = false
                        onEvent(CredentialEvent.DeleteCredential(credential))
                    }
                )
            }
        }
    }
}

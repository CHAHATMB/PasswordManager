package com.example.passwordmanager.presentation.DetailScreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.passwordmanager.data.Credential
import com.example.passwordmanager.presentation.CredentialEvent
import com.example.passwordmanager.presentation.HomeScreen.PASSWORD_MASK
import com.example.passwordmanager.presentation.components.showCopiedMessage

@Composable
fun CredentialDetailScreen(
    credential: Credential,
    onEvent: (CredentialEvent) -> Unit,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var isPasswordRevealed by remember(credential.id) { mutableStateOf(false) }
    val context = LocalContext.current
    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(55.dp)
                    .background(MaterialTheme.colorScheme.onPrimary),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = credential.website,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = { onEvent(CredentialEvent.FavoriteCredential(credential)) }) {
                    Icon(
                        imageVector = if (credential.isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (credential.isFav) "Remove from favourites" else "Add to favourites"
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(imageVector = Icons.Rounded.Edit, contentDescription = "Edit")
                }
                IconButton(onClick = onDelete) {
                    Icon(imageVector = Icons.Rounded.Delete, contentDescription = "Delete")
                }
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DetailField(label = "Website", value = credential.website)
            DetailField(label = "Username", value = credential.username) {
                IconButton(onClick = {
                    onEvent(CredentialEvent.CopyUsername(credential))
                    showCopiedMessage(context, "Username")
                }) {
                    Icon(imageVector = Icons.Rounded.ContentCopy, contentDescription = "Copy username")
                }
            }
            DetailField(label = "Password", value = if (isPasswordRevealed) credential.password else PASSWORD_MASK) {
                IconButton(onClick = { isPasswordRevealed = !isPasswordRevealed }) {
                    Icon(
                        imageVector = if (isPasswordRevealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (isPasswordRevealed) "Hide password" else "Show password"
                    )
                }
                IconButton(onClick = {
                    onEvent(CredentialEvent.CopyPassword(credential))
                    showCopiedMessage(context, "Password")
                }) {
                    Icon(imageVector = Icons.Rounded.ContentCopy, contentDescription = "Copy password")
                }
            }
            if (credential.note.isNotBlank()) {
                DetailField(label = "Note", value = credential.note)
            }
        }
    }
}

@Composable
private fun DetailField(label: String, value: String, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        actions()
    }
}

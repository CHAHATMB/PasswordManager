package com.example.passwordmanager.presentation.Authenticator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.passwordmanager.R
import com.example.passwordmanager.presentation.components.AppTopBar
import com.example.passwordmanager.presentation.components.EmptyState
import com.example.passwordmanager.presentation.components.ItemRow
import com.example.passwordmanager.presentation.components.showCopiedMessage
import com.example.passwordmanager.ui.theme.Spacing
import com.example.passwordmanager.ui.theme.extendedColors

private const val WARNING_SECONDS = 5

@Composable
fun AuthenticatorScreen(
    state: AuthenticatorState,
    dialog: AuthenticatorDialog?,
    onEvent: (AuthenticatorEvent) -> Unit,
    onScanQrCode: () -> Unit,
    onScanImage: () -> Unit
) {
    AuthenticatorDialogs(
        dialog = dialog,
        unixSeconds = state.unixSeconds,
        onEvent = onEvent,
        onScanQrCode = onScanQrCode,
        onScanImage = onScanImage
    )
    Scaffold(topBar = { AppTopBar(title = "Authenticator") }) {
        Box(modifier = Modifier.padding(it)) {
            if (!state.isLoaded) {
                Unit
            } else if (state.items.isEmpty()) {
                EmptyState(
                    icon = Icons.Rounded.Shield,
                    title = "No authenticator accounts",
                    message = "Tap + to add one by scanning its QR code or entering its setup key."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.l),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s)
                ) {
                    items(state.items, key = { item -> item.account.id }) { item ->
                        TotpRow(item = item, onEvent = onEvent)
                    }
                }
            }
        }
    }
}

@Composable
private fun TotpRow(item: TotpCodeItem, onEvent: (AuthenticatorEvent) -> Unit) {
    val account = item.account
    val context = LocalContext.current
    var isMenuOpen by remember { mutableStateOf(false) }
    ItemRow(
        title = account.title,
        subtitle = if (account.issuer.isBlank()) "" else account.accountName,
        onClick = {
            onEvent(AuthenticatorEvent.CopyCode(account))
            showCopiedMessage(context, "Code")
        },
        modifier = Modifier.fillMaxWidth(),
        // Below the names rather than beside them, so an 8-digit code doesn't squeeze the issuer.
        footer = {
            Text(
                text = groupedCode(item.code),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    ) {
        Countdown(
            secondsRemaining = item.secondsRemaining,
            period = account.period,
            modifier = Modifier.align(Alignment.CenterVertically)
        )
        Box {
            IconButton(onClick = { isMenuOpen = true }) {
                Icon(imageVector = Icons.Rounded.MoreVert, contentDescription = "More actions")
            }
            DropdownMenu(expanded = isMenuOpen, onDismissRequest = { isMenuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(text = "Edit") },
                    leadingIcon = { Icon(imageVector = Icons.Rounded.Edit, contentDescription = null) },
                    onClick = {
                        isMenuOpen = false
                        onEvent(AuthenticatorEvent.StartEdit(account))
                    }
                )
                DropdownMenuItem(
                    text = { Text(text = "Delete") },
                    leadingIcon = { Icon(imageVector = Icons.Rounded.Delete, contentDescription = null) },
                    onClick = {
                        isMenuOpen = false
                        onEvent(AuthenticatorEvent.StartDelete(account))
                    }
                )
            }
        }
    }
}

@Composable
private fun Countdown(secondsRemaining: Int, period: Int, modifier: Modifier = Modifier) {
    val color = if (secondsRemaining <= WARNING_SECONDS) MaterialTheme.extendedColors.warning else MaterialTheme.colorScheme.primary
    val description = pluralStringResource(R.plurals.totp_new_code_in, secondsRemaining, secondsRemaining)
    Box(
        modifier = modifier
            .size(36.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = { secondsRemaining / period.toFloat() },
            modifier = Modifier.fillMaxSize(),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeWidth = 3.dp
        )
        Text(text = secondsRemaining.toString(), style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/** `123 456` or `1234 5678`, easier to read and type than one block of digits. */
internal fun groupedCode(code: String): String = code.chunked((code.length + 1) / 2).joinToString(" ")

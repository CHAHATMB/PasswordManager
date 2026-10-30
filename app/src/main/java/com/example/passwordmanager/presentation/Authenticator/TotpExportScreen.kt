package com.example.passwordmanager.presentation.Authenticator

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.passwordmanager.presentation.components.AppTopBar
import com.example.passwordmanager.presentation.components.ConfirmDialog
import com.example.passwordmanager.presentation.components.EmptyState
import com.example.passwordmanager.presentation.components.PasswordDialog
import com.example.passwordmanager.ui.theme.Spacing
import com.example.passwordmanager.ui.theme.extendedColors

@Composable
fun TotpExportScreen(step: TotpExportStep, onEvent: (TotpExportEvent) -> Unit, onClose: () -> Unit) {
    Scaffold(
        topBar = {
            AppTopBar(
                title = "Export accounts",
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
            when (step) {
                TotpExportStep.Warning -> ConfirmDialog(
                    title = "Export accounts?",
                    message = "Each account is shown as a QR code that Google or Microsoft Authenticator can scan. " +
                        "Anyone who sees these codes can copy your 2FA accounts, so make sure no one is watching.",
                    confirmLabel = "Continue",
                    onConfirm = { onEvent(TotpExportEvent.ConfirmWarning) },
                    onDismiss = onClose
                )
                is TotpExportStep.Password -> PasswordDialog(
                    title = "Enter master password",
                    message = "Confirm it's you to show the QR codes.",
                    error = if (step.isWrongPassword) "Wrong master password." else null,
                    confirmLabel = "Show codes",
                    withConfirmation = false,
                    onConfirm = { password, _ -> onEvent(TotpExportEvent.VerifyPassword(password)) },
                    onDismiss = onClose
                )
                TotpExportStep.Verifying -> CircularProgressIndicator()
                TotpExportStep.NoAccounts -> EmptyState(
                    icon = Icons.Rounded.Shield,
                    title = "No authenticator accounts",
                    message = "There is nothing to export yet."
                )
                is TotpExportStep.Showing -> ExportPage(page = step.page, onEvent = onEvent)
            }
        }
    }
}

@Composable
private fun ExportPage(page: TotpExportPage, onEvent: (TotpExportEvent) -> Unit) {
    val account = page.account
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.m)
    ) {
        Text(text = "${page.index + 1} of ${page.count}", style = MaterialTheme.typography.labelLarge)
        Text(text = account.title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (account.issuer.isNotBlank() && account.accountName.isNotBlank()) {
            Text(text = account.accountName, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
        Box(modifier = Modifier.size(QR_SIZE), contentAlignment = Alignment.Center) {
            val qrCode = page.qrCode
            if (qrCode == null) {
                CircularProgressIndicator()
            } else {
                Image(
                    bitmap = qrCode.asImageBitmap(),
                    contentDescription = "QR code for ${account.title}",
                    filterQuality = FilterQuality.None,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        if (page.hasCompatibilityWarning) CompatibilityWarning()
        TextButton(onClick = { onEvent(TotpExportEvent.ToggleKey) }) {
            Text(text = if (page.isKeyRevealed) "Hide setup key" else "Show setup key")
        }
        if (page.isKeyRevealed) {
            Text(
                text = page.groupedKey,
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            OutlinedButton(
                onClick = { onEvent(TotpExportEvent.Previous) },
                enabled = page.index > 0,
                modifier = Modifier.weight(1f)
            ) { Text(text = "Previous") }
            Button(
                onClick = { onEvent(TotpExportEvent.Next) },
                enabled = page.index < page.count - 1,
                modifier = Modifier.weight(1f)
            ) { Text(text = "Next") }
        }
    }
}

@Composable
private fun CompatibilityWarning() {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.extendedColors.warning,
        contentColor = MaterialTheme.extendedColors.onWarning
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = Icons.Rounded.Warning, contentDescription = null)
            Text(text = "May not work in Google or Microsoft Authenticator", style = MaterialTheme.typography.labelLarge)
        }
    }
}

private val QR_SIZE = 264.dp

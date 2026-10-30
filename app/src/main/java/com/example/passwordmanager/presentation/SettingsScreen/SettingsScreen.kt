package com.example.passwordmanager.presentation.SettingsScreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.example.passwordmanager.data.settings.AutoLockTimeout
import com.example.passwordmanager.presentation.components.AppTopBar
import com.example.passwordmanager.presentation.components.SectionHeader
import com.example.passwordmanager.ui.theme.Spacing

@Composable
fun SettingsScreen(
    state: SettingsState,
    isBiometricAvailable: Boolean,
    onAutoLockTimeoutChange: (AutoLockTimeout) -> Unit,
    onBiometricToggle: (enabled: Boolean) -> Unit,
    onBackupEvent: (BackupEvent) -> Unit,
    onPickBackupFile: (BackupPicker) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(title = "Settings")
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = Spacing.l, end = Spacing.l, bottom = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.s)
        ) {
            SectionHeader(text = "Auto-lock")
            Column(modifier = Modifier.selectableGroup()) {
                AutoLockTimeout.entries.forEach { timeout ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = timeout == state.autoLockTimeout,
                                onClick = { onAutoLockTimeoutChange(timeout) },
                                role = Role.RadioButton
                            )
                            .padding(vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = timeout == state.autoLockTimeout, onClick = null)
                        Text(
                            text = timeout.label(),
                            modifier = Modifier.padding(start = Spacing.m),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
            // Also shown while enabled, so biometrics can be turned off after the last fingerprint is removed.
            if (isBiometricAvailable || state.isBiometricEnabled) {
                SectionHeader(text = "Biometrics")
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Fingerprint unlock",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Switch(checked = state.isBiometricEnabled, onCheckedChange = onBiometricToggle)
                }
                if (state.isBiometricError) {
                    Text(
                        text = "Couldn't turn on fingerprint unlock. Please try again.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            BackupSection(dialog = state.backupDialog, onEvent = onBackupEvent, onPick = onPickBackupFile)
        }
    }
}

private fun AutoLockTimeout.label(): String = when (this) {
    AutoLockTimeout.IMMEDIATELY -> "Immediately"
    AutoLockTimeout.SECONDS_30 -> "After 30 seconds"
    AutoLockTimeout.MINUTE_1 -> "After 1 minute"
    AutoLockTimeout.MINUTES_5 -> "After 5 minutes"
    AutoLockTimeout.MINUTES_15 -> "After 15 minutes"
}

package com.example.passwordmanager.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.passwordmanager.ui.theme.Spacing
import com.example.passwordmanager.ui.theme.extendedColors
import com.example.passwordmanager.util.PasswordStrength

@Composable
fun PasswordStrengthIndicator(password: String, modifier: Modifier = Modifier) {
    PasswordStrengthIndicator(strength = remember(password) { PasswordStrength.of(password) }, modifier = modifier)
}

@Composable
fun PasswordStrengthIndicator(strength: PasswordStrength, modifier: Modifier = Modifier, bits: Double? = null) {
    val color = strength.color()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        LinearProgressIndicator(
            progress = { (strength.ordinal + 1f) / PasswordStrength.entries.size },
            modifier = Modifier.fillMaxWidth(),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        val label = if (bits == null) "Strength: ${strength.name}" else "Strength: ${strength.name} · ${bits.toInt()} bits"
        Text(text = label, color = color, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PasswordStrength.color(): Color = when (this) {
    PasswordStrength.Weak -> MaterialTheme.colorScheme.error
    PasswordStrength.Fair -> MaterialTheme.extendedColors.warning
    PasswordStrength.Good, PasswordStrength.Strong -> MaterialTheme.extendedColors.success
}

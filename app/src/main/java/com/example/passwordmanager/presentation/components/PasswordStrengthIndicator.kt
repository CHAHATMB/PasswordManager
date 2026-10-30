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
import androidx.compose.ui.unit.dp
import com.example.passwordmanager.util.PasswordStrength

@Composable
fun PasswordStrengthIndicator(password: String, modifier: Modifier = Modifier) {
    val strength = remember(password) { PasswordStrength.of(password) }
    val color = strength.color()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LinearProgressIndicator(
            progress = { (strength.ordinal + 1f) / PasswordStrength.entries.size },
            modifier = Modifier.fillMaxWidth(),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Text(text = "Strength: ${strength.name}", color = color, style = MaterialTheme.typography.bodySmall)
    }
}

private fun PasswordStrength.color(): Color = when (this) {
    PasswordStrength.Weak -> Color(0xFFC62828)
    PasswordStrength.Fair -> Color(0xFFEF6C00)
    PasswordStrength.Good -> Color(0xFF9E9D24)
    PasswordStrength.Strong -> Color(0xFF2E7D32)
}

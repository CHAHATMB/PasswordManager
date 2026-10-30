package com.example.passwordmanager.presentation.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

@Composable
fun PasswordTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onGenerate: (() -> Unit)? = null
) {
    var isRevealed by remember { mutableStateOf(false) }
    AppTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        enabled = enabled,
        visualTransformation = if (isRevealed) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrect = false),
        trailingIcon = {
            Row {
                if (onGenerate != null) {
                    IconButton(onClick = onGenerate, enabled = enabled) {
                        Icon(imageVector = Icons.Rounded.Autorenew, contentDescription = "Generate password")
                    }
                }
                IconButton(onClick = { isRevealed = !isRevealed }) {
                    Icon(
                        imageVector = if (isRevealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (isRevealed) "Hide password" else "Show password"
                    )
                }
            }
        }
    )
}

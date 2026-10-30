package com.example.passwordmanager.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.SecureFlagPolicy
import com.example.passwordmanager.util.CharacterClass
import com.example.passwordmanager.util.GeneratorOptions
import com.example.passwordmanager.util.MAX_GENERATED_LENGTH
import com.example.passwordmanager.util.MIN_GENERATED_LENGTH
import com.example.passwordmanager.util.PasswordGenerator
import java.security.SecureRandom
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratorSheet(onUse: (String) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val generator = remember { PasswordGenerator(SecureRandom()) }
    var options by remember { mutableStateOf(GeneratorOptions()) }
    var preview by remember { mutableStateOf(generator.generate(options)) }
    val update = { newOptions: GeneratorOptions ->
        options = newOptions
        preview = generator.generate(newOptions)
    }

    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss,
        properties = ModalBottomSheetDefaults.properties(securePolicy = SecureFlagPolicy.SecureOn)
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = "Generate password", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Text(text = preview, fontFamily = FontFamily.Monospace, fontSize = 18.sp)
            PasswordStrengthIndicator(password = preview)

            Text(text = "Length: ${options.length}")
            Slider(
                value = options.length.toFloat(),
                onValueChange = {
                    val length = it.roundToInt()
                    if (length != options.length) update(options.copy(length = length))
                },
                valueRange = MIN_GENERATED_LENGTH.toFloat()..MAX_GENERATED_LENGTH.toFloat(),
                steps = MAX_GENERATED_LENGTH - MIN_GENERATED_LENGTH - 1
            )

            CharacterClass.entries.forEach { characterClass ->
                val isSelected = characterClass in options.classes
                OptionRow(
                    label = characterClass.label(),
                    checked = isSelected,
                    // The generator rejects an empty selection, so the last class stays on.
                    enabled = !(isSelected && options.classes.size == 1),
                    onCheckedChange = { checked ->
                        update(options.copy(classes = if (checked) options.classes + characterClass else options.classes - characterClass))
                    }
                )
            }
            OptionRow(
                label = "Avoid ambiguous (0 O o l 1 I |)",
                checked = options.avoidAmbiguous,
                onCheckedChange = { update(options.copy(avoidAmbiguous = it)) }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End)
            ) {
                OutlinedButton(onClick = { update(options) }) {
                    Text(text = "Regenerate")
                }
                Button(onClick = {
                    onUse(preview)
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                }) {
                    Text(text = "Use")
                }
            }
        }
    }
}

@Composable
private fun OptionRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

private fun CharacterClass.label(): String = when (this) {
    CharacterClass.Uppercase -> "Uppercase (A-Z)"
    CharacterClass.Lowercase -> "Lowercase (a-z)"
    CharacterClass.Digits -> "Digits (0-9)"
    CharacterClass.Symbols -> "Symbols (!@#…)"
}

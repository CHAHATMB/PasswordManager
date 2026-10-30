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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.window.SecureFlagPolicy
import com.example.passwordmanager.ui.theme.Spacing
import com.example.passwordmanager.util.CharacterClass
import com.example.passwordmanager.util.EFF_WORDLIST_SIZE
import com.example.passwordmanager.util.GeneratorOptions
import com.example.passwordmanager.util.MAX_GENERATED_LENGTH
import com.example.passwordmanager.util.MAX_PASSPHRASE_WORDS
import com.example.passwordmanager.util.MIN_GENERATED_LENGTH
import com.example.passwordmanager.util.MIN_PASSPHRASE_WORDS
import com.example.passwordmanager.util.PassphraseGenerator
import com.example.passwordmanager.util.PassphraseOptions
import com.example.passwordmanager.util.PasswordGenerator
import com.example.passwordmanager.util.PasswordStrength
import com.example.passwordmanager.util.Separator
import com.example.passwordmanager.util.entropyBits
import java.io.IOException
import java.security.SecureRandom
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratorSheet(loadPassphraseWords: suspend () -> List<String>, onUse: (String) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // The hide animation outlives the click; by then onUse has recomposed the caller with a new onDismiss.
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val random = remember { SecureRandom() }
    val generator = remember { PasswordGenerator(random) }
    var options by remember { mutableStateOf(GeneratorOptions()) }
    var preview by remember { mutableStateOf(generator.generate(options)) }
    val update = { newOptions: GeneratorOptions ->
        options = newOptions
        preview = generator.generate(newOptions)
    }

    var isPassphrase by remember { mutableStateOf(false) }
    var passphraseGenerator by remember { mutableStateOf<PassphraseGenerator?>(null) }
    var wordlistFailed by remember { mutableStateOf(false) }
    var passphraseOptions by remember { mutableStateOf(PassphraseOptions()) }
    var passphrase by remember { mutableStateOf<String?>(null) }
    val updatePassphrase = { newOptions: PassphraseOptions ->
        passphraseOptions = newOptions
        passphrase = passphraseGenerator?.generate(newOptions)
    }
    LaunchedEffect(isPassphrase) {
        if (isPassphrase && passphraseGenerator == null) {
            try {
                passphraseGenerator = PassphraseGenerator(random, loadPassphraseWords())
                updatePassphrase(passphraseOptions)
            } catch (e: IOException) {
                wordlistFailed = true
            }
        }
    }
    val current = if (isPassphrase) passphrase else preview

    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss,
        properties = ModalBottomSheetDefaults.properties(securePolicy = SecureFlagPolicy.SecureOn)
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Spacing.xl, vertical = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.s)
        ) {
            Text(text = "Generate password", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            OptionRow(label = "Passphrase", checked = isPassphrase, onCheckedChange = { isPassphrase = it })

            if (isPassphrase) {
                PassphraseControls(
                    passphrase = passphrase,
                    wordlistFailed = wordlistFailed,
                    options = passphraseOptions,
                    onOptionsChange = updatePassphrase
                )
            } else {
                Text(text = preview, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
                PasswordStrengthIndicator(password = preview)

                Text(text = "Length: ${options.length}", style = MaterialTheme.typography.bodyMedium)
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
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.l, Alignment.End)
            ) {
                OutlinedButton(
                    onClick = { if (isPassphrase) updatePassphrase(passphraseOptions) else update(options) },
                    enabled = current != null
                ) {
                    Text(text = "Regenerate")
                }
                Button(
                    onClick = {
                        current?.let(onUse)
                        scope.launch { sheetState.hide() }.invokeOnCompletion { currentOnDismiss() }
                    },
                    enabled = current != null
                ) {
                    Text(text = "Use")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PassphraseControls(
    passphrase: String?,
    wordlistFailed: Boolean,
    options: PassphraseOptions,
    onOptionsChange: (PassphraseOptions) -> Unit
) {
    when {
        passphrase != null -> {
            Text(text = passphrase, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
            val bits = options.entropyBits(EFF_WORDLIST_SIZE)
            PasswordStrengthIndicator(strength = PasswordStrength.fromBits(bits), bits = bits)
        }
        wordlistFailed -> Text(
            text = "Couldn't load the wordlist. Turn off Passphrase to generate a password.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
        else -> Text(text = "Loading wordlist…", style = MaterialTheme.typography.bodyMedium)
    }

    Text(text = "Words: ${options.wordCount}", style = MaterialTheme.typography.bodyMedium)
    Slider(
        value = options.wordCount.toFloat(),
        onValueChange = {
            val wordCount = it.roundToInt()
            if (wordCount != options.wordCount) onOptionsChange(options.copy(wordCount = wordCount))
        },
        valueRange = MIN_PASSPHRASE_WORDS.toFloat()..MAX_PASSPHRASE_WORDS.toFloat(),
        steps = MAX_PASSPHRASE_WORDS - MIN_PASSPHRASE_WORDS - 1
    )

    Text(text = "Separator", style = MaterialTheme.typography.bodyMedium)
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        Separator.entries.forEachIndexed { index, separator ->
            SegmentedButton(
                selected = separator == options.separator,
                onClick = { onOptionsChange(options.copy(separator = separator)) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = Separator.entries.size)
            ) {
                Text(text = separator.label())
            }
        }
    }

    OptionRow(
        label = "Capitalise words",
        checked = options.capitalise,
        onCheckedChange = { onOptionsChange(options.copy(capitalise = it)) }
    )
    OptionRow(
        label = "Add a digit",
        checked = options.addDigit,
        onCheckedChange = { onOptionsChange(options.copy(addDigit = it)) }
    )
    Text(
        text = "Wordlist: EFF (CC BY 3.0)",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
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

private fun Separator.label(): String = when (this) {
    Separator.Hyphen -> "-"
    Separator.Space -> "Space"
    Separator.Period -> "."
    Separator.Underscore -> "_"
}

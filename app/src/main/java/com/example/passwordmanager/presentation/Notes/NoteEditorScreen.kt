package com.example.passwordmanager.presentation.Notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.example.passwordmanager.presentation.components.AppTopBar
import com.example.passwordmanager.ui.theme.Spacing
import com.example.passwordmanager.util.NOTE_BODY_MAX_CHARS

private const val BODY_COUNTER_FROM_CHARS = 90_000
private val sentenceKeyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)

/** [onClose] saves a changed note (Save, the back arrow and system Back all do this). */
@Composable
fun NoteEditorScreen(
    editor: NoteEditor,
    onEvent: (NotesEvent) -> Unit,
    onClose: () -> Unit,
    onDelete: () -> Unit
) {
    BackHandler(onBack = onClose)
    var isMenuOpen by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            AppTopBar(
                title = "",
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onClose) {
                        Icon(imageVector = Icons.Rounded.Check, contentDescription = "Save")
                    }
                    if (editor.original != null) {
                        Box {
                            IconButton(onClick = { isMenuOpen = true }) {
                                Icon(imageVector = Icons.Rounded.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(expanded = isMenuOpen, onDismissRequest = { isMenuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(text = "Delete") },
                                    leadingIcon = { Icon(imageVector = Icons.Rounded.Delete, contentDescription = null) },
                                    onClick = {
                                        isMenuOpen = false
                                        onDelete()
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) {
        val borderless = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        )
        val bodyFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { if (editor.original == null) bodyFocus.requestFocus() }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .padding(horizontal = Spacing.xs)
        ) {
            TextField(
                value = editor.title,
                onValueChange = { title -> onEvent(NotesEvent.ChangeTitle(title)) },
                textStyle = MaterialTheme.typography.titleLarge,
                placeholder = { Text(text = "Title", style = MaterialTheme.typography.titleLarge) },
                singleLine = true,
                keyboardOptions = sentenceKeyboard.copy(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { bodyFocus.requestFocus() }),
                colors = borderless,
                modifier = Modifier.fillMaxWidth()
            )
            TextField(
                value = editor.body,
                onValueChange = { body -> onEvent(NotesEvent.ChangeBody(body)) },
                placeholder = { Text(text = "Note") },
                keyboardOptions = sentenceKeyboard,
                supportingText = if (editor.body.length >= BODY_COUNTER_FROM_CHARS) {
                    { Text(text = "${editor.body.length} / $NOTE_BODY_MAX_CHARS") }
                } else {
                    null
                },
                colors = borderless,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .focusRequester(bodyFocus)
            )
        }
    }
}

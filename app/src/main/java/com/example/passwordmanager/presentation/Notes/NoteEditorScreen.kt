package com.example.passwordmanager.presentation.Notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.example.passwordmanager.presentation.components.AppTextField
import com.example.passwordmanager.presentation.components.AppTopBar
import com.example.passwordmanager.ui.theme.Spacing
import com.example.passwordmanager.util.NOTE_BODY_MAX_CHARS

private const val BODY_COUNTER_FROM_CHARS = 90_000
private val sentenceKeyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)

/** [onClose] saves a changed note (Save, the back arrow and system Back all do this). */
@Composable
fun NoteEditorScreen(
    editor: NoteEditor,
    autoTitle: (body: String) -> String,
    onEvent: (NotesEvent) -> Unit,
    onClose: () -> Unit,
    onDelete: () -> Unit
) {
    BackHandler(onBack = onClose)
    var isMenuOpen by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            AppTopBar(
                title = if (editor.original == null) "New note" else "Edit note",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .padding(horizontal = Spacing.l, vertical = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.s)
        ) {
            AppTextField(
                value = editor.title,
                onValueChange = { title -> onEvent(NotesEvent.ChangeTitle(title)) },
                label = "Title",
                placeholder = if (editor.title.isEmpty()) autoTitle(editor.body) else null,
                keyboardOptions = sentenceKeyboard,
                modifier = Modifier.fillMaxWidth()
            )
            AppTextField(
                value = editor.body,
                onValueChange = { body -> onEvent(NotesEvent.ChangeBody(body)) },
                label = "Note",
                singleLine = false,
                keyboardOptions = sentenceKeyboard,
                supportingText = if (editor.body.length >= BODY_COUNTER_FROM_CHARS) {
                    "${editor.body.length} / $NOTE_BODY_MAX_CHARS"
                } else {
                    null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}

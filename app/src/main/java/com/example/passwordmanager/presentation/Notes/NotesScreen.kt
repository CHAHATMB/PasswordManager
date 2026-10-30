package com.example.passwordmanager.presentation.Notes

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.passwordmanager.data.notes.NoteSummary
import com.example.passwordmanager.presentation.components.AppTopBar
import com.example.passwordmanager.presentation.components.EmptyState
import com.example.passwordmanager.presentation.components.ItemRow
import com.example.passwordmanager.presentation.components.SearchField
import com.example.passwordmanager.ui.theme.Spacing

private val WHITESPACE = Regex("\\s+")

@Composable
fun NotesScreen(
    state: NotesState,
    searchText: String,
    onEvent: (NotesEvent) -> Unit,
    onOpen: (id: Int) -> Unit
) {
    Scaffold(
        topBar = {
            Column {
                AppTopBar(title = "Notes")
                SearchField(
                    text = searchText,
                    onTextChange = { onEvent(NotesEvent.OnSearchTextChange(it)) },
                    placeholder = "Search notes",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.l)
                )
            }
        }
    ) {
        Box(modifier = Modifier.padding(it)) {
            if (!state.isLoaded) {
                Unit
            } else if (state.notes.isEmpty()) {
                if (searchText.isBlank()) {
                    EmptyState(
                        icon = Icons.AutoMirrored.Rounded.StickyNote2,
                        title = "No notes yet",
                        message = "Tap + to write your first note."
                    )
                } else {
                    EmptyState(
                        icon = Icons.Rounded.SearchOff,
                        title = "No results",
                        message = "Nothing matches “$searchText”."
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.l),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s)
                ) {
                    items(state.notes, key = { note -> note.id }) { note ->
                        NoteItem(note = note, onClick = { onOpen(note.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteItem(note: NoteSummary, onClick: () -> Unit) {
    val preview = remember(note.preview) { note.preview.trim().replace(WHITESPACE, " ") }
    val updated = remember(note.updatedAt) {
        DateUtils.getRelativeTimeSpanString(
            note.updatedAt,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        ).toString()
    }
    ItemRow(
        title = note.title,
        subtitle = preview,
        subtitleMaxLines = 1,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        footer = {
            Text(text = updated, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    )
}

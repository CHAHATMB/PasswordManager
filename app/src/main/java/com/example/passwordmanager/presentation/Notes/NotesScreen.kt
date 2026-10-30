package com.example.passwordmanager.presentation.Notes

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.passwordmanager.data.notes.NoteSummary
import com.example.passwordmanager.data.settings.NotesLayout
import com.example.passwordmanager.presentation.components.AppTopBar
import com.example.passwordmanager.presentation.components.EmptyState
import com.example.passwordmanager.presentation.components.SearchField
import com.example.passwordmanager.ui.theme.Spacing

private val GRID_CELL_MIN_WIDTH = 160.dp
private const val GRID_PREVIEW_LINES = 8
private const val LIST_PREVIEW_LINES = 2
private const val TITLE_MAX_LINES = 2
/** Three or more line breaks (blank lines may hold spaces), so a card shows text instead of empty space. */
private val BLANK_LINES = Regex("(\\s*\\n){3,}")

@Composable
fun NotesScreen(
    state: NotesState,
    searchText: String,
    layout: NotesLayout,
    onEvent: (NotesEvent) -> Unit,
    onOpen: (id: Int) -> Unit
) {
    var isSearchOpen by rememberSaveable { mutableStateOf(searchText.isNotEmpty()) }
    val closeSearch = {
        isSearchOpen = false
        onEvent(NotesEvent.OnSearchTextChange(""))
    }
    BackHandler(enabled = isSearchOpen, onBack = closeSearch)
    Scaffold(
        topBar = {
            Column {
                AppTopBar(
                    title = "Notes",
                    actions = {
                        if (!isSearchOpen) {
                            IconButton(onClick = { isSearchOpen = true }) {
                                Icon(imageVector = Icons.Rounded.Search, contentDescription = "Search notes")
                            }
                        }
                        when (layout) {
                            NotesLayout.GRID -> IconButton(onClick = { onEvent(NotesEvent.ChangeLayout(NotesLayout.LIST)) }) {
                                Icon(imageVector = Icons.Rounded.ViewAgenda, contentDescription = "List view")
                            }
                            NotesLayout.LIST -> IconButton(onClick = { onEvent(NotesEvent.ChangeLayout(NotesLayout.GRID)) }) {
                                Icon(imageVector = Icons.Rounded.GridView, contentDescription = "Grid view")
                            }
                        }
                    }
                )
                if (isSearchOpen) {
                    val searchFocus = remember { FocusRequester() }
                    LaunchedEffect(Unit) { searchFocus.requestFocus() }
                    Row(
                        modifier = Modifier.padding(start = Spacing.l, end = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SearchField(
                            text = searchText,
                            onTextChange = { onEvent(NotesEvent.OnSearchTextChange(it)) },
                            placeholder = "Search notes",
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(searchFocus)
                        )
                        IconButton(onClick = closeSearch) {
                            Icon(imageVector = Icons.Rounded.Close, contentDescription = "Close search")
                        }
                    }
                }
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
                when (layout) {
                    NotesLayout.GRID -> LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Adaptive(GRID_CELL_MIN_WIDTH),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(Spacing.l),
                        verticalItemSpacing = Spacing.s,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s)
                    ) {
                        items(state.notes, key = { note -> note.id }) { note ->
                            NoteCard(note = note, previewMaxLines = GRID_PREVIEW_LINES, onClick = { onOpen(note.id) })
                        }
                    }
                    NotesLayout.LIST -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(Spacing.l),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s)
                    ) {
                        items(state.notes, key = { note -> note.id }) { note ->
                            NoteCard(note = note, previewMaxLines = LIST_PREVIEW_LINES, onClick = { onOpen(note.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteCard(note: NoteSummary, previewMaxLines: Int, onClick: () -> Unit) {
    val preview = remember(note.preview) { note.preview.trim().replace(BLANK_LINES, "\n\n") }
    val updated = remember(note.updatedAt) {
        DateUtils.getRelativeTimeSpanString(
            note.updatedAt,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        ).toString()
    }
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = note.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = TITLE_MAX_LINES,
                overflow = TextOverflow.Ellipsis
            )
            if (preview.isNotEmpty()) {
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = previewMaxLines,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = updated,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

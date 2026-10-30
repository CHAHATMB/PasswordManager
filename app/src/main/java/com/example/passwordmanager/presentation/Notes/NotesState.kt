package com.example.passwordmanager.presentation.Notes

import com.example.passwordmanager.data.notes.Note
import com.example.passwordmanager.data.notes.NoteSummary

data class NotesState(
    val notes: List<NoteSummary> = emptyList(),
    /** False until the first database emission, so an empty list isn't mistaken for no notes. */
    val isLoaded: Boolean = false
)

/**
 * The open editor's input. [original] is the stored note, null for a new one. While [isTitleAuto], the title
 * follows the body; it is true for a new note and whenever the title is cleared.
 */
data class NoteEditor(val original: Note?, val title: String, val body: String, val isTitleAuto: Boolean) {
    constructor(note: Note?) : this(note, note?.title.orEmpty(), note?.body.orEmpty(), isTitleAuto = note == null)

    /**
     * The note to save, or null when there is nothing to save: a new note with a blank title and body,
     * or an unchanged one. A blank title becomes [autoTitle]; a typed title is kept as it is.
     */
    fun toNoteToSave(now: Long, autoTitle: (body: String) -> String): Note? {
        val title = title.trim()
        if (original == null && title.isEmpty() && body.isBlank()) return null
        if (original != null && title == original.title && body == original.body) return null
        val savedTitle = title.ifEmpty { autoTitle(body) }
        return original?.copy(title = savedTitle, body = body, updatedAt = now)
            ?: Note(title = savedTitle, body = body, createdAt = now, updatedAt = now)
    }
}

sealed interface NotesEvent {
    data class OnSearchTextChange(val text: String) : NotesEvent
    data class ChangeTitle(val title: String) : NotesEvent
    data class ChangeBody(val body: String) : NotesEvent
    /** Saves the editor's note if it changed, then closes the editor. */
    data object CloseEditor : NotesEvent
    /** Deletes the editor's stored note and closes the editor. */
    data object DeleteNote : NotesEvent
    data class RestoreNote(val note: Note) : NotesEvent
}

package com.example.passwordmanager.presentation.Notes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.passwordmanager.PasswordManagerApp
import com.example.passwordmanager.data.notes.Note
import com.example.passwordmanager.data.notes.NoteRepository
import com.example.passwordmanager.data.vault.VaultException
import com.example.passwordmanager.data.vault.VaultState
import com.example.passwordmanager.presentation.NoteRoutes
import com.example.passwordmanager.util.NOTE_BODY_MAX_CHARS
import com.example.passwordmanager.util.NOTE_TITLE_MAX_CHARS
import com.example.passwordmanager.util.deriveNoteTitle
import com.example.passwordmanager.util.limitLength
import java.time.Clock
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModel(
    private val repository: NoteRepository,
    private val vaultState: StateFlow<VaultState>,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val locale: () -> Locale = Locale::getDefault
) : ViewModel() {

    private val _searchText = MutableStateFlow("")
    val searchText = _searchText.asStateFlow()

    val state = _searchText
        .flatMapLatest { repository.search(it) }
        .map { NotesState(notes = it, isLoaded = true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NotesState())

    private val _deletedNotes = Channel<Note>(Channel.BUFFERED)
    /** Each note right after it is deleted, so the UI can offer Undo. */
    val deletedNotes = _deletedNotes.receiveAsFlow()

    /**
     * The open editor, or null while none is open. Compose state like
     * [com.example.passwordmanager.presentation.CredentialViewModel.addDraft]: it survives a configuration
     * change without the note going into the Bundle, and the text fields read it directly.
     */
    var editor by mutableStateOf<NoteEditor?>(null)
        private set

    init {
        // A half-typed note or a pending Undo must not outlive the session.
        viewModelScope.launch {
            vaultState.collect { state ->
                if (state !is VaultState.Unlocked) {
                    editor = null
                    while (_deletedNotes.tryReceive().isSuccess) Unit
                }
            }
        }
    }

    /** The title a blank title field becomes on save. */
    fun autoTitle(body: String): String = deriveNoteTitle(body, LocalDate.now(clock), locale())

    /**
     * Opens the editor for note [id], or for a new note with [NoteRoutes.NEW_NOTE_ID]. Keeps an editor
     * already open for the same note, so a configuration change doesn't lose the input. Returns false
     * when the note no longer exists or the vault is locked.
     */
    suspend fun openEditor(id: Int): Boolean {
        val open = editor
        if (open != null && (open.original?.id ?: NoteRoutes.NEW_NOTE_ID) == id) return true
        if (id == NoteRoutes.NEW_NOTE_ID) {
            editor = NoteEditor(null)
            return true
        }
        val session = vaultState.value
        val note = try {
            repository.getById(id)
        } catch (e: VaultException.Locked) {
            null
        }
        if (note == null || vaultState.value !== session) return false
        editor = NoteEditor(note)
        return true
    }

    fun onEvent(event: NotesEvent) {
        when (event) {
            is NotesEvent.OnSearchTextChange -> _searchText.value = event.text
            // Ignored once closed, so a late change can't reopen the editor.
            is NotesEvent.ChangeTitle -> editor = editor?.copy(title = limitLength(event.title, NOTE_TITLE_MAX_CHARS))
            is NotesEvent.ChangeBody -> editor = editor?.copy(body = limitLength(event.body, NOTE_BODY_MAX_CHARS))
            NotesEvent.CloseEditor -> {
                val note = editor?.toNoteToSave(clock.millis(), ::autoTitle)
                editor = null
                if (note != null) write { repository.upsertNote(note) }
            }
            NotesEvent.DeleteNote -> {
                val note = editor?.original ?: return
                editor = null
                val session = vaultState.value
                write {
                    repository.deleteNote(note)
                    // A lock during the delete has already drained the channel; don't refill it.
                    if (vaultState.value === session) _deletedNotes.send(note)
                }
            }
            is NotesEvent.RestoreNote -> write { repository.upsertNote(event.note) }
        }
    }

    private fun write(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
            } catch (e: VaultException.Locked) {
                // The vault locked before or during the write; the write is dropped.
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as PasswordManagerApp).container
                NotesViewModel(container.noteRepository, container.vaultSession.state)
            }
        }
    }
}

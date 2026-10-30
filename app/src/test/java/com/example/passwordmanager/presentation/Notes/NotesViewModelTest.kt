package com.example.passwordmanager.presentation.Notes

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.passwordmanager.data.CredentialDatabase
import com.example.passwordmanager.data.crypto.Pbkdf2KeyDerivation
import com.example.passwordmanager.data.crypto.TEST_KDF_PARAMS
import com.example.passwordmanager.data.crypto.VaultCrypto
import com.example.passwordmanager.data.notes.Note
import com.example.passwordmanager.data.notes.NoteRepository
import com.example.passwordmanager.data.settings.SettingsStore
import com.example.passwordmanager.data.vault.VaultHeaderStore
import com.example.passwordmanager.data.vault.VaultSession
import com.example.passwordmanager.presentation.NoteRoutes
import com.example.passwordmanager.util.NOTE_BODY_MAX_CHARS
import com.example.passwordmanager.util.NOTE_TITLE_MAX_CHARS
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NotesViewModelTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val openedDatabases = mutableListOf<CredentialDatabase>()
    private val session = VaultSession(
        headerStore = VaultHeaderStore(context.getSharedPreferences(VaultHeaderStore.PREFS_NAME, Context.MODE_PRIVATE)),
        crypto = VaultCrypto(Pbkdf2KeyDerivation()),
        kdfParams = TEST_KDF_PARAMS,
        openDatabase = {
            Room.databaseBuilder(context, CredentialDatabase::class.java, TEST_DB_NAME)
                .allowMainThreadQueries()
                // Synchronous, so a write launched by the view model has finished when onEvent returns.
                .setQueryExecutor { it.run() }
                .setTransactionExecutor { it.run() }
                .build()
                .also { openedDatabases += it }
        },
        deleteDatabases = { context.deleteDatabase(TEST_DB_NAME) },
        deleteBiometricKey = {}
    )
    private val repository = NoteRepository(session)
    private val password = "correct horse battery staple"
    private var now = Instant.parse("2026-10-30T10:00:00Z")

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        openedDatabases.forEach { it.close() }
        context.deleteDatabase(TEST_DB_NAME)
    }

    @Test
    fun newNoteWithABlankTitleGetsTheDerivedTitle() = runTest {
        val viewModel = newUnlockedViewModel()

        viewModel.write(NoteRoutes.NEW_NOTE_ID, title = "  ", body = "Wifi router admin\npassword is on the box")

        val saved = repository.getAll().single()
        println("Saved '${saved.title}'")
        assertEquals("Wifi router admin · 30 Oct 2026", saved.title)
        assertEquals("Wifi router admin\npassword is on the box", saved.body)
        assertEquals(now.toEpochMilli(), saved.createdAt)
        assertEquals(now.toEpochMilli(), saved.updatedAt)
        assertNull(viewModel.editor)
    }

    @Test
    fun newNoteWithBlankTitleAndBodyIsDiscarded() = runTest {
        val viewModel = newUnlockedViewModel()

        viewModel.write(NoteRoutes.NEW_NOTE_ID, title = " ", body = "\n \t")

        assertEquals(emptyList<Note>(), repository.getAll())
        assertNull(viewModel.editor)
    }

    @Test
    fun newNoteTitleFollowsTheBodyAndIsSaved() = runTest {
        val viewModel = newUnlockedViewModel()
        viewModel.openEditor(NoteRoutes.NEW_NOTE_ID)

        viewModel.onEvent(NotesEvent.ChangeBody("Wifi"))
        println("After 'Wifi': '${viewModel.editor?.title}'")
        assertEquals("Wifi · 30 Oct 2026", viewModel.editor?.title)
        viewModel.onEvent(NotesEvent.ChangeBody("Wifi router admin"))
        assertEquals("Wifi router admin · 30 Oct 2026", viewModel.editor?.title)
        viewModel.onEvent(NotesEvent.CloseEditor)

        assertEquals("Wifi router admin · 30 Oct 2026", repository.getAll().single().title)
    }

    @Test
    fun blankBodyGivesAnEmptyAutoTitleAndIsDiscarded() = runTest {
        val viewModel = newUnlockedViewModel()
        viewModel.openEditor(NoteRoutes.NEW_NOTE_ID)

        viewModel.onEvent(NotesEvent.ChangeBody("Wifi"))
        viewModel.onEvent(NotesEvent.ChangeBody(" \n"))
        assertEquals("", viewModel.editor?.title)
        viewModel.onEvent(NotesEvent.CloseEditor)

        assertEquals(emptyList<Note>(), repository.getAll())
    }

    @Test
    fun typedTitleStopsTrackingAndClearingItResumes() = runTest {
        val viewModel = newUnlockedViewModel()
        viewModel.openEditor(NoteRoutes.NEW_NOTE_ID)
        viewModel.onEvent(NotesEvent.ChangeBody("Wifi"))

        viewModel.onEvent(NotesEvent.ChangeTitle("Router"))
        viewModel.onEvent(NotesEvent.ChangeBody("Wifi router admin"))
        assertEquals("Router", viewModel.editor?.title)

        viewModel.onEvent(NotesEvent.ChangeTitle(""))
        assertEquals("", viewModel.editor?.title)
        viewModel.onEvent(NotesEvent.ChangeBody("Bank PINs"))
        println("After clearing the title: '${viewModel.editor?.title}'")
        assertEquals("Bank PINs · 30 Oct 2026", viewModel.editor?.title)
    }

    @Test
    fun openedNoteTitleDoesNotFollowTheBody() = runTest {
        val viewModel = newUnlockedViewModel()
        repository.upsertNote(Note(title = "Wifi", body = "box", createdAt = 1, updatedAt = 1))
        viewModel.openEditor(repository.getAll().single().id)

        viewModel.onEvent(NotesEvent.ChangeBody("Completely different"))

        assertEquals("Wifi", viewModel.editor?.title)
        assertFalse(viewModel.editor!!.isTitleAuto)
    }

    @Test
    fun typedTitleIsKeptAndNotReDerivedWhenTheBodyChanges() = runTest {
        val viewModel = newUnlockedViewModel()
        viewModel.write(NoteRoutes.NEW_NOTE_ID, title = " Bank PINs ", body = "card 1234")
        val saved = repository.getAll().single()
        assertEquals("Bank PINs", saved.title)

        now = now.plusSeconds(3600)
        viewModel.write(saved.id, title = null, body = "card 5678\nother card 9999")

        val edited = repository.getById(saved.id)
        println("Edited $edited '${edited?.title}'")
        assertEquals("Bank PINs", edited?.title)
        assertEquals("card 5678\nother card 9999", edited?.body)
        assertEquals(saved.createdAt, edited?.createdAt)
        assertEquals(now.toEpochMilli(), edited?.updatedAt)
    }

    @Test
    fun derivedTitleOfASavedNoteIsNotReDerivedWhenTheBodyChanges() = runTest {
        val viewModel = newUnlockedViewModel()
        viewModel.write(NoteRoutes.NEW_NOTE_ID, title = "", body = "First line")
        val saved = repository.getAll().single()

        viewModel.write(saved.id, title = null, body = "Completely different")

        assertEquals("First line · 30 Oct 2026", repository.getById(saved.id)?.title)
    }

    @Test
    fun closingAnUnchangedNoteDoesNotTouchUpdatedAt() = runTest {
        val viewModel = newUnlockedViewModel()
        repository.upsertNote(Note(title = "Wifi", body = "box", createdAt = 1, updatedAt = 1))
        val saved = repository.getAll().single()

        viewModel.write(saved.id, title = null, body = null)

        assertEquals(saved, repository.getById(saved.id))
    }

    @Test
    fun editedNoteMovesToTheTopOfTheList() = runTest {
        val viewModel = newUnlockedViewModel()
        repository.upsertNote(Note(title = "Old", body = "a", createdAt = 1, updatedAt = 1))
        repository.upsertNote(Note(title = "Newer", body = "b", createdAt = 2, updatedAt = 2))
        val old = repository.getAll().first { it.title == "Old" }

        viewModel.write(old.id, title = null, body = "a edited")

        assertEquals(listOf("Old", "Newer"), repository.search("").first().map { it.title })
    }

    @Test
    fun inputIsCutToTheLimits() = runTest {
        val viewModel = newUnlockedViewModel()
        viewModel.openEditor(NoteRoutes.NEW_NOTE_ID)

        viewModel.onEvent(NotesEvent.ChangeTitle("t".repeat(NOTE_TITLE_MAX_CHARS + 5)))
        viewModel.onEvent(NotesEvent.ChangeBody("b".repeat(NOTE_BODY_MAX_CHARS + 5)))

        assertEquals(NOTE_TITLE_MAX_CHARS, viewModel.editor?.title?.length)
        assertEquals(NOTE_BODY_MAX_CHARS, viewModel.editor?.body?.length)
    }

    @Test
    fun reopeningTheSameNoteKeepsTheInputLikeAfterRotation() = runTest {
        val viewModel = newUnlockedViewModel()
        viewModel.openEditor(NoteRoutes.NEW_NOTE_ID)
        viewModel.onEvent(NotesEvent.ChangeBody("typed"))

        assertTrue(viewModel.openEditor(NoteRoutes.NEW_NOTE_ID))

        assertEquals("typed", viewModel.editor?.body)
    }

    @Test
    fun openingAMissingNoteFails() = runTest {
        val viewModel = newUnlockedViewModel()

        assertFalse(viewModel.openEditor(42))
        assertNull(viewModel.editor)
    }

    @Test
    fun deleteOffersUndoAndRestoreBringsTheNoteBack() = runTest {
        val viewModel = newUnlockedViewModel()
        repository.upsertNote(Note(title = "Wifi", body = "box", createdAt = 1, updatedAt = 1))
        val saved = repository.getAll().single()
        viewModel.openEditor(saved.id)

        viewModel.onEvent(NotesEvent.DeleteNote)

        assertNull(viewModel.editor)
        assertEquals(emptyList<Note>(), repository.getAll())
        val deleted = viewModel.deletedNotes.first()
        viewModel.onEvent(NotesEvent.RestoreNote(deleted))
        assertEquals(listOf(saved), repository.getAll())
    }

    @Test
    fun lockDiscardsTheEditorThePendingUndoAndTheSearch() = runTest {
        val viewModel = newUnlockedViewModel()
        viewModel.onEvent(NotesEvent.OnSearchTextChange("wifi"))
        repository.upsertNote(Note(title = "Wifi", body = "box", createdAt = 1, updatedAt = 1))
        viewModel.openEditor(repository.getAll().single().id)
        viewModel.onEvent(NotesEvent.DeleteNote)
        viewModel.openEditor(NoteRoutes.NEW_NOTE_ID)
        viewModel.onEvent(NotesEvent.ChangeBody("half typed"))

        session.lock()
        session.unlock(password.toCharArray())

        assertNull(viewModel.editor)
        assertEquals("", viewModel.searchText.value)
        assertNull(withTimeoutOrNull(1000) { viewModel.deletedNotes.first() })
        assertEquals(emptyList<Note>(), repository.getAll())
    }

    /** Opens note [id], changes the fields that are not null, and closes the editor (which saves). */
    private suspend fun NotesViewModel.write(id: Int, title: String?, body: String?) {
        assertTrue(openEditor(id))
        title?.let { onEvent(NotesEvent.ChangeTitle(it)) }
        body?.let { onEvent(NotesEvent.ChangeBody(it)) }
        onEvent(NotesEvent.CloseEditor)
    }

    private suspend fun newUnlockedViewModel(): NotesViewModel {
        session.setup(password.toCharArray())
        return NotesViewModel(
            repository,
            session.state,
            SettingsStore(context.getSharedPreferences(SettingsStore.PREFS_NAME, Context.MODE_PRIVATE)),
            clock = object : Clock() {
                override fun getZone() = ZoneOffset.UTC
                override fun withZone(zone: java.time.ZoneId?) = this
                override fun instant(): Instant = now
            },
            locale = { Locale.US }
        )
    }

    private companion object {
        const val TEST_DB_NAME = "notes_view_model_test.db"
    }
}

package com.example.passwordmanager.data.notes

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.passwordmanager.data.CredentialDatabase
import com.example.passwordmanager.data.crypto.Pbkdf2KeyDerivation
import com.example.passwordmanager.data.crypto.TEST_KDF_PARAMS
import com.example.passwordmanager.data.crypto.VaultCrypto
import com.example.passwordmanager.data.vault.VaultException
import com.example.passwordmanager.data.vault.VaultHeaderStore
import com.example.passwordmanager.data.vault.VaultSession
import com.example.passwordmanager.data.vault.VaultState
import com.example.passwordmanager.util.NOTE_BODY_MAX_CHARS
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NoteRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val headerStore = VaultHeaderStore(
        context.getSharedPreferences(VaultHeaderStore.PREFS_NAME, Context.MODE_PRIVATE)
    )
    private val openedDatabases = mutableListOf<CredentialDatabase>()
    private val password = "correct horse battery staple"
    private val note = note("Wifi router admin · 30 Oct 2026", "Wifi router admin\npassword is on the box", createdAt = 5)

    @After
    fun tearDown() {
        openedDatabases.forEach { it.close() }
        context.deleteDatabase(TEST_DB_NAME)
    }

    @Test
    fun storedNoteIsRetrievedAfterRelaunchAndUnlock() = runTest {
        val setupSession = newSession()
        setupSession.setup(password.toCharArray())
        val longNote = note("Long", "y".repeat(NOTE_BODY_MAX_CHARS), createdAt = 6)
        NoteRepository(setupSession).run {
            upsertNote(note)
            upsertNote(longNote)
        }

        val relaunchedSession = newSession()
        relaunchedSession.unlock(password.toCharArray())
        val repository = NoteRepository(relaunchedSession)
        val stored = repository.getAll()

        println("After relaunch: $stored, summaries ${repository.search("").first().map { it.title }}")
        assertEquals(listOf(note, longNote), stored.map { it.copy(id = 0) })
        assertEquals(longNote.body, repository.getById(stored[1].id)?.body)
        assertEquals(listOf("Long", note.title), repository.search("").first().map { it.title })
    }

    @Test
    fun notesAreHiddenWhileLockedAndBackAfterUnlock() = runTest {
        val session = newSession()
        session.setup(password.toCharArray())
        val repository = NoteRepository(session)
        repository.upsertNote(note)

        session.lock()
        assertEquals(VaultState.Locked, session.state.value)
        assertEquals(emptyList<NoteSummary>(), repository.search("").first())

        session.unlock(password.toCharArray())
        assertEquals(1, repository.search("box").first().size)
    }

    @Test
    fun writesAndReadsWhileLockedThrowLocked() = runTest {
        newSession().setup(password.toCharArray())
        val repository = NoteRepository(newSession())

        listOf<suspend () -> Unit>(
            { repository.upsertNote(note) },
            { repository.deleteNote(note) },
            { repository.getById(1) },
            { repository.getAll() },
            { repository.insertSkippingDuplicates(listOf(note)) }
        ).forEachIndexed { index, action ->
            assertThrows("Case $index", VaultException.Locked::class.java) { runBlocking { action() } }
        }
    }

    @Test
    fun deleteThenRestoreKeepsTheId() = runTest {
        val session = newSession()
        session.setup(password.toCharArray())
        val repository = NoteRepository(session)
        repository.upsertNote(note)
        val saved = repository.getAll().single()

        repository.deleteNote(saved)
        assertNull(repository.getById(saved.id))
        assertFalse(repository.search("").first().any { it.id == saved.id })

        repository.upsertNote(saved)
        assertEquals(saved, repository.getById(saved.id))
    }

    // File-backed so data written by one session is visible to the next, like a relaunch.
    private fun newSession() = VaultSession(
        headerStore = headerStore,
        crypto = VaultCrypto(Pbkdf2KeyDerivation()),
        kdfParams = TEST_KDF_PARAMS,
        openDatabase = {
            Room.databaseBuilder(context, CredentialDatabase::class.java, TEST_DB_NAME)
                .allowMainThreadQueries()
                .build()
                .also { openedDatabases += it }
        },
        deleteDatabases = { context.deleteDatabase(TEST_DB_NAME) },
        deleteBiometricKey = {}
    )

    private companion object {
        const val TEST_DB_NAME = "note-repository-test.db"
    }
}

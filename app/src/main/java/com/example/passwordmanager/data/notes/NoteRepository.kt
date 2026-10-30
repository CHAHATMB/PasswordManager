package com.example.passwordmanager.data.notes

import com.example.passwordmanager.data.likePattern
import com.example.passwordmanager.data.vault.VaultException
import com.example.passwordmanager.data.vault.VaultSession
import com.example.passwordmanager.data.whileUnlocked
import com.example.passwordmanager.data.write
import kotlinx.coroutines.flow.Flow

class NoteRepository(private val session: VaultSession) {

    /** Notes whose title or body contains [query], ignoring case, newest first; all of them when blank. Empty while locked. */
    fun search(query: String): Flow<List<NoteSummary>> =
        session.whileUnlocked(emptyList()) { it.noteDao.search(likePattern(query)) }

    /** Null once deleted. Throws [VaultException.Locked] when locked. */
    suspend fun getById(id: Int): Note? = session.write { it.noteDao.getById(id) }

    /** Throws [VaultException.Locked] when locked, including a lock that closes the DB mid-write. */
    suspend fun upsertNote(note: Note) = session.write { it.noteDao.upsertNote(note) }

    /** Throws [VaultException.Locked] when locked, including a lock that closes the DB mid-write. */
    suspend fun deleteNote(note: Note) = session.write { it.noteDao.deleteNote(note) }

    /** Throws [VaultException.Locked] when locked. */
    suspend fun getAll(): List<Note> = session.write { it.noteDao.getAll() }

    /**
     * Inserts in one transaction, skipping notes with the same title and body as a saved one. Returns how
     * many were inserted. Throws [VaultException.Locked] when locked.
     */
    suspend fun insertSkippingDuplicates(notes: List<Note>): Int =
        session.write { it.noteDao.insertSkippingDuplicates(notes) }
}

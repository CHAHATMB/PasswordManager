package com.example.passwordmanager.data.notes

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

const val NOTE_PREVIEW_CHARS = 200

@Dao
interface NoteDao {
    @Upsert
    suspend fun upsertNote(note: Note)

    @Delete
    suspend fun deleteNote(note: Note)

    /** Newest first. [pattern] is a LIKE pattern that uses `\` as its escape character. */
    @Query(
        "SELECT id, title, substr(body, 1, $NOTE_PREVIEW_CHARS) AS preview, updatedAt FROM Note " +
            "WHERE title LIKE :pattern ESCAPE '\\' OR body LIKE :pattern ESCAPE '\\' " +
            "ORDER BY updatedAt DESC, id DESC"
    )
    fun search(pattern: String): Flow<List<NoteSummary>>

    @Query("SELECT * FROM Note WHERE id = :id")
    suspend fun getById(id: Int): Note?

    @Query("SELECT * FROM Note ORDER BY createdAt ASC, id ASC")
    suspend fun getAll(): List<Note>

    @Insert
    suspend fun insertAll(notes: List<Note>)

    /**
     * Inserts [notes] except those whose title and body match a saved note or an earlier one in the
     * list. Returns how many were inserted.
     */
    @Transaction
    suspend fun insertSkippingDuplicates(notes: List<Note>): Int {
        val seen = getAll().mapTo(HashSet()) { it.title to it.body }
        val new = notes.filter { seen.add(it.title to it.body) }
        insertAll(new)
        return new.size
    }
}

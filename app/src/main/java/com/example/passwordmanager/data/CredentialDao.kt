package com.example.passwordmanager.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// Each CASE is NULL for every row unless its sort is chosen, so dataCreate decides the order for CREATED.
// updatedAt is 0 until the first edit, so MAX falls back to dataCreate for rows never edited.
private const val ORDER_BY = "ORDER BY " +
    "CASE WHEN :sort = 'WEBSITE' THEN website END ASC, " +
    "CASE WHEN :sort = 'MODIFIED' THEN MAX(updatedAt, dataCreate) END DESC, " +
    "CASE WHEN :sort = 'MODIFIED' THEN dataCreate END DESC, " +
    "dataCreate ASC"

@Dao
interface CredentialDao {
    @Upsert
    suspend fun upsertCredential(credential: Credential)

    @Delete
    suspend fun deleteCredential(credential: Credential)

    /** [pattern] is a LIKE pattern that uses `\` as its escape character. */
    @Query(
        "SELECT * FROM credential WHERE (website LIKE :pattern ESCAPE '\\' " +
            "OR username LIKE :pattern ESCAPE '\\') AND (:favoritesOnly = 0 OR isFav = 1) $ORDER_BY"
    )
    fun search(pattern: String, favoritesOnly: Boolean, sort: CredentialSort): Flow<List<Credential>>

    @Query("SELECT * FROM credential WHERE id = :id")
    fun getById(id: Int): Flow<Credential?>

    @Query("SELECT * FROM credential ORDER BY dataCreate ASC")
    suspend fun getAll(): List<Credential>

    @Insert
    suspend fun insertAll(credentials: List<Credential>)

    /**
     * Inserts [credentials] except those whose website and username (ignoring case) match a saved
     * credential or an earlier one in the list. Returns how many were inserted.
     */
    @Transaction
    suspend fun insertSkippingDuplicates(credentials: List<Credential>): Int {
        val seen = getAll().mapTo(HashSet()) { it.duplicateKey() }
        val new = credentials.filter { seen.add(it.duplicateKey()) }
        insertAll(new)
        return new.size
    }
}

private fun Credential.duplicateKey() = website.trim().lowercase() to username.trim().lowercase()

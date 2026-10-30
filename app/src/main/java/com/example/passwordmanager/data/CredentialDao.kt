package com.example.passwordmanager.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// The CASE is NULL for every row unless sorting by website, so dataCreate decides the order.
private const val ORDER_BY = "ORDER BY CASE WHEN :sortByWebsite THEN website END ASC, dataCreate ASC"

@Dao
interface CredentialDao {
    @Upsert
    suspend fun upsertCredential(credential: Credential)

    @Delete
    suspend fun deleteCredential(credential: Credential)

    /** [pattern] is a LIKE pattern that uses `\` as its escape character. */
    @Query(
        "SELECT * FROM credential WHERE website LIKE :pattern ESCAPE '\\' " +
            "OR username LIKE :pattern ESCAPE '\\' $ORDER_BY"
    )
    fun search(pattern: String, sortByWebsite: Boolean): Flow<List<Credential>>

    @Query("SELECT * FROM credential WHERE isFav = 1 $ORDER_BY")
    fun getFavorites(sortByWebsite: Boolean): Flow<List<Credential>>

    @Query("SELECT * FROM credential WHERE id = :id")
    fun getById(id: Int): Flow<Credential?>
}

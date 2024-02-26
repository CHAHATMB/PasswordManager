package com.example.passwordmanager.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CredentialDao {
    @Upsert
    suspend fun upsertCredential(credential: Credential)

    @Delete
    suspend fun deleteCredential(credential: Credential)

    @Query("Select * From credential ORDER BY website ASC")
    fun getOrderedByWebsite(): Flow<List<Credential>>

    @Query("Select * From credential ORDER BY dataCreate ASC")
    fun getOrderedByDateCreated(): Flow<List<Credential>>

    @Query("Select * From credential Where isFav = true")
    fun getFavoriteCredential(): Flow<List<Credential>>
}

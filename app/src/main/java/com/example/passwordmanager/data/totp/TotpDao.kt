package com.example.passwordmanager.data.totp

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

private const val SELECT_ALL = "SELECT * FROM TotpAccount ORDER BY createdAt ASC, id ASC"

@Dao
interface TotpDao {
    @Upsert
    suspend fun upsertAccount(account: TotpAccount)

    @Delete
    suspend fun deleteAccount(account: TotpAccount)

    @Query(SELECT_ALL)
    fun getAccounts(): Flow<List<TotpAccount>>

    @Query(SELECT_ALL)
    suspend fun getAll(): List<TotpAccount>

    @Insert
    suspend fun insertAll(accounts: List<TotpAccount>)

    /**
     * Inserts [accounts] except those whose secret matches a saved account or an earlier one in the
     * list. Secrets are normalised, so equal keys compare equal. Returns how many were inserted.
     */
    @Transaction
    suspend fun insertSkippingDuplicates(accounts: List<TotpAccount>): Int {
        val seen = getAll().mapTo(HashSet()) { it.secret }
        val new = accounts.filter { seen.add(it.secret) }
        insertAll(new)
        return new.size
    }
}

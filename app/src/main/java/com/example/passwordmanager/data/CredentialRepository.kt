package com.example.passwordmanager.data

import android.database.SQLException
import com.example.passwordmanager.data.vault.VaultException
import com.example.passwordmanager.data.vault.VaultSession
import com.example.passwordmanager.data.vault.VaultState
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

class CredentialRepository(private val session: VaultSession) {

    fun getOrderedByWebsite(): Flow<List<Credential>> = whileUnlocked { it.getOrderedByWebsite() }

    fun getOrderedByDateCreated(): Flow<List<Credential>> = whileUnlocked { it.getOrderedByDateCreated() }

    /** Throws [VaultException.Locked] when locked, including a lock that closes the DB mid-write. */
    suspend fun upsertCredential(credential: Credential) = write { it.upsertCredential(credential) }

    /** Throws [VaultException.Locked] when locked, including a lock that closes the DB mid-write. */
    suspend fun deleteCredential(credential: Credential) = write { it.deleteCredential(credential) }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun whileUnlocked(query: (CredentialDao) -> Flow<List<Credential>>): Flow<List<Credential>> =
        session.state.flatMapLatest { state ->
            if (state is VaultState.Unlocked) query(state.database.dao) else flowOf(emptyList())
        }

    @VisibleForTesting
    internal suspend fun write(action: suspend (CredentialDao) -> Unit) {
        val state = session.state.value as? VaultState.Unlocked ?: throw VaultException.Locked()
        try {
            action(state.database.dao)
        } catch (e: CancellationException) {
            // A subclass of IllegalStateException; it must propagate, not become Locked.
            throw e
        } catch (e: IllegalStateException) {
            throw lockedOr(e)
        } catch (e: SQLException) {
            throw lockedOr(e)
        }
    }

    // A closed DB surfaces as IllegalStateException or SQLException; only a lock explains it.
    private fun lockedOr(e: Exception): Exception =
        if (session.state.value is VaultState.Unlocked) e else VaultException.Locked(e)
}

package com.example.passwordmanager.data

import com.example.passwordmanager.data.vault.VaultSession
import com.example.passwordmanager.data.vault.VaultState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

class CredentialRepository(private val session: VaultSession) {

    fun getOrderedByWebsite(): Flow<List<Credential>> = whileUnlocked { it.getOrderedByWebsite() }

    fun getOrderedByDateCreated(): Flow<List<Credential>> = whileUnlocked { it.getOrderedByDateCreated() }

    suspend fun upsertCredential(credential: Credential) = requireDao().upsertCredential(credential)

    suspend fun deleteCredential(credential: Credential) = requireDao().deleteCredential(credential)

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun whileUnlocked(query: (CredentialDao) -> Flow<List<Credential>>): Flow<List<Credential>> =
        session.state.flatMapLatest { state ->
            if (state is VaultState.Unlocked) query(state.database.dao) else flowOf(emptyList())
        }

    private fun requireDao(): CredentialDao {
        val state = session.state.value
        check(state is VaultState.Unlocked) { "Vault is locked" }
        return state.database.dao
    }
}

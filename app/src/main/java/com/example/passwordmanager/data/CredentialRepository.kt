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

    /** Credentials whose website or username contains [query], ignoring case; all of them when blank. */
    fun search(query: String, sortByWebsite: Boolean): Flow<List<Credential>> =
        whileUnlocked(emptyList()) { it.search(likePattern(query), sortByWebsite) }

    fun getFavorites(sortByWebsite: Boolean): Flow<List<Credential>> =
        whileUnlocked(emptyList()) { it.getFavorites(sortByWebsite) }

    /** Emits null while locked or once the credential is deleted. */
    fun getById(id: Int): Flow<Credential?> = whileUnlocked(null) { it.getById(id) }

    /** Throws [VaultException.Locked] when locked, including a lock that closes the DB mid-write. */
    suspend fun upsertCredential(credential: Credential) = write { it.upsertCredential(credential) }

    /** Throws [VaultException.Locked] when locked, including a lock that closes the DB mid-write. */
    suspend fun deleteCredential(credential: Credential) = write { it.deleteCredential(credential) }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> whileUnlocked(lockedValue: T, query: (CredentialDao) -> Flow<T>): Flow<T> =
        session.state.flatMapLatest { state ->
            if (state is VaultState.Unlocked) query(state.database.dao) else flowOf(lockedValue)
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

/** Escapes LIKE wildcards so `%` and `_` typed by the user match literally. */
internal fun likePattern(query: String): String {
    val escaped = query.trim()
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
    return "%$escaped%"
}

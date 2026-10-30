package com.example.passwordmanager.data

import com.example.passwordmanager.data.vault.VaultException
import com.example.passwordmanager.data.vault.VaultSession
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.flow.Flow

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

    private fun <T> whileUnlocked(lockedValue: T, query: (CredentialDao) -> Flow<T>): Flow<T> =
        session.whileUnlocked(lockedValue) { query(it.dao) }

    /** Throws [VaultException.Locked] when locked. */
    suspend fun getAll(): List<Credential> = write { it.getAll() }

    /**
     * Inserts in one transaction, skipping duplicates of saved credentials (same website and
     * username, ignoring case). Returns how many were inserted. Throws [VaultException.Locked] when locked.
     */
    suspend fun insertSkippingDuplicates(credentials: List<Credential>): Int =
        write { it.insertSkippingDuplicates(credentials) }

    @VisibleForTesting
    internal suspend fun <T> write(action: suspend (CredentialDao) -> T): T = session.write { action(it.dao) }
}

/** Escapes LIKE wildcards so `%` and `_` typed by the user match literally. */
internal fun likePattern(query: String): String {
    val escaped = query.trim()
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
    return "%$escaped%"
}

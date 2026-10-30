package com.example.passwordmanager.data

import android.database.SQLException
import com.example.passwordmanager.data.vault.VaultException
import com.example.passwordmanager.data.vault.VaultSession
import com.example.passwordmanager.data.vault.VaultState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/** Runs [query] on each unlocked DB; emits [lockedValue] while locked. Shared by the repositories. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> VaultSession.whileUnlocked(lockedValue: T, query: (CredentialDatabase) -> Flow<T>): Flow<T> =
    state.flatMapLatest { state ->
        if (state is VaultState.Unlocked) query(state.database) else flowOf(lockedValue)
    }

/** Throws [VaultException.Locked] when locked, including a lock that closes the DB mid-write. */
internal suspend fun <T> VaultSession.write(action: suspend (CredentialDatabase) -> T): T {
    val unlocked = state.value as? VaultState.Unlocked ?: throw VaultException.Locked()
    try {
        return action(unlocked.database)
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
private fun VaultSession.lockedOr(e: Exception): Exception =
    if (state.value is VaultState.Unlocked) e else VaultException.Locked(e)

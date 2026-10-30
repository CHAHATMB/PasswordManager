package com.example.passwordmanager.data.vault

import com.example.passwordmanager.data.CredentialDatabase
import com.example.passwordmanager.data.crypto.KdfParams
import com.example.passwordmanager.data.crypto.VaultCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

sealed interface VaultState {
    data object NotSetUp : VaultState
    data object Locked : VaultState
    class Unlocked(val database: CredentialDatabase) : VaultState
}

class VaultSession(
    private val headerStore: VaultHeaderStore,
    private val crypto: VaultCrypto,
    private val kdfParams: KdfParams,
    private val openDatabase: (vaultKey: ByteArray) -> CredentialDatabase,
    private val deleteDatabases: () -> Unit
) {
    private val _state = MutableStateFlow(
        if (headerStore.exists()) VaultState.Locked else VaultState.NotSetUp
    )
    val state: StateFlow<VaultState> = _state.asStateFlow()

    /** The caller still owns [password] and should zero it afterwards. */
    suspend fun setup(password: CharArray) {
        check(_state.value == VaultState.NotSetUp) { "Vault is already set up" }
        // NonCancellable: once the key exists, it must be zeroed and an opened DB must reach
        // the state (or be closed); a cancellation in between would leak either.
        withContext(NonCancellable + Dispatchers.Default) {
            val created = crypto.createVault(password, kdfParams)
            try {
                val database = withContext(Dispatchers.IO) {
                    deleteDatabases()
                    openDatabase(created.vaultKey).also { database ->
                        try {
                            headerStore.save(created.header)
                        } catch (e: Exception) {
                            database.close()
                            throw e
                        }
                    }
                }
                _state.value = VaultState.Unlocked(database)
            } finally {
                created.vaultKey.fill(0)
            }
        }
    }

    /** Throws [VaultException.WrongPassword] and stays locked when the password is wrong. */
    suspend fun unlock(password: CharArray) {
        check(_state.value == VaultState.Locked) { "Vault is not locked" }
        val header = withContext(Dispatchers.IO) { headerStore.load() }
            ?: throw VaultException.CorruptHeader("Vault header is missing")
        withContext(NonCancellable + Dispatchers.Default) {
            val vaultKey = crypto.unlock(password, header)
            try {
                val database = withContext(Dispatchers.IO) { openDatabase(vaultKey) }
                _state.value = VaultState.Unlocked(database)
            } finally {
                vaultKey.fill(0)
            }
        }
    }
}

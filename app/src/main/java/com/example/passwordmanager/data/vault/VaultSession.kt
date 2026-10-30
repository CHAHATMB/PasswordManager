package com.example.passwordmanager.data.vault

import com.example.passwordmanager.data.CredentialDatabase
import com.example.passwordmanager.data.crypto.KdfParams
import com.example.passwordmanager.data.crypto.VaultCrypto
import javax.crypto.Cipher
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
    private val openDatabase: (databaseKey: ByteArray) -> CredentialDatabase,
    private val deleteDatabases: () -> Unit,
    private val deleteBiometricKey: () -> Unit
) {
    private val _state = MutableStateFlow(
        if (headerStore.exists()) VaultState.Locked else VaultState.NotSetUp
    )
    val state: StateFlow<VaultState> = _state.asStateFlow()

    // Both are set only while unlocked and wiped by lock(). SQLCipher keeps a reference to
    // databaseKey for the DB's lifetime; vaultKey is kept so biometric unlock can be enabled.
    private var vaultKey: ByteArray? = null
    private var databaseKey: ByteArray? = null

    /** The caller still owns [password] and should zero it afterwards. */
    suspend fun setup(password: CharArray) {
        check(_state.value == VaultState.NotSetUp) { "Vault is already set up" }
        // NonCancellable: once the key exists, it must be kept or zeroed and an opened DB must
        // reach the state (or be closed); a cancellation in between would leak either.
        withContext(NonCancellable + Dispatchers.Default) {
            val created = crypto.createVault(password, kdfParams)
            openVault(created.vaultKey) { databaseKey ->
                deleteDatabases()
                openDatabase(databaseKey).also { database ->
                    try {
                        headerStore.save(created.header)
                    } catch (e: Exception) {
                        database.close()
                        throw e
                    }
                }
            }
        }
    }

    /** Throws [VaultException.WrongPassword] and stays locked when the password is wrong. */
    suspend fun unlock(password: CharArray) {
        check(_state.value == VaultState.Locked) { "Vault is not locked" }
        val header = loadHeader()
        withContext(NonCancellable + Dispatchers.Default) {
            openVault(crypto.unlock(password, header))
        }
    }

    /** [cipher] is the DECRYPT cipher authenticated by the biometric prompt. */
    suspend fun unlockWithBiometric(cipher: Cipher) {
        check(_state.value == VaultState.Locked) { "Vault is not locked" }
        val biometric = checkNotNull(loadHeader().biometric) { "Biometric unlock is not enabled" }
        withContext(NonCancellable + Dispatchers.Default) {
            openVault(cipher.doFinal(biometric.wrappedKey))
        }
    }

    /** Closes the DB and wipes the keys. Does nothing unless unlocked. */
    fun lock() {
        val unlocked = _state.value as? VaultState.Unlocked ?: return
        _state.value = VaultState.Locked
        unlocked.database.close()
        databaseKey?.fill(0)
        vaultKey?.fill(0)
        databaseKey = null
        vaultKey = null
    }

    /** Null when biometric unlock is off. */
    fun biometricIv(): ByteArray? = headerStore.load()?.biometric?.iv

    /** [cipher] is the ENCRYPT cipher authenticated by the biometric prompt. */
    suspend fun enableBiometric(cipher: Cipher) {
        val key = checkNotNull(vaultKey) { "Vault is locked" }
        val biometric = BiometricWrappedKey(cipher.iv, cipher.doFinal(key))
        withContext(Dispatchers.IO) { headerStore.save(loadHeader().withBiometric(biometric)) }
    }

    suspend fun disableBiometric() {
        withContext(Dispatchers.IO) {
            headerStore.load()?.let { headerStore.save(it.withBiometric(null)) }
            deleteBiometricKey()
        }
    }

    private suspend fun loadHeader(): VaultHeader = withContext(Dispatchers.IO) { headerStore.load() }
        ?: throw VaultException.CorruptHeader("Vault header is missing")

    /** Takes ownership of [vaultKey]: kept until [lock] on success, zeroed on failure. */
    private suspend fun openVault(
        vaultKey: ByteArray,
        open: (databaseKey: ByteArray) -> CredentialDatabase = openDatabase
    ) {
        val databaseKey = CredentialDatabase.sqlCipherRawKey(vaultKey)
        try {
            val database = withContext(Dispatchers.IO) { open(databaseKey) }
            this.vaultKey = vaultKey
            this.databaseKey = databaseKey
            _state.value = VaultState.Unlocked(database)
        } catch (e: Throwable) {
            vaultKey.fill(0)
            databaseKey.fill(0)
            throw e
        }
    }
}

package com.example.passwordmanager

import android.app.ActivityManager
import android.content.Context
import com.example.passwordmanager.data.CredentialDatabase
import com.example.passwordmanager.data.CredentialRepository
import com.example.passwordmanager.data.backup.BackupCrypto
import com.example.passwordmanager.data.backup.BackupRepository
import com.example.passwordmanager.data.crypto.Argon2KeyDerivation
import com.example.passwordmanager.data.crypto.BiometricKeyStore
import com.example.passwordmanager.data.crypto.KdfParams
import com.example.passwordmanager.data.crypto.VaultCrypto
import com.example.passwordmanager.data.notes.NoteRepository
import com.example.passwordmanager.data.settings.SettingsStore
import com.example.passwordmanager.data.totp.TotpRepository
import com.example.passwordmanager.data.vault.UnlockThrottle
import com.example.passwordmanager.data.vault.VaultHeaderStore
import com.example.passwordmanager.data.vault.VaultSession
import com.example.passwordmanager.data.vault.VaultState
import com.example.passwordmanager.util.QrImageReader
import com.example.passwordmanager.util.SecureClipboard
import com.example.passwordmanager.util.SystemClipboard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val biometricKeyStore = BiometricKeyStore()

    private val keyDerivation = Argon2KeyDerivation()

    private val kdfParams = if (appContext.getSystemService(ActivityManager::class.java).isLowRamDevice) {
        KdfParams.LOW_RAM
    } else {
        KdfParams.DEFAULT
    }

    val vaultSession = VaultSession(
        headerStore = VaultHeaderStore(
            appContext.getSharedPreferences(VaultHeaderStore.PREFS_NAME, Context.MODE_PRIVATE)
        ),
        crypto = VaultCrypto(keyDerivation),
        kdfParams = kdfParams,
        openDatabase = { databaseKey -> CredentialDatabase.openEncrypted(appContext, databaseKey) },
        deleteDatabases = {
            appContext.deleteDatabase(CredentialDatabase.LEGACY_DB_NAME)
            appContext.deleteDatabase(CredentialDatabase.VAULT_DB_NAME)
        },
        deleteBiometricKey = biometricKeyStore::deleteKey
    )

    val credentialRepository = CredentialRepository(vaultSession)

    val totpRepository = TotpRepository(vaultSession)

    val noteRepository = NoteRepository(vaultSession)

    val backupRepository = BackupRepository(
        contentResolver = appContext.contentResolver,
        credentials = credentialRepository,
        totp = totpRepository,
        notes = noteRepository,
        crypto = BackupCrypto(keyDerivation),
        kdfParams = kdfParams
    )

    val settingsStore = SettingsStore(
        appContext.getSharedPreferences(SettingsStore.PREFS_NAME, Context.MODE_PRIVATE)
    )

    val unlockThrottle = UnlockThrottle(
        appContext.getSharedPreferences(UnlockThrottle.PREFS_NAME, Context.MODE_PRIVATE)
    )

    val autoLockObserver = AutoLockObserver(vaultSession, settingsStore)

    val secureClipboard = SecureClipboard(SystemClipboard(appContext), applicationScope)

    val qrImageReader = QrImageReader(appContext.contentResolver)

    init {
        applicationScope.launch {
            vaultSession.state.collect { if (it !is VaultState.Unlocked) secureClipboard.clearNow() }
        }
    }
}

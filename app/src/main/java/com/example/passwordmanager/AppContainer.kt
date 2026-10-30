package com.example.passwordmanager

import android.app.ActivityManager
import android.content.Context
import com.example.passwordmanager.data.CredentialDatabase
import com.example.passwordmanager.data.CredentialRepository
import com.example.passwordmanager.data.crypto.Argon2KeyDerivation
import com.example.passwordmanager.data.crypto.BiometricKeyStore
import com.example.passwordmanager.data.crypto.KdfParams
import com.example.passwordmanager.data.crypto.VaultCrypto
import com.example.passwordmanager.data.settings.SettingsStore
import com.example.passwordmanager.data.vault.UnlockThrottle
import com.example.passwordmanager.data.vault.VaultHeaderStore
import com.example.passwordmanager.data.vault.VaultSession

class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val biometricKeyStore = BiometricKeyStore()

    val vaultSession = VaultSession(
        headerStore = VaultHeaderStore(
            appContext.getSharedPreferences(VaultHeaderStore.PREFS_NAME, Context.MODE_PRIVATE)
        ),
        crypto = VaultCrypto(Argon2KeyDerivation()),
        kdfParams = if (appContext.getSystemService(ActivityManager::class.java).isLowRamDevice) {
            KdfParams.LOW_RAM
        } else {
            KdfParams.DEFAULT
        },
        openDatabase = { databaseKey -> CredentialDatabase.openEncrypted(appContext, databaseKey) },
        deleteDatabases = {
            appContext.deleteDatabase(CredentialDatabase.LEGACY_DB_NAME)
            appContext.deleteDatabase(CredentialDatabase.VAULT_DB_NAME)
        },
        deleteBiometricKey = biometricKeyStore::deleteKey
    )

    val credentialRepository = CredentialRepository(vaultSession)

    val settingsStore = SettingsStore(
        appContext.getSharedPreferences(SettingsStore.PREFS_NAME, Context.MODE_PRIVATE)
    )

    val unlockThrottle = UnlockThrottle(
        appContext.getSharedPreferences(UnlockThrottle.PREFS_NAME, Context.MODE_PRIVATE)
    )

    val autoLockObserver = AutoLockObserver(vaultSession, settingsStore)
}

package com.example.passwordmanager.presentation.SettingsScreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.passwordmanager.PasswordManagerApp
import com.example.passwordmanager.data.backup.BackupException
import com.example.passwordmanager.data.backup.BackupRepository
import com.example.passwordmanager.data.backup.EncryptedBackup
import com.example.passwordmanager.data.crypto.BiometricKeyStore
import com.example.passwordmanager.data.settings.AutoLockTimeout
import com.example.passwordmanager.data.settings.SettingsStore
import com.example.passwordmanager.data.vault.VaultException
import com.example.passwordmanager.data.vault.VaultSession
import com.example.passwordmanager.data.vault.VaultState
import com.example.passwordmanager.presentation.MasterPasswordError
import com.example.passwordmanager.presentation.validateMasterPassword
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.ProviderException
import javax.crypto.Cipher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsState(
    val autoLockTimeout: AutoLockTimeout = AutoLockTimeout.DEFAULT,
    val isBiometricEnabled: Boolean = false,
    val isBiometricError: Boolean = false,
    val backupDialog: BackupDialog? = null
)

class SettingsViewModel(
    private val session: VaultSession,
    private val settingsStore: SettingsStore,
    private val biometricKeyStore: BiometricKeyStore,
    private val backupRepository: BackupRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    // Encrypted bytes only: the export file waiting for the save picker, and the opened backup
    // waiting for its password.
    private var pendingExport: ByteArray? = null
    private var pendingImport: EncryptedBackup? = null

    init {
        viewModelScope.launch {
            session.state.collect { if (it !is VaultState.Unlocked) onBackupEvent(BackupEvent.Dismiss) }
        }
    }

    /** Re-reads the stores; the Unlock screen may have turned biometrics off in the meantime. */
    fun refresh() {
        _state.update {
            it.copy(
                autoLockTimeout = settingsStore.autoLockTimeout,
                isBiometricEnabled = session.biometricIv() != null,
                isBiometricError = false
            )
        }
    }

    fun setAutoLockTimeout(timeout: AutoLockTimeout) {
        settingsStore.autoLockTimeout = timeout
        _state.update { it.copy(autoLockTimeout = timeout) }
    }

    /** The ENCRYPT cipher for the enable prompt, or null (with an error shown) if no key can be made. */
    fun newBiometricCipher(): Cipher? = try {
        biometricKeyStore.newEncryptCipher()
    } catch (e: GeneralSecurityException) {
        showBiometricError()
        null
    } catch (e: ProviderException) {
        showBiometricError()
        null
    }

    fun enableBiometric(cipher: Cipher) {
        viewModelScope.launch {
            try {
                session.enableBiometric(cipher)
                refresh()
            } catch (e: GeneralSecurityException) {
                showBiometricError()
            } catch (e: IOException) {
                showBiometricError()
            }
        }
    }

    fun disableBiometric() {
        viewModelScope.launch {
            session.disableBiometric()
            refresh()
        }
    }

    private fun showBiometricError() = _state.update { it.copy(isBiometricError = true) }

    fun onBackupEvent(event: BackupEvent) {
        when (event) {
            BackupEvent.StartExport -> showBackupDialog(BackupDialog.MasterPassword())
            is BackupEvent.VerifyMasterPassword -> runBackup(isCsv = false) {
                try {
                    withChars(event.password) { session.verifyMasterPassword(it) }
                    BackupDialog.ExportPassword()
                } catch (e: VaultException.WrongPassword) {
                    BackupDialog.MasterPassword(BackupError.WrongMasterPassword)
                }
            }
            is BackupEvent.CreateBackup -> when (validateMasterPassword(event.password, event.confirmation)) {
                MasterPasswordError.TooShort -> showBackupDialog(BackupDialog.ExportPassword(BackupError.PasswordTooShort))
                MasterPasswordError.Mismatch -> showBackupDialog(BackupDialog.ExportPassword(BackupError.PasswordMismatch))
                null -> runBackup(isCsv = false) {
                    pendingExport = withChars(event.password) { backupRepository.createBackup(it) }
                    BackupDialog.ChooseSaveLocation
                }
            }
            BackupEvent.SavePickerOpened -> showBackupDialog(null)
            is BackupEvent.SaveBackup -> {
                val backup = pendingExport
                pendingExport = null
                if (event.uri != null && backup != null) runBackup(isCsv = false) {
                    backupRepository.save(event.uri, backup)
                    BackupDialog.Exported
                }
            }
            is BackupEvent.OpenBackup -> if (event.uri != null) runBackup(isCsv = false) {
                pendingImport = backupRepository.readBackup(event.uri)
                BackupDialog.BackupPassword()
            }
            is BackupEvent.ImportBackup -> {
                val backup = pendingImport ?: return
                runBackup(isCsv = false) {
                    try {
                        val result = withChars(event.password) { backupRepository.importBackup(backup, it) }
                        pendingImport = null
                        BackupDialog.Imported(result)
                    } catch (e: BackupException.WrongPassword) {
                        BackupDialog.BackupPassword(BackupError.WrongBackupPassword)
                    }
                }
            }
            BackupEvent.StartCsvImport -> showBackupDialog(BackupDialog.CsvWarning)
            is BackupEvent.ImportCsv -> if (event.uri != null) runBackup(isCsv = true) {
                BackupDialog.Imported(backupRepository.importCsv(event.uri))
            }
            BackupEvent.Dismiss -> {
                pendingExport = null
                pendingImport = null
                showBackupDialog(null)
            }
        }
    }

    private fun showBackupDialog(dialog: BackupDialog?) = _state.update { it.copy(backupDialog = dialog) }

    /** Shows progress while [action] runs, then the dialog it returns, or the error it throws. */
    private fun runBackup(isCsv: Boolean, action: suspend () -> BackupDialog) {
        if (_state.value.backupDialog == BackupDialog.Working) return
        showBackupDialog(BackupDialog.Working)
        viewModelScope.launch {
            val next = try {
                action()
            } catch (e: BackupException) {
                BackupDialog.Failed(e.toBackupError(isCsv))
            } catch (e: VaultException.Locked) {
                // The vault locked mid-operation; the Settings screen is gone with it.
                null
            } catch (e: IOException) {
                BackupDialog.Failed(BackupError.Unreadable)
            } catch (e: SecurityException) {
                // The document provider revoked access to the picked file.
                BackupDialog.Failed(BackupError.Unreadable)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Native KDF, SQLCipher or provider failures: show an error instead of crashing.
                BackupDialog.Failed(BackupError.Unexpected)
            }
            // A lock while this ran already dismissed everything; don't bring a step back after it.
            if (session.state.value is VaultState.Unlocked) showBackupDialog(next) else onBackupEvent(BackupEvent.Dismiss)
        }
    }

    private suspend fun <T> withChars(password: String, action: suspend (CharArray) -> T): T {
        val chars = password.toCharArray()
        try {
            return action(chars)
        } finally {
            chars.fill('\u0000')
        }
    }

    private fun BackupException.toBackupError(isCsv: Boolean): BackupError = when (this) {
        is BackupException.FileTooLarge -> BackupError.FileTooLarge
        is BackupException.UnknownFormat -> BackupError.NotABackup
        is BackupException.UnsupportedVersion -> BackupError.UnsupportedVersion
        is BackupException.Malformed -> if (isCsv) BackupError.InvalidCsv else BackupError.DamagedBackup
        is BackupException.WrongPassword -> BackupError.WrongBackupPassword
        is BackupException.NotText -> BackupError.NotText
        is BackupException.MissingColumns -> BackupError.MissingColumns(columns)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as PasswordManagerApp).container
                SettingsViewModel(
                    container.vaultSession,
                    container.settingsStore,
                    container.biometricKeyStore,
                    container.backupRepository
                )
            }
        }
    }
}

package com.example.passwordmanager.presentation.Authenticator

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.passwordmanager.PasswordManagerApp
import com.example.passwordmanager.data.totp.TotpRepository
import com.example.passwordmanager.data.totp.toTotpAccount
import com.example.passwordmanager.data.vault.VaultException
import com.example.passwordmanager.data.vault.VaultState
import com.example.passwordmanager.presentation.isTotpEditValid
import com.example.passwordmanager.util.QrImageReader
import com.example.passwordmanager.util.SecureClipboard
import com.example.passwordmanager.util.totp.OtpAuthResult
import com.example.passwordmanager.util.totp.OtpAuthUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val MILLIS_PER_SECOND = 1000L
private const val GOOGLE_MIGRATION_SCHEME = "otpauth-migration:"

class AuthenticatorViewModel(
    private val repository: TotpRepository,
    private val clipboard: SecureClipboard,
    private val vaultState: StateFlow<VaultState>,
    private val qrImageReader: QrImageReader,
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {

    // Wall-clock seconds, emitted just after each second starts.
    private val unixSeconds = flow {
        while (true) {
            val now = clock()
            emit(now / MILLIS_PER_SECOND)
            delay(MILLIS_PER_SECOND - now % MILLIS_PER_SECOND)
        }
    }

    val state = combine(repository.getAccounts(), unixSeconds) { accounts, seconds ->
        AuthenticatorState(
            items = accounts.map { TotpCodeItem(it, it.codeAt(seconds), it.secondsRemaining(seconds)) },
            unixSeconds = seconds,
            isLoaded = true
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AuthenticatorState())

    /** Compose state, like [com.example.passwordmanager.presentation.CredentialViewModel.addDraft]: text fields read it directly. */
    var dialog by mutableStateOf<AuthenticatorDialog?>(null)
        private set

    init {
        // A half-typed setup key or a scanned secret must not outlive the session.
        viewModelScope.launch {
            vaultState.collect { if (it !is VaultState.Unlocked) dialog = null }
        }
    }

    fun onEvent(event: AuthenticatorEvent) {
        when (event) {
            AuthenticatorEvent.OpenAddOptions -> dialog = AuthenticatorDialog.AddOptions
            AuthenticatorEvent.OpenManualEntry -> dialog = AuthenticatorDialog.ManualEntry(TotpDraft())
            is AuthenticatorEvent.ChangeDraft -> {
                // Ignored once closed, so a late change can't reopen the form.
                if (dialog is AuthenticatorDialog.ManualEntry) dialog = AuthenticatorDialog.ManualEntry(event.draft)
            }
            AuthenticatorEvent.SaveDraft -> {
                val draft = (dialog as? AuthenticatorDialog.ManualEntry)?.draft ?: return
                val account = draft.toAccountOrNull()
                if (account == null) {
                    dialog = AuthenticatorDialog.ManualEntry(draft.copy(isSubmitted = true))
                    return
                }
                dialog = null
                write { repository.upsertAccount(account.toTotpAccount(clock())) }
            }
            is AuthenticatorEvent.QrScanned -> {
                // The vault may have locked while the scanner was open (screen off); drop the result.
                if (vaultState.value !is VaultState.Unlocked) return
                dialog = when {
                    event.text != null -> scannedDialog(event.text)
                    event.isCameraPermissionMissing -> AuthenticatorDialog.Failed(ScanError.CameraPermissionDenied)
                    else -> null
                }
            }
            is AuthenticatorEvent.ImagePicked -> {
                val uri = event.uri ?: return
                if (vaultState.value !is VaultState.Unlocked) return
                dialog = AuthenticatorDialog.Scanning
                viewModelScope.launch {
                    val text = qrImageReader.read(uri)
                    if (vaultState.value !is VaultState.Unlocked || dialog != AuthenticatorDialog.Scanning) return@launch
                    dialog = if (text == null) AuthenticatorDialog.Failed(ScanError.NoQrCodeInImage) else scannedDialog(text)
                }
            }
            AuthenticatorEvent.SaveScanned -> {
                val account = (dialog as? AuthenticatorDialog.ConfirmScanned)?.account ?: return
                dialog = null
                write { repository.upsertAccount(account) }
            }
            is AuthenticatorEvent.CopyCode -> clipboard.copy(event.account.codeAt(clock() / MILLIS_PER_SECOND))
            is AuthenticatorEvent.StartEdit ->
                dialog = AuthenticatorDialog.Edit(event.account, event.account.issuer, event.account.accountName)
            is AuthenticatorEvent.ChangeEdit -> {
                val edit = dialog as? AuthenticatorDialog.Edit ?: return
                dialog = edit.copy(issuer = event.issuer, accountName = event.accountName)
            }
            AuthenticatorEvent.SaveEdit -> {
                val edit = dialog as? AuthenticatorDialog.Edit ?: return
                if (!isTotpEditValid(edit.issuer, edit.accountName)) return
                dialog = null
                write { repository.upsertAccount(edit.account.copy(issuer = edit.issuer.trim(), accountName = edit.accountName.trim())) }
            }
            is AuthenticatorEvent.StartDelete -> dialog = AuthenticatorDialog.Delete(event.account)
            AuthenticatorEvent.ConfirmDelete -> {
                val account = (dialog as? AuthenticatorDialog.Delete)?.account ?: return
                dialog = null
                write { repository.deleteAccount(account) }
            }
            AuthenticatorEvent.Dismiss -> dialog = null
        }
    }

    private fun scannedDialog(text: String): AuthenticatorDialog {
        if (text.trimStart().startsWith(GOOGLE_MIGRATION_SCHEME, ignoreCase = true)) {
            return AuthenticatorDialog.Failed(ScanError.GoogleMigration)
        }
        return when (val result = OtpAuthUri.parse(text)) {
            is OtpAuthResult.Totp -> AuthenticatorDialog.ConfirmScanned(result.account.toTotpAccount(clock()))
            OtpAuthResult.UnsupportedType -> AuthenticatorDialog.Failed(ScanError.CounterBased)
            OtpAuthResult.NotOtpAuth -> AuthenticatorDialog.Failed(ScanError.NotAuthenticator)
            OtpAuthResult.MissingSecret, OtpAuthResult.InvalidSecret, OtpAuthResult.InvalidParameter ->
                AuthenticatorDialog.Failed(ScanError.InvalidCode)
        }
    }

    private fun write(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
            } catch (e: VaultException.Locked) {
                // The vault locked before or during the write; the write is dropped.
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as PasswordManagerApp).container
                AuthenticatorViewModel(
                    container.totpRepository,
                    container.secureClipboard,
                    container.vaultSession.state,
                    container.qrImageReader
                )
            }
        }
    }
}

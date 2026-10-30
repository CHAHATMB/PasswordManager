package com.example.passwordmanager.presentation.Authenticator

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.passwordmanager.PasswordManagerApp
import com.example.passwordmanager.data.totp.TotpAccount
import com.example.passwordmanager.data.totp.TotpRepository
import com.example.passwordmanager.data.totp.toOtpAuthAccount
import com.example.passwordmanager.data.vault.VaultException
import com.example.passwordmanager.data.vault.VaultSession
import com.example.passwordmanager.data.vault.VaultState
import com.example.passwordmanager.util.QrCodeEncoder
import com.example.passwordmanager.util.totp.DEFAULT_TOTP_ALGORITHM
import com.example.passwordmanager.util.totp.DEFAULT_TOTP_DIGITS
import com.example.passwordmanager.util.totp.DEFAULT_TOTP_PERIOD
import com.example.passwordmanager.util.totp.OtpAuthUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SETUP_KEY_GROUP_SIZE = 4

/** One account on the export screen. [qrCode] is null while it is being rendered. */
data class TotpExportPage(
    val account: TotpAccount,
    val index: Int,
    val count: Int,
    val qrCode: Bitmap?,
    val isKeyRevealed: Boolean
) {
    /** Google and Microsoft Authenticator don't reliably honour other algorithms, digits or periods. */
    val hasCompatibilityWarning: Boolean
        get() = account.algorithm != DEFAULT_TOTP_ALGORITHM || account.digits != DEFAULT_TOTP_DIGITS ||
            account.period != DEFAULT_TOTP_PERIOD

    val groupedKey: String get() = account.secret.chunked(SETUP_KEY_GROUP_SIZE).joinToString(" ")
}

sealed interface TotpExportStep {
    /** Explains the risk before anything else. */
    data object Warning : TotpExportStep
    data class Password(val isWrongPassword: Boolean = false) : TotpExportStep
    data object Verifying : TotpExportStep
    data object NoAccounts : TotpExportStep
    data class Showing(val page: TotpExportPage) : TotpExportStep
}

sealed interface TotpExportEvent {
    data object ConfirmWarning : TotpExportEvent
    data class VerifyPassword(val password: String) : TotpExportEvent
    data object Next : TotpExportEvent
    data object Previous : TotpExportEvent
    data object ToggleKey : TotpExportEvent
}

/**
 * The only place secrets are shown. Scoped to the export screen's back stack entry: leaving the
 * screen clears it, and so does a lock, after which it starts again from [TotpExportStep.Warning].
 */
class TotpExportViewModel(
    private val session: VaultSession,
    private val repository: TotpRepository,
    private val encodeQr: (String) -> Bitmap = QrCodeEncoder::encode
) : ViewModel() {

    private val _state = MutableStateFlow<TotpExportStep>(TotpExportStep.Warning)
    val state: StateFlow<TotpExportStep> = _state.asStateFlow()

    // Loaded only after the master password was verified. Only the visible page gets a QR bitmap.
    private var accounts: List<TotpAccount> = emptyList()
    private var renderJob: Job? = null

    init {
        viewModelScope.launch {
            session.state.collect { if (it !is VaultState.Unlocked) clear() }
        }
    }

    fun onEvent(event: TotpExportEvent) {
        when (event) {
            TotpExportEvent.ConfirmWarning ->
                if (_state.value == TotpExportStep.Warning) _state.value = TotpExportStep.Password()
            is TotpExportEvent.VerifyPassword -> verify(event.password)
            TotpExportEvent.Next -> currentPage()?.let { show(it.index + 1) }
            TotpExportEvent.Previous -> currentPage()?.let { show(it.index - 1) }
            TotpExportEvent.ToggleKey -> _state.update {
                if (it is TotpExportStep.Showing) it.copy(page = it.page.copy(isKeyRevealed = !it.page.isKeyRevealed)) else it
            }
        }
    }

    private fun verify(password: String) {
        if (_state.value !is TotpExportStep.Password || session.state.value !is VaultState.Unlocked) return
        _state.value = TotpExportStep.Verifying
        viewModelScope.launch {
            val chars = password.toCharArray()
            try {
                session.verifyMasterPassword(chars)
                val loaded = repository.getAll()
                // A lock while this ran already cleared the state; don't bring secrets back after it.
                if (session.state.value !is VaultState.Unlocked) return@launch
                accounts = loaded
                if (loaded.isEmpty()) _state.value = TotpExportStep.NoAccounts else show(0)
            } catch (e: VaultException.WrongPassword) {
                _state.value = TotpExportStep.Password(isWrongPassword = true)
            } catch (e: VaultException.Locked) {
                clear()
            } finally {
                chars.fill('\u0000')
            }
        }
    }

    private fun currentPage(): TotpExportPage? = (_state.value as? TotpExportStep.Showing)?.page

    private fun show(index: Int) {
        val account = accounts.getOrNull(index) ?: return
        val page = TotpExportPage(account, index, accounts.size, qrCode = null, isKeyRevealed = false)
        _state.value = TotpExportStep.Showing(page)
        renderJob?.cancel()
        renderJob = viewModelScope.launch {
            val qrCode = withContext(Dispatchers.Default) { encodeQr(OtpAuthUri.build(account.toOtpAuthAccount())) }
            _state.update {
                if (it is TotpExportStep.Showing && it.page.index == index) it.copy(page = it.page.copy(qrCode = qrCode)) else it
            }
        }
    }

    private fun clear() {
        renderJob?.cancel()
        accounts = emptyList()
        _state.value = TotpExportStep.Warning
    }

    override fun onCleared() = clear()

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as PasswordManagerApp).container
                TotpExportViewModel(container.vaultSession, container.totpRepository)
            }
        }
    }
}

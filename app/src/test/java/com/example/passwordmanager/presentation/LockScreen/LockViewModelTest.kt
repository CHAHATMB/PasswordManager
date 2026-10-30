package com.example.passwordmanager.presentation.LockScreen

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.passwordmanager.data.CredentialDatabase
import com.example.passwordmanager.data.crypto.BiometricKeyStore
import com.example.passwordmanager.data.crypto.Pbkdf2KeyDerivation
import com.example.passwordmanager.data.crypto.TEST_KDF_PARAMS
import com.example.passwordmanager.data.crypto.VaultCrypto
import com.example.passwordmanager.data.vault.UnlockThrottle
import com.example.passwordmanager.data.vault.VaultHeaderStore
import com.example.passwordmanager.data.vault.VaultSession
import com.example.passwordmanager.data.vault.VaultState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LockViewModelTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val headerStore = VaultHeaderStore(
        context.getSharedPreferences(VaultHeaderStore.PREFS_NAME, Context.MODE_PRIVATE)
    )
    private val openedDatabases = mutableListOf<CredentialDatabase>()
    private val password = "correct horse battery staple"

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        openedDatabases.forEach { it.close() }
    }

    @Test
    fun shortPasswordMapsToPasswordTooShort() {
        val viewModel = newViewModel(newSession())

        viewModel.setup("short", "short")

        assertEquals(LockState(error = LockError.PasswordTooShort), viewModel.state.value)
    }

    @Test
    fun mismatchedConfirmationMapsToPasswordMismatch() {
        val viewModel = newViewModel(newSession())

        viewModel.setup(password, password + "x")

        assertEquals(LockState(error = LockError.PasswordMismatch), viewModel.state.value)
    }

    @Test
    fun wrongUnlockMapsToWrongPasswordAndStopsWorking() = runTest {
        newSession().setup(password.toCharArray())
        val viewModel = newViewModel(newSession())

        viewModel.unlock("wrong password!!")

        assertEquals(LockState(error = LockError.WrongPassword), viewModel.state.first { !it.isWorking })
        assertEquals(VaultState.Locked, viewModel.vaultState.value)
    }

    @Test
    fun databaseFailureMapsToUnexpected() = runTest {
        val viewModel = newViewModel(newSession(openDatabase = { throw SQLiteException("file is not a database") }))

        viewModel.setup(password, password)

        assertEquals(LockState(error = LockError.Unexpected), viewModel.state.first { !it.isWorking })
        assertEquals(VaultState.NotSetUp, viewModel.vaultState.value)
    }

    @Test
    fun correctUnlockClearsErrorAndUnlocks() = runTest {
        newSession().setup(password.toCharArray())
        val viewModel = newViewModel(newSession())

        viewModel.unlock(password)

        assertNull(viewModel.state.first { !it.isWorking }.error)
        assertTrue(viewModel.vaultState.value is VaultState.Unlocked)
    }

    @Test
    fun keystoreFailureTurnsBiometricsOffAndKeepsPasswordWorking() = runTest {
        // Robolectric has no AndroidKeyStore, so the real BiometricKeyStore fails the way a broken OEM Keystore does.
        assertThrows(GeneralSecurityException::class.java) { KeyStore.getInstance("AndroidKeyStore") }
        val setupSession = newSession()
        setupSession.setup(password.toCharArray())
        val jcaKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        setupSession.enableBiometric(Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, jcaKey) })
        val session = newSession()
        val viewModel = newViewModel(session)

        assertNull("no prompt may be shown", viewModel.takeBiometricUnlockCipher())

        assertEquals(LockError.BiometricFailed, viewModel.state.value.error)
        withContext(Dispatchers.Default) {
            withTimeout(5_000) { while (session.biometricIv() != null) delay(10) }
        }
        assertNull("a relaunch must not try the broken key again", newViewModel(newSession()).takeBiometricUnlockCipher())
        viewModel.unlock(password)
        assertTrue(viewModel.state.first { !it.isWorking }.error == null)
        assertTrue(viewModel.vaultState.value is VaultState.Unlocked)
    }

    private fun newViewModel(session: VaultSession) =
        LockViewModel(session, UnlockThrottle(), BiometricKeyStore())

    private fun newSession(
        openDatabase: (ByteArray) -> CredentialDatabase = {
            Room.inMemoryDatabaseBuilder(context, CredentialDatabase::class.java)
                .allowMainThreadQueries()
                .build()
                .also { openedDatabases += it }
        }
    ) = VaultSession(
        headerStore = headerStore,
        crypto = VaultCrypto(Pbkdf2KeyDerivation()),
        kdfParams = TEST_KDF_PARAMS,
        openDatabase = openDatabase,
        deleteDatabases = {},
        deleteBiometricKey = {}
    )
}

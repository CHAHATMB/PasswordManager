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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

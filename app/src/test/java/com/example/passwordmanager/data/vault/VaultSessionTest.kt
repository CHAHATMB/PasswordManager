package com.example.passwordmanager.data.vault

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.passwordmanager.data.CredentialDatabase
import com.example.passwordmanager.data.crypto.Pbkdf2KeyDerivation
import com.example.passwordmanager.data.crypto.TEST_KDF_PARAMS
import com.example.passwordmanager.data.crypto.VaultCrypto
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VaultSessionTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val headerStore = VaultHeaderStore(
        context.getSharedPreferences(VaultHeaderStore.PREFS_NAME, Context.MODE_PRIVATE)
    )
    private val openedDatabases = mutableListOf<CredentialDatabase>()
    private var deleteCalls = 0
    private val password = "correct horse battery staple"

    @After
    fun tearDown() {
        openedDatabases.forEach { it.close() }
    }

    @Test
    fun noHeaderStartsNotSetUp() {
        assertEquals(VaultState.NotSetUp, newSession().state.value)
    }

    @Test
    fun setupUnlocksAndStoresHeader() = runTest {
        val session = newSession()

        session.setup(password.toCharArray())

        assertTrue(session.state.value is VaultState.Unlocked)
        assertTrue(headerStore.exists())
        assertEquals(1, deleteCalls)
    }

    @Test
    fun newSessionWithExistingHeaderStartsLocked() = runTest {
        newSession().setup(password.toCharArray())

        assertEquals(VaultState.Locked, newSession().state.value)
    }

    @Test
    fun wrongPasswordStaysLocked() = runTest {
        newSession().setup(password.toCharArray())
        val session = newSession()

        try {
            session.unlock("wrong password!!".toCharArray())
            fail("Expected WrongPassword")
        } catch (expected: VaultException.WrongPassword) {
        }

        assertEquals(VaultState.Locked, session.state.value)
    }

    @Test
    fun correctPasswordUnlocks() = runTest {
        newSession().setup(password.toCharArray())
        val session = newSession()

        session.unlock(password.toCharArray())

        assertTrue(session.state.value is VaultState.Unlocked)
    }

    private fun newSession() = VaultSession(
        headerStore = headerStore,
        crypto = VaultCrypto(Pbkdf2KeyDerivation()),
        kdfParams = TEST_KDF_PARAMS,
        openDatabase = {
            Room.inMemoryDatabaseBuilder(context, CredentialDatabase::class.java)
                .allowMainThreadQueries()
                .build()
                .also { openedDatabases += it }
        },
        deleteDatabases = { deleteCalls++ }
    )
}

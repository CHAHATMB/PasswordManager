package com.example.passwordmanager.data.totp

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.passwordmanager.data.CredentialDatabase
import com.example.passwordmanager.data.crypto.Pbkdf2KeyDerivation
import com.example.passwordmanager.data.crypto.TEST_KDF_PARAMS
import com.example.passwordmanager.data.crypto.VaultCrypto
import com.example.passwordmanager.data.vault.VaultException
import com.example.passwordmanager.data.vault.VaultHeaderStore
import com.example.passwordmanager.data.vault.VaultSession
import com.example.passwordmanager.data.vault.VaultState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TotpRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val headerStore = VaultHeaderStore(
        context.getSharedPreferences(VaultHeaderStore.PREFS_NAME, Context.MODE_PRIVATE)
    )
    private val openedDatabases = mutableListOf<CredentialDatabase>()
    private val password = "correct horse battery staple"
    private val account = totpAccount("Example", "alice@example.com", "JBSWY3DPEHPK3PXP")

    @After
    fun tearDown() {
        openedDatabases.forEach { it.close() }
        context.deleteDatabase(TEST_DB_NAME)
    }

    @Test
    fun storedAccountIsRetrievedAfterRelaunchAndUnlock() = runTest {
        val setupSession = newSession()
        setupSession.setup(password.toCharArray())
        TotpRepository(setupSession).upsertAccount(account)

        val relaunchedSession = newSession()
        relaunchedSession.unlock(password.toCharArray())
        val stored = TotpRepository(relaunchedSession).getAccounts().first()

        println("After relaunch: $stored")
        assertEquals(listOf(account), stored.map { it.copy(id = 0) })
    }

    @Test
    fun accountsAreHiddenWhileLockedAndBackAfterUnlock() = runTest {
        val session = newSession()
        session.setup(password.toCharArray())
        val repository = TotpRepository(session)
        repository.upsertAccount(account)

        session.lock()
        assertEquals(VaultState.Locked, session.state.value)
        assertEquals(emptyList<TotpAccount>(), repository.getAccounts().first())

        session.unlock(password.toCharArray())
        assertEquals(1, repository.getAccounts().first().size)
    }

    @Test
    fun writesAndReadsWhileLockedThrowLocked() = runTest {
        newSession().setup(password.toCharArray())
        val repository = TotpRepository(newSession())

        listOf<suspend () -> Unit>(
            { repository.upsertAccount(account) },
            { repository.deleteAccount(account) },
            { repository.getAll() },
            { repository.insertSkippingDuplicates(listOf(account)) }
        ).forEachIndexed { index, action ->
            assertThrows("Case $index", VaultException.Locked::class.java) { runBlocking { action() } }
        }
    }

    @Test
    fun deleteRemovesTheAccount() = runTest {
        val session = newSession()
        session.setup(password.toCharArray())
        val repository = TotpRepository(session)
        repository.upsertAccount(account)

        repository.deleteAccount(repository.getAll().single())

        assertEquals(emptyList<TotpAccount>(), repository.getAccounts().first())
    }

    // File-backed so data written by one session is visible to the next, like a relaunch.
    private fun newSession() = VaultSession(
        headerStore = headerStore,
        crypto = VaultCrypto(Pbkdf2KeyDerivation()),
        kdfParams = TEST_KDF_PARAMS,
        openDatabase = {
            Room.databaseBuilder(context, CredentialDatabase::class.java, TEST_DB_NAME)
                .allowMainThreadQueries()
                .build()
                .also { openedDatabases += it }
        },
        deleteDatabases = { context.deleteDatabase(TEST_DB_NAME) },
        deleteBiometricKey = {}
    )

    private companion object {
        const val TEST_DB_NAME = "totp-repository-test.db"
    }
}

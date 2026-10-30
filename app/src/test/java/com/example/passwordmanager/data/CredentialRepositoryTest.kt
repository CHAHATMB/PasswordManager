package com.example.passwordmanager.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.passwordmanager.data.crypto.Pbkdf2KeyDerivation
import com.example.passwordmanager.data.crypto.TEST_KDF_PARAMS
import com.example.passwordmanager.data.crypto.VaultCrypto
import com.example.passwordmanager.data.vault.VaultException
import com.example.passwordmanager.data.vault.VaultHeaderStore
import com.example.passwordmanager.data.vault.VaultSession
import com.example.passwordmanager.data.vault.VaultState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CredentialRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val headerStore = VaultHeaderStore(
        context.getSharedPreferences(VaultHeaderStore.PREFS_NAME, Context.MODE_PRIVATE)
    )
    private val openedDatabases = mutableListOf<CredentialDatabase>()
    private val password = "correct horse battery staple"

    @After
    fun tearDown() {
        openedDatabases.forEach { it.close() }
        context.deleteDatabase(TEST_DB_NAME)
    }

    @Test
    fun flowsEmitEmptyWhileNotSetUp() = runTest {
        val repository = CredentialRepository(newSession())

        assertEquals(emptyList<Credential>(), repository.search("", sortByWebsite = false).first())
        assertEquals(emptyList<Credential>(), repository.search("", sortByWebsite = true).first())
    }

    @Test
    fun flowsEmitEmptyWhileLockedEvenWhenDataExists() = runTest {
        val setupSession = newSession()
        setupSession.setup(password.toCharArray())
        CredentialRepository(setupSession).upsertCredential(credential("github.com"))

        val lockedSession = newSession()
        val repository = CredentialRepository(lockedSession)

        assertEquals(VaultState.Locked, lockedSession.state.value)
        assertEquals(emptyList<Credential>(), repository.search("", sortByWebsite = false).first())
        assertEquals(emptyList<Credential>(), repository.search("", sortByWebsite = true).first())
    }

    @Test
    fun writeWhileLockedThrows() = runTest {
        newSession().setup(password.toCharArray())
        val repository = CredentialRepository(newSession())

        try {
            repository.upsertCredential(credential("github.com"))
            fail("Expected VaultException.Locked")
        } catch (expected: VaultException.Locked) {
        }
    }

    @Test
    fun closedDatabaseErrorAfterLockMidWriteBecomesLocked() = runTest {
        val session = newSession()
        session.setup(password.toCharArray())

        val thrown = writeFailure(CredentialRepository(session)) {
            session.lock()
            throw IllegalStateException("attempt to re-open an already-closed object")
        }

        println("Mid-write lock surfaced as $thrown")
        assertTrue(thrown is VaultException.Locked)
    }

    @Test
    fun cancellationDuringWriteAfterLockIsRethrownNotMappedToLocked() = runTest {
        val session = newSession()
        session.setup(password.toCharArray())

        val thrown = writeFailure(CredentialRepository(session)) {
            session.lock()
            throw CancellationException("cancelled")
        }

        println("Cancellation during a locked write surfaced as $thrown")
        assertTrue(thrown is CancellationException)
    }

    @Test
    fun storedCredentialIsRetrievedAfterRelaunchAndUnlock() = runTest {
        val setupSession = newSession()
        setupSession.setup(password.toCharArray())
        CredentialRepository(setupSession).upsertCredential(credential("github.com"))

        val relaunchedSession = newSession()
        relaunchedSession.unlock(password.toCharArray())
        val stored = CredentialRepository(relaunchedSession).search("", sortByWebsite = false).first()

        assertEquals(1, stored.size)
        assertEquals("github.com", stored[0].website)
        assertEquals("user@github.com", stored[0].username)
        assertEquals("secret-github.com", stored[0].password)
    }

    @Test
    fun deleteRemovesCredential() = runTest {
        val session = newSession()
        session.setup(password.toCharArray())
        val repository = CredentialRepository(session)
        repository.upsertCredential(credential("github.com"))
        val saved = repository.search("", sortByWebsite = true).first().single()

        repository.deleteCredential(saved)

        assertTrue(repository.search("", sortByWebsite = true).first().isEmpty())
    }

    private suspend fun writeFailure(repository: CredentialRepository, action: suspend () -> Unit): Exception? =
        try {
            repository.write { action() }
            null
        } catch (e: Exception) {
            e
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

    private fun credential(website: String) = Credential(
        website = website,
        username = "user@$website",
        password = "secret-$website",
        dataCreate = 1,
        note = ""
    )

    private companion object {
        const val TEST_DB_NAME = "repository-test.db"
    }
}

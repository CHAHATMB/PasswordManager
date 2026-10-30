package com.example.passwordmanager.data.vault

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.passwordmanager.data.Credential
import com.example.passwordmanager.data.CredentialDatabase
import com.example.passwordmanager.data.CredentialRepository
import com.example.passwordmanager.data.crypto.Pbkdf2KeyDerivation
import com.example.passwordmanager.data.crypto.TEST_KDF_PARAMS
import com.example.passwordmanager.data.crypto.VaultCrypto
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    private val databaseKeys = mutableListOf<ByteArray>()
    private var deleteCalls = 0
    private var deleteBiometricKeyCalls = 0
    // Plain JCA stand-in for the Keystore key, which needs a device.
    private val biometricKey: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
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

    @Test
    fun verifyMasterPasswordAcceptsOnlyTheMasterPasswordAndKeepsTheVaultUnlocked() = runTest {
        val session = newSession()
        session.setup(password.toCharArray())

        session.verifyMasterPassword(password.toCharArray())
        try {
            session.verifyMasterPassword("wrong password!!".toCharArray())
            fail("Expected VaultException.WrongPassword")
        } catch (expected: VaultException.WrongPassword) {
        }

        assertTrue(session.state.value is VaultState.Unlocked)
    }

    @Test
    fun lockClosesDatabaseAndWipesItsKey() = runTest {
        val session = newSession()
        session.setup(password.toCharArray())
        val database = (session.state.value as VaultState.Unlocked).database
        val databaseKey = databaseKeys.single()
        assertTrue("key must be live while unlocked", databaseKey.any { it != 0.toByte() })

        session.lock()

        assertEquals(VaultState.Locked, session.state.value)
        assertFalse("DB must be closed", database.isOpen)
        assertTrue("SQLCipher key must be zeroed: ${databaseKey.toList()}", databaseKey.all { it == 0.toByte() })
    }

    @Test
    fun lockEmptiesRepositoryFlows() = runTest {
        val session = newSession()
        val repository = CredentialRepository(session)
        session.setup(password.toCharArray())
        repository.upsertCredential(Credential(website = "a.com", username = "u", password = "p", dataCreate = 1, note = ""))
        assertEquals(1, repository.search("", sortByWebsite = true).first().size)

        session.lock()

        assertTrue(repository.search("", sortByWebsite = true).first().isEmpty())
        assertTrue(repository.search("", sortByWebsite = false).first().isEmpty())
    }

    @Test
    fun lockWhenNotUnlockedDoesNothing() = runTest {
        newSession().setup(password.toCharArray())
        val session = newSession()

        session.lock()

        assertEquals(VaultState.Locked, session.state.value)
        session.unlock(password.toCharArray())
        assertTrue("an earlier lock must not undo a later unlock", session.state.value is VaultState.Unlocked)
    }

    @Test
    fun lockDuringUnlockLocksAgainOnceUnlockFinishes() = runTest {
        newSession().setup(password.toCharArray())
        lateinit var session: VaultSession
        // Simulates a screen-off while the KDF/DB open is still running.
        session = newSession(beforeOpen = { session.lock() })

        session.unlock(password.toCharArray())

        assertEquals(VaultState.Locked, session.state.value)
        assertFalse("DB must be closed", openedDatabases.last().isOpen)
        assertTrue("SQLCipher key must be zeroed", databaseKeys.last().all { it == 0.toByte() })
    }

    @Test
    fun biometricUnlockOpensTheVaultWithTheSameKey() = runTest {
        val session = newSession()
        session.setup(password.toCharArray())
        val setupKey = databaseKeys.single().copyOf()
        session.enableBiometric(encryptCipher())
        session.lock()

        session.unlockWithBiometric(decryptCipher(session.biometricIv()!!))

        assertTrue(session.state.value is VaultState.Unlocked)
        assertArrayEquals("biometric unlock must yield the vault key", setupKey, databaseKeys.last())
    }

    @Test
    fun biometricUnlockWithOtherKeyFailsAndStaysLocked() = runTest {
        val session = newSession()
        session.setup(password.toCharArray())
        session.enableBiometric(encryptCipher())
        session.lock()
        val otherKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

        try {
            session.unlockWithBiometric(decryptCipher(session.biometricIv()!!, otherKey))
            fail("Expected AEADBadTagException")
        } catch (expected: AEADBadTagException) {
        }

        assertEquals(VaultState.Locked, session.state.value)
    }

    @Test
    fun enableBiometricWhileLockedThrows() = runTest {
        newSession().setup(password.toCharArray())
        val session = newSession()

        try {
            session.enableBiometric(encryptCipher())
            fail("Expected IllegalStateException")
        } catch (expected: IllegalStateException) {
        }

        assertNull(session.biometricIv())
    }

    @Test
    fun disableBiometricRemovesBlobAndKeyButPasswordStillWorks() = runTest {
        val session = newSession()
        session.setup(password.toCharArray())
        session.enableBiometric(encryptCipher())
        assertNotNull(session.biometricIv())

        session.disableBiometric()
        session.lock()

        assertNull(session.biometricIv())
        assertEquals(1, deleteBiometricKeyCalls)
        session.unlock(password.toCharArray())
        assertTrue(session.state.value is VaultState.Unlocked)
    }

    private fun encryptCipher() = Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(Cipher.ENCRYPT_MODE, biometricKey)
    }

    private fun decryptCipher(iv: ByteArray, key: SecretKey = biometricKey) =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        }

    private fun newSession(beforeOpen: () -> Unit = {}) = VaultSession(
        headerStore = headerStore,
        crypto = VaultCrypto(Pbkdf2KeyDerivation()),
        kdfParams = TEST_KDF_PARAMS,
        openDatabase = { databaseKey ->
            beforeOpen()
            databaseKeys += databaseKey
            Room.inMemoryDatabaseBuilder(context, CredentialDatabase::class.java)
                .allowMainThreadQueries()
                .build()
                .also { openedDatabases += it }
        },
        deleteDatabases = { deleteCalls++ },
        deleteBiometricKey = { deleteBiometricKeyCalls++ }
    )
}

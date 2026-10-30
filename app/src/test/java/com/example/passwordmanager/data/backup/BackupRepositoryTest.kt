package com.example.passwordmanager.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.passwordmanager.data.Credential
import com.example.passwordmanager.data.CredentialDatabase
import com.example.passwordmanager.data.CredentialRepository
import com.example.passwordmanager.data.crypto.KdfParams
import com.example.passwordmanager.data.crypto.Pbkdf2KeyDerivation
import com.example.passwordmanager.data.crypto.TEST_KDF_PARAMS
import com.example.passwordmanager.data.crypto.VaultCrypto
import com.example.passwordmanager.data.vault.VaultHeaderStore
import com.example.passwordmanager.data.vault.VaultSession
import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class BackupRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val openedDatabases = mutableListOf<CredentialDatabase>()
    private val now = 1_700_000_000_000
    private val backupPassword = "export password 1"
    private val saved = listOf(
        Credential(website = "github.com", username = "alice", password = "gh-secret-1", dataCreate = 1, isFav = true, note = "2FA codes\nin the drawer"),
        Credential(website = "example.com", username = "bob", password = "ex,pass-2", dataCreate = 2, note = "")
    )

    @After
    fun tearDown() {
        openedDatabases.forEach { it.close() }
        VAULT_NAMES.forEach { context.deleteDatabase(it) }
    }

    @Test
    fun csvImportCountsInvalidRowsThenReportsEveryRowAsDuplicateOnReimport() = runTest {
        val (repository, credentials) = unlockedVault(VAULT_NAMES[0])
        val uri = register("gpm.csv", fixture("gpm.csv"))

        val first = repository.importCsv(uri)
        val second = repository.importCsv(uri)

        println("First import ${first.counts()}, second ${second.counts()}")
        assertEquals(Triple(3, 0, 1), first.counts())
        assertEquals(Triple(0, 3, 1), second.counts())
        assertEquals(3, credentials.getAll().size)
        assertEquals(now, credentials.getAll().first().dataCreate)
    }

    @Test
    fun backupRestoresEveryFieldIntoANewVaultAndReimportIsAllDuplicates() = runTest {
        val (source, sourceCredentials) = unlockedVault(VAULT_NAMES[0])
        sourceCredentials.insertSkippingDuplicates(saved)
        val backupUri = register("backup.json", source.createBackup(backupPassword.toCharArray()))

        val (target, targetCredentials) = unlockedVault(VAULT_NAMES[1])
        val restored = target.importBackup(target.readBackup(backupUri), backupPassword.toCharArray())
        val again = target.importBackup(target.readBackup(backupUri), backupPassword.toCharArray())

        println("Restore ${restored.counts()}, again ${again.counts()}")
        assertEquals(Triple(2, 0, 0), restored.counts())
        assertEquals(Triple(0, 2, 0), again.counts())
        assertEquals(saved, targetCredentials.getAll().map { it.copy(id = 0) })
    }

    @Test
    fun wrongBackupPasswordImportsNothing() = runTest {
        val (source, sourceCredentials) = unlockedVault(VAULT_NAMES[0])
        sourceCredentials.insertSkippingDuplicates(saved)
        val backupUri = register("backup.json", source.createBackup(backupPassword.toCharArray()))
        val (target, targetCredentials) = unlockedVault(VAULT_NAMES[1])
        val backup = target.readBackup(backupUri)

        assertThrows(BackupException.WrongPassword::class.java) {
            runBlocking { target.importBackup(backup, "export password 2".toCharArray()) }
        }
        assertEquals(emptyList<Credential>(), targetCredentials.getAll())
    }

    @Test
    fun oversizedFileIsRejectedBeforeParsing() = runTest {
        val (repository, credentials) = unlockedVault(VAULT_NAMES[0])
        val uri = register("huge.csv", ByteArray(MAX_IMPORT_FILE_MEGABYTES * 1024 * 1024 + 1))

        assertThrows(BackupException.FileTooLarge::class.java) {
            runBlocking { repository.importCsv(uri) }
        }
        assertEquals(emptyList<Credential>(), credentials.getAll())
    }

    private fun ImportResult.counts() = Triple(imported, duplicates, invalid)

    private fun register(name: String, bytes: ByteArray): Uri {
        val uri = Uri.parse("content://test/$name")
        shadowOf(context.contentResolver).registerInputStreamSupplier(uri) { ByteArrayInputStream(bytes) }
        return uri
    }

    private fun fixture(name: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/csv/$name")) { "Missing fixture $name" }.use { it.readBytes() }

    /** A set-up vault with its own header prefs and DB file, so two of them can coexist like two phones. */
    private suspend fun unlockedVault(name: String): Pair<BackupRepository, CredentialRepository> {
        val session = VaultSession(
            headerStore = VaultHeaderStore(context.getSharedPreferences(name, Context.MODE_PRIVATE)),
            crypto = VaultCrypto(Pbkdf2KeyDerivation()),
            kdfParams = TEST_KDF_PARAMS,
            openDatabase = {
                Room.databaseBuilder(context, CredentialDatabase::class.java, name)
                    .allowMainThreadQueries()
                    .build()
                    .also { openedDatabases += it }
            },
            deleteDatabases = { context.deleteDatabase(name) },
            deleteBiometricKey = {}
        )
        session.setup("correct horse battery staple".toCharArray())
        val credentials = CredentialRepository(session)
        // Within the bounds BackupCrypto.parse enforces; the PBKDF2 stand-in reads iterations only.
        val backupParams = KdfParams(memoryKib = 16, iterations = 10, parallelism = 1)
        return BackupRepository(context.contentResolver, credentials, BackupCrypto(Pbkdf2KeyDerivation()), backupParams) { now } to credentials
    }

    private companion object {
        val VAULT_NAMES = listOf("backup-source", "backup-target")
    }
}

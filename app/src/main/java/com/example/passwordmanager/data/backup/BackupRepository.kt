package com.example.passwordmanager.data.backup

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import com.example.passwordmanager.data.CredentialRepository
import com.example.passwordmanager.data.crypto.KdfParams
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ImportResult(val imported: Int, val duplicates: Int, val invalid: Int)

/** Reads and writes backup and CSV files picked through the Storage Access Framework. */
class BackupRepository(
    private val contentResolver: ContentResolver,
    private val credentials: CredentialRepository,
    private val crypto: BackupCrypto,
    private val kdfParams: KdfParams,
    private val clock: () -> Long = System::currentTimeMillis
) {

    /** Encrypts every credential. The result holds no plaintext, so it can wait while the user picks a file. */
    suspend fun createBackup(password: CharArray): ByteArray {
        val items = credentials.getAll().map { it.toBackupItem() }
        return withContext(Dispatchers.Default) { crypto.encrypt(items, password, kdfParams) }
    }

    /** On failure, deletes the document the picker created, so no empty or partial file is left. */
    suspend fun save(uri: Uri, backup: ByteArray) = withContext(Dispatchers.IO) {
        try {
            val output = contentResolver.openOutputStream(uri) ?: throw FileNotFoundException("Cannot write the chosen file")
            output.use { it.write(backup) }
        } catch (e: IOException) {
            deleteQuietly(uri)
            throw e
        } catch (e: SecurityException) {
            deleteQuietly(uri)
            throw e
        }
    }

    suspend fun delete(uri: Uri) = withContext(Dispatchers.IO) { deleteQuietly(uri) }

    /** Checks the file before the password is asked for, so a wrong file fails early. */
    suspend fun readBackup(uri: Uri): EncryptedBackup = withContext(Dispatchers.IO) { crypto.parse(read(uri)) }

    suspend fun importBackup(backup: EncryptedBackup, password: CharArray): ImportResult {
        val parsed = withContext(Dispatchers.Default) {
            crypto.decrypt(backup, password).map { it.toCredential() }.toParsedImport()
        }
        return insert(parsed)
    }

    suspend fun importCsv(uri: Uri): ImportResult {
        val file = withContext(Dispatchers.IO) { read(uri) }
        val parsed = try {
            withContext(Dispatchers.Default) { CsvImporter.parse(file, clock()) }
        } finally {
            file.fill(0)
        }
        return insert(parsed)
    }

    private suspend fun insert(parsed: ParsedImport): ImportResult {
        val imported = credentials.insertSkippingDuplicates(parsed.credentials)
        return ImportResult(imported, duplicates = parsed.credentials.size - imported, invalid = parsed.invalid)
    }

    // Best effort: the provider may already have removed the file, or not support deleting it.
    private fun deleteQuietly(uri: Uri) {
        try {
            DocumentsContract.deleteDocument(contentResolver, uri)
        } catch (e: FileNotFoundException) {
        } catch (e: SecurityException) {
        } catch (e: UnsupportedOperationException) {
        } catch (e: IllegalArgumentException) {
        }
    }

    private fun read(uri: Uri): ByteArray {
        val input = contentResolver.openInputStream(uri) ?: throw FileNotFoundException("Cannot read the chosen file")
        return input.use { it.readImportFile() }
    }
}

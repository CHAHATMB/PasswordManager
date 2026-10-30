package com.example.passwordmanager.data.backup

import com.example.passwordmanager.data.Credential
import java.io.InputStream

// Bounds memory too: every row of a file this size becomes objects on the heap at once.
const val MAX_IMPORT_FILE_MEGABYTES = 5
private const val MAX_IMPORT_FILE_BYTES = MAX_IMPORT_FILE_MEGABYTES * 1024 * 1024
private const val INITIAL_BUFFER_BYTES = 8192

/** The credentials read from an import file, and how many rows could not be used. */
class ParsedImport(val credentials: List<Credential>, val invalid: Int)

/** Rows without a website or password can't be saved, the same rule as the Add form. */
fun Credential.isImportable(): Boolean = website.isNotBlank() && password.isNotBlank()

fun List<Credential>.toParsedImport(): ParsedImport {
    val (valid, invalid) = partition { it.isImportable() }
    return ParsedImport(valid, invalid.size)
}

/**
 * Reads the whole stream, failing with [BackupException.FileTooLarge] as soon as it passes [limit]
 * bytes. Outgrown buffers are wiped, since they may hold a plaintext CSV.
 */
fun InputStream.readImportFile(limit: Int = MAX_IMPORT_FILE_BYTES): ByteArray {
    var buffer = ByteArray(minOf(INITIAL_BUFFER_BYTES, limit + 1))
    try {
        var size = 0
        while (true) {
            if (size == buffer.size) {
                val grown = buffer.copyOf(minOf(buffer.size * 2, limit + 1))
                buffer.fill(0)
                buffer = grown
            }
            val read = read(buffer, size, buffer.size - size)
            if (read < 0) return buffer.copyOf(size)
            size += read
            if (size > limit) throw BackupException.FileTooLarge()
        }
    } finally {
        buffer.fill(0)
    }
}

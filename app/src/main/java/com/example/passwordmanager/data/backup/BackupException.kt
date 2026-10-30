package com.example.passwordmanager.data.backup

sealed class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class FileTooLarge : BackupException("File is larger than $MAX_IMPORT_FILE_MEGABYTES MB")
    class UnknownFormat : BackupException("Not a Vaultly backup file")
    class UnsupportedVersion(val version: Int?) : BackupException("Unsupported backup version $version")
    class Malformed(message: String, cause: Throwable? = null) : BackupException(message, cause)
    class WrongPassword(cause: Throwable) : BackupException("Wrong backup password or modified file", cause)
    class NotText(cause: Throwable) : BackupException("File is not UTF-8 text", cause)
    class MissingColumns(val columns: List<String>) : BackupException("CSV is missing columns $columns")
}

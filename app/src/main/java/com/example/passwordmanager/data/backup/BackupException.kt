package com.example.passwordmanager.data.backup

sealed class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class UnknownFormat : BackupException("Not a Password Manager backup file")
    class UnsupportedVersion(val version: Int?) : BackupException("Unsupported backup version $version")
    class Malformed(message: String, cause: Throwable? = null) : BackupException(message, cause)
    class WrongPassword(cause: Throwable) : BackupException("Wrong backup password or modified file", cause)
}

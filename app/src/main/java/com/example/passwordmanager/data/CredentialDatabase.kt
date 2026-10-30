package com.example.passwordmanager.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.passwordmanager.data.notes.Note
import com.example.passwordmanager.data.notes.NoteDao
import com.example.passwordmanager.data.totp.TotpAccount
import com.example.passwordmanager.data.totp.TotpDao
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [Credential::class, TotpAccount::class, Note::class],
    version = 3,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)]
)
abstract class CredentialDatabase: RoomDatabase() {
    abstract val dao: CredentialDao
    abstract val totpDao: TotpDao
    abstract val noteDao: NoteDao

    companion object {
        const val VAULT_DB_NAME = "vault.db"
        const val LEGACY_DB_NAME = "credential.db"

        /**
         * Blocking: opens the DB immediately so a bad key fails here rather than on the first query.
         * [databaseKey] comes from [sqlCipherRawKey]; the factory keeps a reference to it
         * (clearPassphrase = false) for the DB's lifetime, so the caller wipes it after closing.
         */
        fun openEncrypted(context: Context, databaseKey: ByteArray): CredentialDatabase =
            Room.databaseBuilder(context, CredentialDatabase::class.java, VAULT_DB_NAME)
                .openHelperFactory(SupportOpenHelperFactory(databaseKey))
                .build()
                .also { it.openHelper.writableDatabase }

        // SQLCipher treats a key of the form x'<64 hex>' as a raw key and skips its own KDF.
        // Built without a String so the key is never interned.
        internal fun sqlCipherRawKey(key: ByteArray): ByteArray {
            val hexDigits = "0123456789abcdef"
            val rawKey = ByteArray(key.size * 2 + 3)
            rawKey[0] = 'x'.code.toByte()
            rawKey[1] = '\''.code.toByte()
            key.forEachIndexed { index, byte ->
                val value = byte.toInt() and 0xff
                rawKey[2 + index * 2] = hexDigits[value ushr 4].code.toByte()
                rawKey[3 + index * 2] = hexDigits[value and 0x0f].code.toByte()
            }
            rawKey[rawKey.size - 1] = '\''.code.toByte()
            return rawKey
        }
    }
}

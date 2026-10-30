package com.example.passwordmanager.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.passwordmanager.data.notes.Note
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CredentialDatabaseMigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun tearDown() {
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun version1DatabaseKeepsItsCredentialsAndGainsEmptyTotpAndNoteTables() = runTest {
        createDatabase(SCHEMA_1, version = 1)

        val database = openCurrent()
        try {
            val credentials = database.dao.getAll()
            println("After migration: ${credentials.map { it.website }}, version ${database.openHelper.readableDatabase.version}")
            assertEquals(CURRENT_VERSION, database.openHelper.readableDatabase.version)
            assertEquals(listOf("github.com"), credentials.map { it.website })
            assertEquals("gh-secret", credentials.single().password)
            assertEquals(emptyList<Any>(), database.totpDao.getAccounts().first())
            assertEquals(emptyList<Any>(), database.noteDao.getAll())
        } finally {
            database.close()
        }
    }

    @Test
    fun version2DatabaseKeepsItsCredentialsAndTotpAccountsAndGainsAnEmptyNoteTable() = runTest {
        createDatabase(SCHEMA_2, version = 2) { db ->
            db.insert("TotpAccount", null, ContentValues().apply {
                put("issuer", "Example")
                put("accountName", "alice@example.com")
                put("secret", "JBSWY3DPEHPK3PXP")
                put("algorithm", "SHA1")
                put("digits", 6)
                put("period", 30)
                put("createdAt", 2L)
            })
        }

        val database = openCurrent()
        try {
            val accounts = database.totpDao.getAll()
            println("After migration: ${database.dao.getAll().map { it.website }}, $accounts")
            assertEquals(CURRENT_VERSION, database.openHelper.readableDatabase.version)
            assertEquals(listOf("github.com"), database.dao.getAll().map { it.website })
            assertEquals("996554", accounts.single().codeAt(59))
            assertEquals(emptyList<Any>(), database.noteDao.getAll())

            database.noteDao.upsertNote(Note(title = "Wifi", body = "on the box", createdAt = 3, updatedAt = 3))
            assertEquals(listOf("Wifi"), database.noteDao.getAll().map { it.title })
        } finally {
            database.close()
        }
    }

    private fun openCurrent(): CredentialDatabase =
        Room.databaseBuilder(context, CredentialDatabase::class.java, DB_NAME)
            .allowMainThreadQueries()
            .build()

    /** Builds the DB exactly as an older build left it, from that version's exported schema, with one credential. */
    private fun createDatabase(schemaPath: String, version: Int, insertMore: (SQLiteDatabase) -> Unit = {}) {
        val schema = Json.parseToJsonElement(File(schemaPath).readText()).jsonObject["database"] as JsonObject
        val file = context.getDatabasePath(DB_NAME).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            schema["entities"]!!.jsonArray.forEach { entity ->
                val table = entity.jsonObject["tableName"]!!.jsonPrimitive.content
                db.execSQL(entity.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            }
            schema["setupQueries"]!!.jsonArray.forEach { query ->
                db.execSQL(query.jsonPrimitive.content.replace("\${TABLE_NAME}", "room_master_table"))
            }
            db.insert("Credential", null, ContentValues().apply {
                put("website", "github.com")
                put("username", "alice")
                put("password", "gh-secret")
                put("dataCreate", 1L)
                put("isFav", 0)
                put("note", "")
            })
            insertMore(db)
            db.version = version
        }
    }

    private companion object {
        const val DB_NAME = "migration-test.db"
        const val CURRENT_VERSION = 3
        // Unit tests run with the module directory as the working directory.
        const val SCHEMA_1 = "schemas/com.example.passwordmanager.data.CredentialDatabase/1.json"
        const val SCHEMA_2 = "schemas/com.example.passwordmanager.data.CredentialDatabase/2.json"
    }
}

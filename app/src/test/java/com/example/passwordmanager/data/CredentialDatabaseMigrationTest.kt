package com.example.passwordmanager.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
    fun version1DatabaseKeepsItsCredentialsAndGainsAnEmptyTotpTable() = runTest {
        createVersion1Database()

        val database = Room.databaseBuilder(context, CredentialDatabase::class.java, DB_NAME)
            .allowMainThreadQueries()
            .build()
        try {
            val credentials = database.dao.getAll()
            println("After migration: ${credentials.map { it.website }}, version ${database.openHelper.readableDatabase.version}")
            assertEquals(2, database.openHelper.readableDatabase.version)
            assertEquals(listOf("github.com"), credentials.map { it.website })
            assertEquals("gh-secret", credentials.single().password)
            assertEquals(emptyList<Any>(), database.totpDao.getAccounts().first())
        } finally {
            database.close()
        }
    }

    /** Builds the DB exactly as the 08 build left it, from the exported version 1 schema. */
    private fun createVersion1Database() {
        val schema = Json.parseToJsonElement(File(SCHEMA_1).readText()).jsonObject["database"] as JsonObject
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
            db.version = 1
        }
    }

    private companion object {
        const val DB_NAME = "migration-test.db"
        // Unit tests run with the module directory as the working directory.
        const val SCHEMA_1 = "schemas/com.example.passwordmanager.data.CredentialDatabase/1.json"
    }
}

package com.example.passwordmanager.data.notes

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.passwordmanager.data.CredentialDatabase
import com.example.passwordmanager.data.likePattern
import com.example.passwordmanager.util.NOTE_BODY_MAX_CHARS
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NoteDaoTest {

    private lateinit var database: CredentialDatabase
    private lateinit var dao: NoteDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            CredentialDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.noteDao
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertUpdateDeleteRoundTripKeepsEveryField() = runTest {
        val note = note("Wifi", "router admin\npassword is on the box", createdAt = 1, updatedAt = 1)
        dao.upsertNote(note)
        val saved = dao.getAll().single()
        println("Saved $saved")
        assertEquals(note.copy(id = saved.id), saved)
        assertEquals(saved, dao.getById(saved.id))

        val edited = saved.copy(title = "Wifi 2", body = "new body", updatedAt = 9)
        dao.upsertNote(edited)
        assertEquals(edited, dao.getById(saved.id))
        assertEquals(1, dao.getAll().size)

        dao.deleteNote(edited)
        assertNull(dao.getById(saved.id))
        assertEquals(emptyList<NoteSummary>(), dao.search(likePattern("")).first())
    }

    @Test
    fun summariesAreNewestFirstWithAShortPreview() = runTest {
        val longBody = "x".repeat(NOTE_BODY_MAX_CHARS)
        dao.upsertNote(note("Old", "old body", updatedAt = 1))
        dao.upsertNote(note("Long", longBody, updatedAt = 3))
        dao.upsertNote(note("Middle", "middle body", updatedAt = 2))

        val summaries = dao.search(likePattern("")).first()

        println("Summaries ${summaries.map { it.title to it.preview.length }}")
        assertEquals(listOf("Long", "Middle", "Old"), summaries.map { it.title })
        assertEquals(NOTE_PREVIEW_CHARS, summaries[0].preview.length)
        assertEquals("middle body", summaries[1].preview)
        assertEquals(3L, summaries[0].updatedAt)
        assertEquals(longBody, dao.getById(summaries[0].id)?.body)
    }

    @Test
    fun previewCountsCharactersNotBytes() = runTest {
        val emoji = "🔑"
        dao.upsertNote(note("Keys", emoji.repeat(NOTE_PREVIEW_CHARS + 10)))

        val preview = dao.search(likePattern("")).first().single().preview

        assertEquals(emoji.repeat(NOTE_PREVIEW_CHARS), preview)
    }

    @Test
    fun editingANoteMovesItToTheTop() = runTest {
        dao.upsertNote(note("First", "a", updatedAt = 1))
        dao.upsertNote(note("Second", "b", updatedAt = 2))
        val first = dao.getAll().first { it.title == "First" }

        dao.upsertNote(first.copy(body = "a edited", updatedAt = 3))

        assertEquals(listOf("First", "Second"), dao.search(likePattern("")).first().map { it.title })
    }

    @Test
    fun searchMatchesTitleOrBodyIgnoringCaseAndTreatsWildcardsLiterally() = runTest {
        dao.upsertNote(note("Bank PINs", "card 1234", updatedAt = 1))
        dao.upsertNote(note("Wifi", "Router ADMIN password", updatedAt = 2))
        dao.upsertNote(note("Discount", "100% off_today", updatedAt = 3))
        dao.upsertNote(note("Plain", "100 off today", updatedAt = 4))

        val cases = mapOf(
            "bank" to listOf("Bank PINs"),
            "admin" to listOf("Wifi"),
            "  1234 " to listOf("Bank PINs"),
            "%" to listOf("Discount"),
            "_" to listOf("Discount"),
            "0% off_" to listOf("Discount"),
            "nothing here" to emptyList()
        )
        cases.forEach { (query, expected) ->
            val titles = dao.search(likePattern(query)).first().map { it.title }
            println("'$query' -> $titles")
            assertEquals("Query '$query'", expected, titles)
        }
    }

    @Test
    fun insertSkippingDuplicatesMatchesTitleAndBody() = runTest {
        dao.upsertNote(note("Wifi", "router"))

        val inserted = dao.insertSkippingDuplicates(
            listOf(
                note("Wifi", "router", createdAt = 5),
                note("Wifi", "router 2"),
                note("wifi", "router"),
                note("Wifi", "router 2")
            )
        )

        val all = dao.getAll()
        println("Inserted $inserted, stored ${all.map { it.title to it.body }}")
        assertEquals(2, inserted)
        assertEquals(listOf("Wifi" to "router", "Wifi" to "router 2", "wifi" to "router"), all.map { it.title to it.body })
        assertEquals(0, dao.insertSkippingDuplicates(all))
    }
}

fun note(title: String, body: String, createdAt: Long = 1, updatedAt: Long = createdAt) =
    Note(title = title, body = body, createdAt = createdAt, updatedAt = updatedAt)

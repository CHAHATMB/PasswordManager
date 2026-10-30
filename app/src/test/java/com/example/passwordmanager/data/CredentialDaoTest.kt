package com.example.passwordmanager.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CredentialDaoTest {

    private lateinit var database: CredentialDatabase
    private lateinit var dao: CredentialDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            CredentialDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.dao
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun upsertInsertsThenUpdates() = runTest {
        dao.upsertCredential(credential("github.com", dateCreated = 1))
        val inserted = dao.getOrderedByDateCreated().first().single()

        dao.upsertCredential(inserted.copy(password = "new-secret"))

        val updated = dao.getOrderedByDateCreated().first().single()
        assertEquals(inserted.id, updated.id)
        assertEquals("new-secret", updated.password)
    }

    @Test
    fun deleteRemovesOnlyThatCredential() = runTest {
        dao.upsertCredential(credential("github.com", dateCreated = 1))
        dao.upsertCredential(credential("example.com", dateCreated = 2))
        val github = dao.getOrderedByDateCreated().first().first()

        dao.deleteCredential(github)

        assertEquals(listOf("example.com"), dao.getOrderedByDateCreated().first().map { it.website })
    }

    @Test
    fun ordersByWebsiteAndByDateCreated() = runTest {
        dao.upsertCredential(credential("zeta.com", dateCreated = 1))
        dao.upsertCredential(credential("alpha.com", dateCreated = 3))
        dao.upsertCredential(credential("mid.com", dateCreated = 2))

        assertEquals(
            listOf("alpha.com", "mid.com", "zeta.com"),
            dao.getOrderedByWebsite().first().map { it.website }
        )
        assertEquals(
            listOf("zeta.com", "mid.com", "alpha.com"),
            dao.getOrderedByDateCreated().first().map { it.website }
        )
    }

    @Test
    fun favoritesQueryReturnsOnlyFavorites() = runTest {
        dao.upsertCredential(credential("fav.com", dateCreated = 1, isFav = true))
        dao.upsertCredential(credential("plain.com", dateCreated = 2))

        assertEquals(listOf("fav.com"), dao.getFavoriteCredential().first().map { it.website })
    }

    private fun credential(website: String, dateCreated: Long, isFav: Boolean = false) = Credential(
        website = website,
        username = "user@$website",
        password = "secret-$website",
        dataCreate = dateCreated,
        isFav = isFav,
        note = ""
    )
}

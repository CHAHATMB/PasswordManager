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
        val inserted = dao.search(ALL, favoritesOnly = false, CredentialSort.CREATED).first().single()

        dao.upsertCredential(inserted.copy(password = "new-secret"))

        val updated = dao.search(ALL, favoritesOnly = false, CredentialSort.CREATED).first().single()
        assertEquals(inserted.id, updated.id)
        assertEquals("new-secret", updated.password)
    }

    @Test
    fun deleteRemovesOnlyThatCredential() = runTest {
        dao.upsertCredential(credential("github.com", dateCreated = 1))
        dao.upsertCredential(credential("example.com", dateCreated = 2))
        val github = dao.search(ALL, favoritesOnly = false, CredentialSort.CREATED).first().first()

        dao.deleteCredential(github)

        assertEquals(listOf("example.com"), dao.search(ALL, favoritesOnly = false, CredentialSort.CREATED).first().map { it.website })
    }

    @Test
    fun ordersByWebsiteAndByDateCreated() = runTest {
        dao.upsertCredential(credential("zeta.com", dateCreated = 1))
        dao.upsertCredential(credential("alpha.com", dateCreated = 3))
        dao.upsertCredential(credential("mid.com", dateCreated = 2))

        assertEquals(
            listOf("alpha.com", "mid.com", "zeta.com"),
            dao.search(ALL, favoritesOnly = false, CredentialSort.WEBSITE).first().map { it.website }
        )
        assertEquals(
            listOf("zeta.com", "mid.com", "alpha.com"),
            dao.search(ALL, favoritesOnly = false, CredentialSort.CREATED).first().map { it.website }
        )
    }

    @Test
    fun favoritesQueryReturnsOnlyFavoritesInTheChosenOrder() = runTest {
        dao.upsertCredential(credential("zeta.com", dateCreated = 1, isFav = true))
        dao.upsertCredential(credential("plain.com", dateCreated = 2))
        dao.upsertCredential(credential("alpha.com", dateCreated = 3, isFav = true))

        assertEquals(listOf("zeta.com", "alpha.com"), dao.search(ALL, favoritesOnly = true, CredentialSort.CREATED).first().map { it.website })
        assertEquals(listOf("alpha.com", "zeta.com"), dao.search(ALL, favoritesOnly = true, CredentialSort.WEBSITE).first().map { it.website })
    }

    @Test
    fun searchMatchesWebsiteOrUsernameIgnoringCase() = runTest {
        dao.upsertCredential(credential("github.com", dateCreated = 1, username = "alice"))
        dao.upsertCredential(credential("gitlab.com", dateCreated = 2, username = "bob"))
        dao.upsertCredential(credential("example.org", dateCreated = 3, username = "GitFan"))

        assertEquals(listOf("github.com"), searchWebsites("HUB"))
        assertEquals(listOf("gitlab.com"), searchWebsites("Bo"))
        assertEquals(listOf("github.com", "gitlab.com", "example.org"), searchWebsites("git"))
        assertEquals(emptyList<String>(), searchWebsites("nomatch"))
    }

    @Test
    fun blankSearchReturnsEverything() = runTest {
        dao.upsertCredential(credential("github.com", dateCreated = 1))
        dao.upsertCredential(credential("example.org", dateCreated = 2))

        assertEquals(listOf("github.com", "example.org"), searchWebsites(""))
        assertEquals(listOf("github.com", "example.org"), searchWebsites("   "))
    }

    @Test
    fun searchTreatsLikeWildcardsLiterally() = runTest {
        dao.upsertCredential(credential("my_site.com", dateCreated = 1))
        dao.upsertCredential(credential("myxsite.com", dateCreated = 2))
        dao.upsertCredential(credential("100%.com", dateCreated = 3))

        assertEquals(listOf("my_site.com"), searchWebsites("my_"))
        assertEquals(listOf("100%.com"), searchWebsites("%"))
        assertEquals(emptyList<String>(), searchWebsites("\\"))
    }

    @Test
    fun getByIdEmitsTheCredentialThenNullAfterDelete() = runTest {
        dao.upsertCredential(credential("github.com", dateCreated = 1))
        val saved = dao.search(ALL, favoritesOnly = false, CredentialSort.CREATED).first().single()

        assertEquals(saved, dao.getById(saved.id).first())
        dao.deleteCredential(saved)
        assertEquals(null, dao.getById(saved.id).first())
    }

    @Test
    fun insertSkippingDuplicatesMatchesWebsiteAndUsernameIgnoringCase() = runTest {
        dao.upsertCredential(credential("github.com", dateCreated = 1, username = "alice"))

        val inserted = dao.insertSkippingDuplicates(
            listOf(
                credential("GitHub.com", dateCreated = 2, username = "ALICE"),
                credential("github.com", dateCreated = 3, username = "bob"),
                credential("gitlab.com", dateCreated = 4, username = "alice"),
                credential(" GITLAB.COM", dateCreated = 5, username = "Alice")
            )
        )

        assertEquals(2, inserted)
        assertEquals(
            listOf("github.com/alice", "github.com/bob", "gitlab.com/alice"),
            dao.getAll().map { "${it.website}/${it.username}" }
        )
    }

    @Test
    fun insertingTheSameCredentialsAgainInsertsNone() = runTest {
        val credentials = listOf(credential("github.com", dateCreated = 1), credential("example.org", dateCreated = 2))

        assertEquals(2, dao.insertSkippingDuplicates(credentials))
        assertEquals(0, dao.insertSkippingDuplicates(credentials))
        assertEquals(2, dao.getAll().size)
    }

    private suspend fun searchWebsites(query: String): List<String> {
        val websites = dao.search(likePattern(query), favoritesOnly = false, CredentialSort.CREATED).first().map { it.website }
        println("search(\"$query\") -> $websites")
        return websites
    }

    private fun credential(
        website: String,
        dateCreated: Long,
        isFav: Boolean = false,
        username: String = "user@$website"
    ) = Credential(
        website = website,
        username = username,
        password = "secret-$website",
        dataCreate = dateCreated,
        isFav = isFav,
        note = ""
    )

    private companion object {
        val ALL = likePattern("")
    }
}

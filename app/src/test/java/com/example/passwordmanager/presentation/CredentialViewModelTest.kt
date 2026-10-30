package com.example.passwordmanager.presentation

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.passwordmanager.data.Credential
import com.example.passwordmanager.data.CredentialDatabase
import com.example.passwordmanager.data.CredentialRepository
import com.example.passwordmanager.data.CredentialSort
import com.example.passwordmanager.data.crypto.Pbkdf2KeyDerivation
import com.example.passwordmanager.data.crypto.TEST_KDF_PARAMS
import com.example.passwordmanager.data.crypto.VaultCrypto
import com.example.passwordmanager.data.vault.VaultHeaderStore
import com.example.passwordmanager.data.vault.VaultSession
import com.example.passwordmanager.util.ClipboardAccess
import com.example.passwordmanager.util.SecureClipboard
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CredentialViewModelTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val openedDatabases = mutableListOf<CredentialDatabase>()
    private val session = VaultSession(
        headerStore = VaultHeaderStore(context.getSharedPreferences(VaultHeaderStore.PREFS_NAME, Context.MODE_PRIVATE)),
        crypto = VaultCrypto(Pbkdf2KeyDerivation()),
        kdfParams = TEST_KDF_PARAMS,
        openDatabase = {
            Room.databaseBuilder(context, CredentialDatabase::class.java, TEST_DB_NAME)
                .allowMainThreadQueries()
                .build()
                .also { openedDatabases += it }
        },
        deleteDatabases = { context.deleteDatabase(TEST_DB_NAME) },
        deleteBiometricKey = {}
    )
    private val repository = CredentialRepository(session)
    private val draft = CredentialDraft(website = "github.com", username = "alice", password = "gh-secret-1")

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        openedDatabases.forEach { it.close() }
        context.deleteDatabase(TEST_DB_NAME)
    }

    @Test
    fun addDraftKeepsInputUntilTheSheetCloses() = runTest {
        val viewModel = newUnlockedViewModel()
        assertNull(viewModel.addDraft)

        viewModel.onEvent(CredentialEvent.OpenAddSheet)
        assertEquals(CredentialDraft(), viewModel.addDraft)

        viewModel.onEvent(CredentialEvent.ChangeAddDraft(draft))
        assertEquals(draft, viewModel.addDraft)

        viewModel.onEvent(CredentialEvent.CloseAddSheet)
        assertNull(viewModel.addDraft)
    }

    @Test
    fun changeAfterCloseDoesNotReopenTheSheet() = runTest {
        val viewModel = newUnlockedViewModel()
        viewModel.onEvent(CredentialEvent.OpenAddSheet)
        viewModel.onEvent(CredentialEvent.CloseAddSheet)

        viewModel.onEvent(CredentialEvent.ChangeAddDraft(draft))

        assertNull(viewModel.addDraft)
    }

    @Test
    fun lockDiscardsTheAddDraft() = runTest {
        val viewModel = newUnlockedViewModel()
        viewModel.onEvent(CredentialEvent.OpenAddSheet)
        viewModel.onEvent(CredentialEvent.ChangeAddDraft(draft))

        session.lock()

        assertNull(viewModel.addDraft)
    }

    @Test
    fun saveSetsDateCreatedAndUpdatedAtToTheClockTime() = runTest {
        val viewModel = newUnlockedViewModel()

        viewModel.onEvent(CredentialEvent.SaveCredential("github.com", "alice", "gh-secret-1", note = null))

        val saved = viewModel.awaitCredentials { it.isNotEmpty() }.single()
        println("Saved: dataCreate=${saved.dataCreate} updatedAt=${saved.updatedAt}")
        assertEquals(NOW, saved.dataCreate)
        assertEquals(NOW, saved.updatedAt)
    }

    @Test
    fun updateSetsUpdatedAtToTheClockTimeAndKeepsDateCreated() = runTest {
        val viewModel = newUnlockedViewModel()
        repository.upsertCredential(credential("github.com", dateCreated = 1))
        val original = viewModel.awaitCredentials { it.isNotEmpty() }.single()

        viewModel.onEvent(CredentialEvent.UpdateCredential(original.copy(password = "new-secret")))

        val edited = viewModel.awaitCredentials { it.single().password == "new-secret" }.single()
        println("Edited: dataCreate=${edited.dataCreate} updatedAt=${edited.updatedAt}")
        assertEquals(1L, edited.dataCreate)
        assertEquals(NOW, edited.updatedAt)
    }

    @Test
    fun favouriteToggleFlipsIsFavAndKeepsUpdatedAt() = runTest {
        val viewModel = newUnlockedViewModel()
        repository.upsertCredential(credential("github.com", dateCreated = 1, updatedAt = 5))
        val original = viewModel.awaitCredentials { it.isNotEmpty() }.single()

        viewModel.onEvent(CredentialEvent.FavoriteCredential(original))

        val toggled = viewModel.awaitCredentials { it.single().isFav }.single()
        println("Toggled: isFav=${toggled.isFav} updatedAt=${toggled.updatedAt}")
        assertEquals(5L, toggled.updatedAt)
    }

    @Test
    fun setSortAndToggleFavoritesOnlyChangeTheEmittedList() = runTest {
        val viewModel = newUnlockedViewModel()
        repository.upsertCredential(credential("zeta.com", dateCreated = 1, isFav = true))
        repository.upsertCredential(credential("alpha.com", dateCreated = 2))
        repository.upsertCredential(credential("mid.com", dateCreated = 3, isFav = true, updatedAt = 50))
        val all = listOf("zeta.com", "alpha.com", "mid.com")
        viewModel.awaitWebsites(all)

        viewModel.onEvent(CredentialEvent.SetSort(CredentialSort.WEBSITE))
        assertEquals(CredentialSort.WEBSITE, viewModel.sort.value)
        viewModel.awaitWebsites(listOf("alpha.com", "mid.com", "zeta.com"))

        viewModel.onEvent(CredentialEvent.SetSort(CredentialSort.MODIFIED))
        viewModel.awaitWebsites(listOf("mid.com", "alpha.com", "zeta.com"))

        viewModel.onEvent(CredentialEvent.SetSort(CredentialSort.CREATED))
        viewModel.onEvent(CredentialEvent.ToggleFavoritesOnly)
        assertTrue(viewModel.favoritesOnly.value)
        viewModel.awaitWebsites(listOf("zeta.com", "mid.com"))

        viewModel.onEvent(CredentialEvent.ToggleFavoritesOnly)
        viewModel.awaitWebsites(all)
    }

    /** Waits for the list the database emits after an event, since writes and queries run off the test thread. */
    private suspend fun CredentialViewModel.awaitCredentials(predicate: (List<Credential>) -> Boolean): List<Credential> =
        state.first { it.isLoaded && predicate(it.credentials) }.credentials

    private suspend fun CredentialViewModel.awaitWebsites(expected: List<String>) {
        val websites = awaitCredentials { list -> list.map { it.website } == expected }.map { it.website }
        println("sort=${sort.value} favoritesOnly=${favoritesOnly.value} -> $websites")
    }

    private suspend fun TestScope.newUnlockedViewModel(): CredentialViewModel {
        session.setup("correct horse battery staple".toCharArray())
        return CredentialViewModel(
            repository,
            SecureClipboard(NoClipboard, backgroundScope),
            session.state,
            clock = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC)
        )
    }

    private fun credential(website: String, dateCreated: Long, isFav: Boolean = false, updatedAt: Long = 0) =
        Credential(
            website = website,
            username = "user@$website",
            password = "secret-$website",
            dataCreate = dateCreated,
            isFav = isFav,
            note = "",
            updatedAt = updatedAt
        )

    private object NoClipboard : ClipboardAccess {
        override fun primaryClipLabel(): CharSequence? = null
        override fun setSensitiveClip(label: String, text: String) = Unit
        override fun clear() = Unit
    }

    private companion object {
        const val TEST_DB_NAME = "credential_view_model_test.db"
        const val NOW = 1_700_000_000_000L
    }
}

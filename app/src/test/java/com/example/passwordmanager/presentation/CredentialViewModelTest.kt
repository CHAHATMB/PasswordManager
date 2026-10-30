package com.example.passwordmanager.presentation

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.passwordmanager.data.CredentialDatabase
import com.example.passwordmanager.data.CredentialRepository
import com.example.passwordmanager.data.crypto.Pbkdf2KeyDerivation
import com.example.passwordmanager.data.crypto.TEST_KDF_PARAMS
import com.example.passwordmanager.data.crypto.VaultCrypto
import com.example.passwordmanager.data.vault.VaultHeaderStore
import com.example.passwordmanager.data.vault.VaultSession
import com.example.passwordmanager.util.ClipboardAccess
import com.example.passwordmanager.util.SecureClipboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private suspend fun TestScope.newUnlockedViewModel(): CredentialViewModel {
        session.setup("correct horse battery staple".toCharArray())
        return CredentialViewModel(
            CredentialRepository(session),
            SecureClipboard(NoClipboard, backgroundScope),
            session.state
        )
    }

    private object NoClipboard : ClipboardAccess {
        override fun primaryClipLabel(): CharSequence? = null
        override fun setSensitiveClip(label: String, text: String) = Unit
        override fun clear() = Unit
    }

    private companion object {
        const val TEST_DB_NAME = "credential_view_model_test.db"
    }
}

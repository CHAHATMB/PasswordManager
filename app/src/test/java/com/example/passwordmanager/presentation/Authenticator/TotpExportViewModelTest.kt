package com.example.passwordmanager.presentation.Authenticator

import android.content.Context
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.passwordmanager.data.CredentialDatabase
import com.example.passwordmanager.data.crypto.Pbkdf2KeyDerivation
import com.example.passwordmanager.data.crypto.TEST_KDF_PARAMS
import com.example.passwordmanager.data.crypto.VaultCrypto
import com.example.passwordmanager.data.totp.TotpRepository
import com.example.passwordmanager.data.totp.totpAccount
import com.example.passwordmanager.data.vault.VaultHeaderStore
import com.example.passwordmanager.data.vault.VaultSession
import com.example.passwordmanager.util.totp.OtpAuthResult
import com.example.passwordmanager.util.totp.OtpAuthUri
import com.example.passwordmanager.util.totp.TotpAlgorithm
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TotpExportViewModelTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val headerStore = VaultHeaderStore(
        context.getSharedPreferences(VaultHeaderStore.PREFS_NAME, Context.MODE_PRIVATE)
    )
    private val openedDatabases = mutableListOf<CredentialDatabase>()
    private val password = "correct horse battery staple"
    private val encodedTexts: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val example = totpAccount("Example", "alice@example.com", "JBSWY3DPEHPK3PXP", createdAt = 1)
    private val acme = totpAccount("ACME Co", "bob", "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", TotpAlgorithm.SHA512, 8, createdAt = 2)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        openedDatabases.forEach { it.close() }
    }

    @Test
    fun passwordIsIgnoredUntilTheWarningIsConfirmed() = runTest {
        val viewModel = newViewModel(unlockedSessionWithAccounts())

        viewModel.onEvent(TotpExportEvent.VerifyPassword(password))
        viewModel.onEvent(TotpExportEvent.Next)

        assertEquals(TotpExportStep.Warning, viewModel.state.value)
        assertTrue(encodedTexts.isEmpty())
    }

    @Test
    fun wrongPasswordNeverExposesAccounts() = runTest {
        val viewModel = newViewModel(unlockedSessionWithAccounts())
        viewModel.onEvent(TotpExportEvent.ConfirmWarning)
        assertEquals(TotpExportStep.Password(), viewModel.state.value)

        viewModel.onEvent(TotpExportEvent.VerifyPassword("wrong password!!"))

        val step = viewModel.state.first { it != TotpExportStep.Verifying }
        println("After wrong password: $step, encoded: ${encodedTexts.size}")
        assertEquals(TotpExportStep.Password(isWrongPassword = true), step)
        viewModel.onEvent(TotpExportEvent.Next)
        viewModel.onEvent(TotpExportEvent.ToggleKey)
        assertEquals(TotpExportStep.Password(isWrongPassword = true), viewModel.state.value)
        assertTrue(encodedTexts.isEmpty())
    }

    @Test
    fun correctPasswordShowsOneQrCodePerAccountWithNextAndPrevious() = runTest {
        val viewModel = newViewModel(unlockedSessionWithAccounts())
        viewModel.onEvent(TotpExportEvent.ConfirmWarning)

        viewModel.onEvent(TotpExportEvent.VerifyPassword(password))
        val first = renderedPage(viewModel)
        println("Page 1: ${first.account} ${first.index + 1}/${first.count}, warning=${first.hasCompatibilityWarning}")
        assertEquals(listOf("Example", 0, 2, false, false), listOf(first.account.issuer, first.index, first.count, first.isKeyRevealed, first.hasCompatibilityWarning))

        viewModel.onEvent(TotpExportEvent.Next)
        val second = renderedPage(viewModel)
        assertEquals(listOf("ACME Co", 1, true), listOf(second.account.issuer, second.index, second.hasCompatibilityWarning))

        viewModel.onEvent(TotpExportEvent.Next)
        assertEquals("Next on the last page stays", 1, currentPage(viewModel).index)

        viewModel.onEvent(TotpExportEvent.ToggleKey)
        assertEquals("GEZD GNBV GY3T QOJQ GEZD GNBV GY3T QOJQ", currentPage(viewModel).takeIf { it.isKeyRevealed }?.groupedKey)

        viewModel.onEvent(TotpExportEvent.Previous)
        val back = renderedPage(viewModel)
        assertEquals("Key is hidden again on a new page", listOf(0, false), listOf(back.index, back.isKeyRevealed))

        println("Encoded: $encodedTexts")
        assertEquals(3, encodedTexts.size)
        listOf(example, acme, example).zip(encodedTexts).forEach { (expected, text) ->
            val parsed = (OtpAuthUri.parse(text) as OtpAuthResult.Totp).account
            assertEquals(
                listOf(expected.issuer, expected.accountName, expected.secret, expected.algorithm, expected.digits, expected.period),
                listOf(parsed.issuer, parsed.accountName, parsed.secret, parsed.algorithm, parsed.digits, parsed.period)
            )
        }
    }

    @Test
    fun lockClearsTheShownAccountsAndRequiresReauthentication() = runTest {
        val session = unlockedSessionWithAccounts()
        val viewModel = newViewModel(session)
        viewModel.onEvent(TotpExportEvent.ConfirmWarning)
        viewModel.onEvent(TotpExportEvent.VerifyPassword(password))
        renderedPage(viewModel)

        session.lock()

        assertEquals(TotpExportStep.Warning, viewModel.state.value)
        viewModel.onEvent(TotpExportEvent.Next)
        assertEquals(TotpExportStep.Warning, viewModel.state.value)
        viewModel.onEvent(TotpExportEvent.ConfirmWarning)
        viewModel.onEvent(TotpExportEvent.VerifyPassword(password))
        assertFalse("Locked: nothing is verified or shown", viewModel.state.value is TotpExportStep.Showing)
    }

    @Test
    fun emptyVaultShowsNoAccounts() = runTest {
        val session = newSession()
        session.setup(password.toCharArray())
        val viewModel = newViewModel(session)
        viewModel.onEvent(TotpExportEvent.ConfirmWarning)

        viewModel.onEvent(TotpExportEvent.VerifyPassword(password))

        assertEquals(TotpExportStep.NoAccounts, viewModel.state.first { it != TotpExportStep.Verifying })
    }

    private suspend fun renderedPage(viewModel: TotpExportViewModel): TotpExportPage =
        (viewModel.state.first { it is TotpExportStep.Showing && it.page.qrCode != null } as TotpExportStep.Showing).page

    private fun currentPage(viewModel: TotpExportViewModel) = (viewModel.state.value as TotpExportStep.Showing).page

    private suspend fun unlockedSessionWithAccounts(): VaultSession {
        val session = newSession()
        session.setup(password.toCharArray())
        TotpRepository(session).insertSkippingDuplicates(listOf(example, acme))
        return session
    }

    private fun newViewModel(session: VaultSession) = TotpExportViewModel(session, TotpRepository(session)) { text ->
        encodedTexts += text
        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }

    private fun newSession() = VaultSession(
        headerStore = headerStore,
        crypto = VaultCrypto(Pbkdf2KeyDerivation()),
        kdfParams = TEST_KDF_PARAMS,
        openDatabase = {
            Room.inMemoryDatabaseBuilder(context, CredentialDatabase::class.java)
                .allowMainThreadQueries()
                .build()
                .also { openedDatabases += it }
        },
        deleteDatabases = {},
        deleteBiometricKey = {}
    )
}

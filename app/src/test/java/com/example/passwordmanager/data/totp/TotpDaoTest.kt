package com.example.passwordmanager.data.totp

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.passwordmanager.data.CredentialDatabase
import com.example.passwordmanager.util.totp.OtpAuthAccount
import com.example.passwordmanager.util.totp.OtpAuthResult
import com.example.passwordmanager.util.totp.TotpAlgorithm
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TotpDaoTest {

    private lateinit var database: CredentialDatabase
    private lateinit var dao: TotpDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            CredentialDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.totpDao
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertUpdateDeleteRoundTripKeepsEveryField() = runTest {
        val account = totpAccount("ACME Co", "bob", GEZ_SECRET, TotpAlgorithm.SHA256, digits = 8, period = 60, createdAt = 5)
        dao.upsertAccount(account)
        val saved = dao.getAccounts().first().single()
        println("Saved $saved")
        assertEquals(account.copy(id = saved.id), saved)

        dao.upsertAccount(saved.copy(issuer = "ACME", accountName = "bob@acme.test"))
        val edited = dao.getAccounts().first().single()
        assertEquals(saved.copy(issuer = "ACME", accountName = "bob@acme.test"), edited)

        dao.deleteAccount(edited)
        assertEquals(emptyList<TotpAccount>(), dao.getAccounts().first())
    }

    @Test
    fun accountsAreListedOldestFirst() = runTest {
        dao.upsertAccount(totpAccount("Late", "a", JBS_SECRET, createdAt = 3))
        dao.upsertAccount(totpAccount("Early", "b", GEZ_SECRET, createdAt = 1))

        assertEquals(listOf("Early", "Late"), dao.getAccounts().first().map { it.issuer })
    }

    @Test
    fun insertSkippingDuplicatesMatchesTheNormalisedSecret() = runTest {
        dao.upsertAccount(totpAccount("Example", "alice", JBS_SECRET, createdAt = 1))

        val inserted = dao.insertSkippingDuplicates(
            listOf(
                // Same key typed differently: normalised to the same secret.
                totpAccount("Other name", "alice", "jbsw y3dp ehpk 3pxp", createdAt = 2),
                totpAccount("ACME Co", "bob", GEZ_SECRET, createdAt = 3),
                totpAccount("ACME again", "bob", GEZ_SECRET, createdAt = 4)
            )
        )

        val all = dao.getAll()
        println("Inserted $inserted, stored ${all.map { it.issuer }}")
        assertEquals(1, inserted)
        assertEquals(listOf("Example", "ACME Co"), all.map { it.issuer })
        assertEquals(0, dao.insertSkippingDuplicates(all))
    }

    @Test
    fun storedAccountGeneratesTheReferenceCode() = runTest {
        dao.upsertAccount(totpAccount("Example", "alice", JBS_SECRET, createdAt = 1))

        assertEquals("996554", dao.getAll().single().codeAt(59))
    }

    private companion object {
        const val JBS_SECRET = "JBSWY3DPEHPK3PXP"
        const val GEZ_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
    }
}

fun totpAccount(
    issuer: String,
    accountName: String,
    secret: String,
    algorithm: TotpAlgorithm = TotpAlgorithm.SHA1,
    digits: Int = 6,
    period: Int = 30,
    createdAt: Long = 1
): TotpAccount {
    val result = OtpAuthAccount.create(issuer, accountName, secret, algorithm, digits, period)
    return (result as OtpAuthResult.Totp).account.toTotpAccount(createdAt)
}

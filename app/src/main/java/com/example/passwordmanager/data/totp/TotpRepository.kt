package com.example.passwordmanager.data.totp

import com.example.passwordmanager.data.vault.VaultException
import com.example.passwordmanager.data.vault.VaultSession
import com.example.passwordmanager.data.whileUnlocked
import com.example.passwordmanager.data.write
import kotlinx.coroutines.flow.Flow

class TotpRepository(private val session: VaultSession) {

    /** Oldest first; empty while locked. */
    fun getAccounts(): Flow<List<TotpAccount>> = session.whileUnlocked(emptyList()) { it.totpDao.getAccounts() }

    /** Throws [VaultException.Locked] when locked, including a lock that closes the DB mid-write. */
    suspend fun upsertAccount(account: TotpAccount) = session.write { it.totpDao.upsertAccount(account) }

    /** Throws [VaultException.Locked] when locked, including a lock that closes the DB mid-write. */
    suspend fun deleteAccount(account: TotpAccount) = session.write { it.totpDao.deleteAccount(account) }

    /** Throws [VaultException.Locked] when locked. */
    suspend fun getAll(): List<TotpAccount> = session.write { it.totpDao.getAll() }

    /**
     * Inserts in one transaction, skipping accounts whose secret is already saved. Returns how many
     * were inserted. Throws [VaultException.Locked] when locked.
     */
    suspend fun insertSkippingDuplicates(accounts: List<TotpAccount>): Int =
        session.write { it.totpDao.insertSkippingDuplicates(accounts) }
}

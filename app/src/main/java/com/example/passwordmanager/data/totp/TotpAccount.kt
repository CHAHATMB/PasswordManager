package com.example.passwordmanager.data.totp

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.passwordmanager.util.totp.Base32
import com.example.passwordmanager.util.totp.OtpAuthAccount
import com.example.passwordmanager.util.totp.Totp
import com.example.passwordmanager.util.totp.TotpAlgorithm

/** [secret] is normalised Base32, already validated: build new instances with [toTotpAccount]. */
@Entity
data class TotpAccount(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val issuer: String,
    val accountName: String,
    val secret: String,
    val algorithm: TotpAlgorithm,
    val digits: Int,
    val period: Int,
    val createdAt: Long
) {
    /** The issuer, or the account name when there is no issuer. */
    val title: String get() = issuer.ifBlank { accountName }

    fun codeAt(unixSeconds: Long): String {
        val key = checkNotNull(Base32.decode(secret)) { "Stored secret is not Base32" }
        try {
            return Totp.generate(key, unixSeconds, algorithm, digits, period)
        } finally {
            key.fill(0)
        }
    }

    fun secondsRemaining(unixSeconds: Long): Int = Totp.secondsRemaining(unixSeconds, period)

    override fun toString() = "TotpAccount(id=$id, issuer=$issuer, accountName=$accountName)"
}

fun OtpAuthAccount.toTotpAccount(createdAt: Long) = TotpAccount(
    issuer = issuer,
    accountName = accountName,
    secret = secret,
    algorithm = algorithm,
    digits = digits,
    period = period,
    createdAt = createdAt
)

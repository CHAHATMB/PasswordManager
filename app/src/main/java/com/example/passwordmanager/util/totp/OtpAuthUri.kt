package com.example.passwordmanager.util.totp

import java.net.URLDecoder
import java.net.URLEncoder

private const val SCHEME = "otpauth://"
private const val TYPE_TOTP = "totp"
private const val TYPE_HOTP = "hotp"
// Scanned text is untrusted; a real otpauth URI is a few hundred characters.
private const val MAX_URI_LENGTH = 4096
private const val UTF_8 = "UTF-8"

/** A validated TOTP setup. [secret] is normalised Base32: uppercase, no separators or padding. */
class OtpAuthAccount private constructor(
    val issuer: String,
    val accountName: String,
    val secret: String,
    val algorithm: TotpAlgorithm,
    val digits: Int,
    val period: Int
) {
    override fun toString() = "OtpAuthAccount(issuer=$issuer, accountName=$accountName)"

    companion object {
        /** The one place that checks a TOTP setup, for scanned URIs, manual entry and backups alike. */
        fun create(
            issuer: String,
            accountName: String,
            secret: String,
            algorithm: TotpAlgorithm,
            digits: Int,
            period: Int
        ): OtpAuthResult {
            if (secret.isBlank()) return OtpAuthResult.MissingSecret
            val key = Base32.decode(secret)
            if (key == null || key.size !in TOTP_SECRET_BYTES_RANGE) return OtpAuthResult.InvalidSecret
            if (digits !in TOTP_DIGITS || period !in TOTP_PERIOD_RANGE) return OtpAuthResult.InvalidParameter
            val normalized = Base32.encode(key).trimEnd('=')
            key.fill(0)
            return OtpAuthResult.Totp(OtpAuthAccount(issuer.trim(), accountName.trim(), normalized, algorithm, digits, period))
        }
    }
}

sealed interface OtpAuthResult {
    class Totp(val account: OtpAuthAccount) : OtpAuthResult
    /** Not an `otpauth://` URI at all. */
    data object NotOtpAuth : OtpAuthResult
    /** A counter-based `otpauth://hotp` URI. */
    data object UnsupportedType : OtpAuthResult
    data object MissingSecret : OtpAuthResult
    data object InvalidSecret : OtpAuthResult
    /** Unknown type, algorithm, digits or period, or broken percent-encoding. */
    data object InvalidParameter : OtpAuthResult
}

/**
 * Splits a decoded `Issuer:account` label into `(issuer, account)`; a non-blank [issuer] wins over
 * the label prefix. When the label starts with that issuer, only that prefix is removed, so an issuer
 * containing `:` survives a build → parse round trip.
 */
internal fun splitLabel(label: String, issuer: String): Pair<String, String> {
    if (issuer.isNotBlank()) {
        val prefix = "$issuer:"
        return issuer to if (label.startsWith(prefix)) label.removePrefix(prefix) else label.substringAfter(':')
    }
    return (if (label.contains(':')) label.substringBefore(':') else "") to label.substringAfter(':')
}

/** Google's "Key Uri Format": `otpauth://totp/Issuer:account?secret=...&issuer=...`. */
object OtpAuthUri {

    fun parse(text: String): OtpAuthResult {
        val uri = text.trim()
        if (uri.length > MAX_URI_LENGTH || !uri.startsWith(SCHEME, ignoreCase = true)) return OtpAuthResult.NotOtpAuth
        // Split by hand rather than with java.net.URI, which rejects the raw spaces some sites put in labels.
        val rest = uri.substring(SCHEME.length).substringBefore('#')
        val type = rest.substringBefore('/').substringBefore('?').lowercase()
        when (type) {
            TYPE_TOTP -> Unit
            TYPE_HOTP -> return OtpAuthResult.UnsupportedType
            else -> return OtpAuthResult.InvalidParameter
        }
        val path = rest.substringBefore('?')
        val rawLabel = if (path.contains('/')) path.substringAfter('/') else ""
        val query = if (rest.contains('?')) rest.substringAfter('?') else ""

        val label: String
        val parameters: Map<String, String>
        try {
            label = decode(rawLabel)
            parameters = query.split('&')
                .filter { it.isNotEmpty() }
                .associate { decode(it.substringBefore('=')).lowercase() to decode(it.substringAfter('=', "")) }
        } catch (e: IllegalArgumentException) {
            return OtpAuthResult.InvalidParameter
        }

        val (issuer, accountName) = splitLabel(label, parameters["issuer"].orEmpty())
        val algorithm = parameters["algorithm"]?.let { TotpAlgorithm.fromName(it) ?: return OtpAuthResult.InvalidParameter }
            ?: DEFAULT_TOTP_ALGORITHM
        val digits = parameters["digits"]?.let { it.toIntOrNull() ?: return OtpAuthResult.InvalidParameter }
            ?: DEFAULT_TOTP_DIGITS
        val period = parameters["period"]?.let { it.toIntOrNull() ?: return OtpAuthResult.InvalidParameter }
            ?: DEFAULT_TOTP_PERIOD
        return OtpAuthAccount.create(issuer, accountName, parameters["secret"].orEmpty(), algorithm, digits, period)
    }

    fun build(account: OtpAuthAccount): String {
        val label = if (account.issuer.isBlank()) encode(account.accountName) else "${encode(account.issuer)}:${encode(account.accountName)}"
        val issuer = if (account.issuer.isBlank()) "" else "&issuer=${encode(account.issuer)}"
        return "$SCHEME$TYPE_TOTP/$label?secret=${account.secret}$issuer" +
            "&algorithm=${account.algorithm.name}&digits=${account.digits}&period=${account.period}"
    }

    private fun decode(text: String): String = URLDecoder.decode(text, UTF_8)

    // URLEncoder writes spaces as `+`, which some authenticator apps show literally.
    private fun encode(text: String): String = URLEncoder.encode(text, UTF_8).replace("+", "%20")
}

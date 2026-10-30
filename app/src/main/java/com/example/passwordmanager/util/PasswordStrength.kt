package com.example.passwordmanager.util

import kotlin.math.abs
import kotlin.math.log2

private const val FAIR_BITS = 40.0
private const val GOOD_BITS = 60.0
private const val STRONG_BITS = 80.0

// Printable ASCII minus letters and digits; any other character also counts as a symbol.
private const val SYMBOL_POOL_SIZE = 33

// Long random passwords repeat characters by chance, so only short-range repeats (`aaa`, `abab`) count.
private const val REPEAT_WINDOW = 3

enum class PasswordStrength {
    Weak, Fair, Good, Strong;

    companion object {
        fun of(password: String): PasswordStrength {
            val bits = entropyBits(password)
            return when {
                bits < FAIR_BITS -> Weak
                bits < GOOD_BITS -> Fair
                bits < STRONG_BITS -> Good
                else -> Strong
            }
        }
    }
}

/**
 * Bits for a random pick from the pools the password uses, per counted character. A character
 * that appeared within the previous [REPEAT_WINDOW] characters, or that extends a run of three or
 * more one-step characters (the `c` in `abc`, the `1` in `321`), adds nothing.
 */
private fun entropyBits(password: String): Double {
    var poolSize = 0
    if (password.any { it in 'a'..'z' }) poolSize += 26
    if (password.any { it in 'A'..'Z' }) poolSize += 26
    if (password.any { it in '0'..'9' }) poolSize += 10
    if (password.any { it !in 'a'..'z' && it !in 'A'..'Z' && it !in '0'..'9' }) poolSize += SYMBOL_POOL_SIZE
    if (poolSize == 0) return 0.0

    val counted = password.indices.count { index ->
        val isRepeat = (maxOf(0, index - REPEAT_WINDOW) until index).any { password[it] == password[index] }
        val step = if (index > 1) password[index] - password[index - 1] else 0
        val continuesSequence = abs(step) == 1 && step == password[index - 1] - password[index - 2]
        !isRepeat && !continuesSequence
    }
    return counted * log2(poolSize.toDouble())
}

package com.phantom.ghost.auth

import android.util.Base64
import java.security.SecureRandom
import java.security.spec.KeySpec
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

enum class AuthError { INVALID_EMAIL, WEAK_PASSWORD, USER_ALREADY_EXISTS, INVALID_CREDENTIALS, INVALID_VERIFICATION_CODE, EXPIRED_CODE, NOT_VERIFIED }

sealed interface AuthResult {
    data class Success(val userId: String, val session: DevSession) : AuthResult
    data class Failure(val error: AuthError) : AuthResult
}

data class DevSession(val userId: String, val opaqueToken: String, val expiresAtMillis: Long)

/** Development-only delivery adapter. Replace with Firebase/Supabase email delivery in production. */
fun interface VerificationMailer {
    fun deliver(email: String, code: String)
}

/**
 * In-memory mock auth for UI development only.
 * opaqueToken is NOT a JWT. Do not ship this implementation as production authentication.
 */
class DevMockAuthRepository(
    private val mailer: VerificationMailer,
    private val now: () -> Long = System::currentTimeMillis
) {
    private data class Account(
        val userId: String,
        val salt: ByteArray,
        val passwordHash: ByteArray,
        var verified: Boolean = false,
        var code: String? = null,
        var codeExpiresAt: Long = 0L
    )

    private val accounts = ConcurrentHashMap<String, Account>()
    private val random = SecureRandom()

    fun signUp(emailInput: String, password: CharArray): AuthResult {
        val email = emailInput.trim().lowercase()
        if (!email.matches(Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))) return AuthResult.Failure(AuthError.INVALID_EMAIL)
        if (password.size < 10) return AuthResult.Failure(AuthError.WEAK_PASSWORD)
        if (accounts.containsKey(email)) return AuthResult.Failure(AuthError.USER_ALREADY_EXISTS)

        val salt = ByteArray(16).also(random::nextBytes)
        val hash = derive(password, salt)
        val code = (100000 + random.nextInt(900000)).toString()
        val account = Account("user_${randomToken(12)}", salt, hash, code = code, codeExpiresAt = now() + 10 * 60_000L)
        if (accounts.putIfAbsent(email, account) != null) return AuthResult.Failure(AuthError.USER_ALREADY_EXISTS)
        mailer.deliver(email, code)
        password.fill(0.toChar())
        return AuthResult.Failure(AuthError.NOT_VERIFIED)
    }

    fun verifyEmail(emailInput: String, codeInput: String): AuthResult {
        val account = accounts[emailInput.trim().lowercase()] ?: return AuthResult.Failure(AuthError.INVALID_CREDENTIALS)
        if (account.codeExpiresAt < now()) return AuthResult.Failure(AuthError.EXPIRED_CODE)
        if (account.code != codeInput.trim() || !codeInput.matches(Regex("^\\d{6}$"))) {
            return AuthResult.Failure(AuthError.INVALID_VERIFICATION_CODE)
        }
        account.verified = true
        account.code = null
        return AuthResult.Success(account.userId, session(account.userId))
    }

    fun logIn(emailInput: String, password: CharArray): AuthResult {
        val account = accounts[emailInput.trim().lowercase()] ?: run {
            password.fill(0.toChar())
            return AuthResult.Failure(AuthError.INVALID_CREDENTIALS)
        }
        val candidate = derive(password, account.salt)
        password.fill(0.toChar())
        if (!constantTimeEquals(candidate, account.passwordHash)) return AuthResult.Failure(AuthError.INVALID_CREDENTIALS)
        if (!account.verified) return AuthResult.Failure(AuthError.NOT_VERIFIED)
        return AuthResult.Success(account.userId, session(account.userId))
    }

    private fun session(userId: String) = DevSession(userId, randomToken(32), now() + 24 * 60 * 60_000L)

    private fun derive(password: CharArray, salt: ByteArray): ByteArray {
        val spec: KeySpec = PBEKeySpec(password, salt, 120_000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun randomToken(bytes: Int): String =
        ByteArray(bytes).also(random::nextBytes).let { Base64.encodeToString(it, Base64.NO_WRAP or Base64.URL_SAFE) }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }
}

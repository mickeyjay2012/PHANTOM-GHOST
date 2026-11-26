package com.phantom.ghost.auth

import com.phantom.ghost.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed interface AuthResult {
    data class Authenticated(val session: AuthSession) : AuthResult
    data object VerificationRequired : AuthResult
    data class Failure(val code: String, val message: String) : AuthResult
}

interface AuthRepository {
    suspend fun signUp(email: String, password: String): AuthResult
    suspend fun verifyEmail(email: String, sixDigitCode: String): AuthResult
    suspend fun signIn(email: String, password: String): AuthResult
    suspend fun currentAccessToken(): String?
    suspend fun signOut()
}

/** Supabase Auth REST client. Uses only the public anon key; never ship a service-role key. */
class SupabaseAuthRepository(
    private val sessions: SessionManager,
    private val baseUrl: String = BuildConfig.SUPABASE_URL,
    private val anonKey: String = BuildConfig.SUPABASE_ANON_KEY,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build(),
    private val now: () -> Long = System::currentTimeMillis
) : AuthRepository {
    private val mediaType = "application/json; charset=utf-8".toMediaType()
    private fun configured() = baseUrl.startsWith("https://") && anonKey.isNotBlank()

    override suspend fun signUp(email: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        if (!configured()) return@withContext AuthResult.Failure("NOT_CONFIGURED", "Configure SUPABASE_URL and SUPABASE_ANON_KEY.")
        if (!validEmail(email)) return@withContext AuthResult.Failure("INVALID_EMAIL", "Enter a valid email address.")
        if (password.length < 10) return@withContext AuthResult.Failure("WEAK_PASSWORD", "Use at least 10 characters.")
        val (status, body) = request("POST", "/auth/v1/signup", JSONObject().put("email", email.trim()).put("password", password))
        if (status in 200..299) {
            if (body.optString("access_token").isNotBlank()) AuthResult.Authenticated(storeSession(body))
            else AuthResult.VerificationRequired
        } else mapError(status, body)
    }

    override suspend fun verifyEmail(email: String, sixDigitCode: String): AuthResult = withContext(Dispatchers.IO) {
        if (!sixDigitCode.matches(Regex("^\\d{6}$"))) {
            return@withContext AuthResult.Failure("INVALID_VERIFICATION_CODE", "The code must contain exactly six digits.")
        }
        val payload = JSONObject().put("type", "signup").put("email", email.trim()).put("token", sixDigitCode)
        val (status, body) = request("POST", "/auth/v1/verify", payload)
        if (status in 200..299 && body.optString("access_token").isNotBlank()) {
            AuthResult.Authenticated(storeSession(body))
        } else mapError(status, body)
    }

    override suspend fun signIn(email: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("email", email.trim()).put("password", password)
        val (status, body) = request("POST", "/auth/v1/token?grant_type=password", payload)
        if (status in 200..299 && body.optString("access_token").isNotBlank()) {
            AuthResult.Authenticated(storeSession(body))
        } else mapError(status, body)
    }

    override suspend fun currentAccessToken(): String? = withContext(Dispatchers.IO) {
        val old = sessions.load() ?: return@withContext null
        if (old.expiresAtMillis > now() + 60_000L) return@withContext old.accessToken
        val (status, body) = request(
            "POST", "/auth/v1/token?grant_type=refresh_token",
            JSONObject().put("refresh_token", old.refreshToken)
        )
        if (status in 200..299 && body.optString("access_token").isNotBlank()) {
            storeSession(body).accessToken
        } else {
            sessions.clear()
            null
        }
    }

    override suspend fun signOut() = withContext(Dispatchers.IO) {
        val access = sessions.load()?.accessToken
        if (!access.isNullOrBlank() && configured()) request("POST", "/auth/v1/logout", JSONObject(), access)
        sessions.clear()
    }

    private fun request(method: String, path: String, payload: JSONObject, bearer: String? = null): Pair<Int, JSONObject> {
        if (!configured()) return 0 to JSONObject().put("message", "Supabase is not configured.")
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .header("apikey", anonKey)
            .header("Authorization", "Bearer " + (bearer ?: anonKey))
            .method(method, payload.toString().toRequestBody(mediaType))
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                response.code to (runCatching { JSONObject(text) }
                    .getOrElse { JSONObject().put("message", text.take(400)) })
            }
        } catch (error: IOException) {
            -1 to JSONObject().put("message", error.message ?: "Network error.")
        }
    }

    private fun storeSession(body: JSONObject): AuthSession {
        val user = body.getJSONObject("user")
        val session = AuthSession(
            userId = user.getString("id"),
            accessToken = body.getString("access_token"),
            refreshToken = body.getString("refresh_token"),
            expiresAtMillis = now() + body.optLong("expires_in", 3600L) * 1000L
        )
        sessions.save(session)
        return session
    }

    private fun mapError(status: Int, body: JSONObject): AuthResult.Failure {
        val message = body.optString("msg").ifBlank {
            body.optString("message").ifBlank { body.optString("error_description").ifBlank { "Authentication failed ($status)." } }
        }
        val lower = message.lowercase()
        val code = when {
            status == 422 || "already registered" in lower || "already exists" in lower -> "USER_ALREADY_EXISTS"
            status == 401 || "invalid jwt" in lower || "invalid login credentials" in lower -> "INVALID_JWT_OR_CREDENTIALS"
            "email not confirmed" in lower -> "EMAIL_NOT_VERIFIED"
            status == -1 -> "NETWORK_ERROR"
            else -> "AUTH_ERROR"
        }
        return AuthResult.Failure(code, message)
    }

    private fun validEmail(email: String) =
        email.trim().matches(Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))
}

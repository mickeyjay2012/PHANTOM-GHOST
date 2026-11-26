package com.phantom.ghost.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class AuthSession(
    val userId: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long
)

/** AES-GCM token storage using a non-exportable Android Keystore key. */
class SessionManager(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("phantom_secure_session", Context.MODE_PRIVATE)
    private val keyAlias = "phantom.session.aes.v1"

    @Synchronized fun save(session: AuthSession) {
        val plain = JSONObject()
            .put("user_id", session.userId)
            .put("access_token", session.accessToken)
            .put("refresh_token", session.refreshToken)
            .put("expires_at", session.expiresAtMillis)
            .toString().toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getKey())
        val sealed = cipher.doFinal(plain)
        preferences.edit().putString("session", Base64.encodeToString(cipher.iv + sealed, Base64.NO_WRAP)).apply()
        plain.fill(0)
    }

    @Synchronized fun load(): AuthSession? = runCatching {
        val encoded = preferences.getString("session", null) ?: return null
        val packed = Base64.decode(encoded, Base64.NO_WRAP)
        require(packed.size > 12) { "Invalid encrypted session." }
        val iv = packed.copyOfRange(0, 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(128, iv))
        val plain = cipher.doFinal(packed.copyOfRange(12, packed.size))
        val json = JSONObject(String(plain, Charsets.UTF_8))
        plain.fill(0)
        AuthSession(
            json.getString("user_id"),
            json.getString("access_token"),
            json.getString("refresh_token"),
            json.getLong("expires_at")
        )
    }.getOrElse {
        clear()
        null
    }

    @Synchronized fun clear() { preferences.edit().remove("session").apply() }

    private fun getKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}

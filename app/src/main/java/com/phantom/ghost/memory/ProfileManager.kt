package com.phantom.ghost.memory

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

/** Device-local identity for memory isolation; it is not an account identifier. */
class ProfileManager(context: Context) {
    private val preferences = EncryptedSharedPreferences.create(
        context.applicationContext,
        "phantom_local_profile",
        MasterKey.Builder(context.applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    val localProfileId: String
        get() = synchronized(this) {
            preferences.getString(KEY_PROFILE, null) ?: UUID.randomUUID().toString().also {
                check(preferences.edit().putString(KEY_PROFILE, it).commit()) { "Could not persist local profile." }
            }
        }

    var autoLearnEnabled: Boolean
        get() = preferences.getBoolean(KEY_AUTO_LEARN, false)
        set(value) { check(preferences.edit().putBoolean(KEY_AUTO_LEARN, value).commit()) }

    companion object {
        private const val KEY_PROFILE = "local_profile_id"
        private const val KEY_AUTO_LEARN = "auto_learn_enabled"
    }
}

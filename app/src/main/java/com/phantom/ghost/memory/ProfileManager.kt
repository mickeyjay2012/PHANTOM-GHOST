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

    var ghostName: String
        get() = preferences.getString(KEY_GHOST_NAME, "Phantom") ?: "Phantom"
        set(value) { check(preferences.edit().putString(KEY_GHOST_NAME, value.trim().take(32)).commit()) }

    var preferredName: String
        get() = preferences.getString(KEY_PREFERRED_NAME, "") ?: ""
        set(value) { check(preferences.edit().putString(KEY_PREFERRED_NAME, value.trim().take(40)).commit()) }

    var selfReference: String
        get() = preferences.getString(KEY_SELF_REFERENCE, "") ?: ""
        set(value) { check(preferences.edit().putString(KEY_SELF_REFERENCE, value.trim().take(40)).commit()) }

    var handsFreeRequested: Boolean
        get() = preferences.getBoolean(KEY_HANDS_FREE_REQUESTED, false)
        set(value) { check(preferences.edit().putBoolean(KEY_HANDS_FREE_REQUESTED, value).commit()) }

    var voiceInputEnabled: Boolean
        get() = preferences.getBoolean(KEY_VOICE_INPUT_ENABLED, false)
        set(value) { check(preferences.edit().putBoolean(KEY_VOICE_INPUT_ENABLED, value).commit()) }

    var onboardingComplete: Boolean
        get() = preferences.getBoolean(KEY_ONBOARDING_COMPLETE, false)
        set(value) { check(preferences.edit().putBoolean(KEY_ONBOARDING_COMPLETE, value).commit()) }

    companion object {
        private const val KEY_PROFILE = "local_profile_id"
        private const val KEY_AUTO_LEARN = "auto_learn_enabled"
        private const val KEY_GHOST_NAME = "ghost_name"
        private const val KEY_PREFERRED_NAME = "preferred_name"
        private const val KEY_SELF_REFERENCE = "self_reference"
        private const val KEY_HANDS_FREE_REQUESTED = "hands_free_requested"
        private const val KEY_VOICE_INPUT_ENABLED = "voice_input_enabled"
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
    }
}

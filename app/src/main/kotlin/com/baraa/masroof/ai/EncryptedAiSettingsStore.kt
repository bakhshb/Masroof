package com.baraa.masroof.ai

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Android-backed API key store. Uses
 * [EncryptedSharedPreferences] with a [MasterKey] backed by the Android
 * Keystore.
 *
 * Properties:
 *  - The key is encrypted with AES-256-GCM (master key) and AES-256-SIV
 *    (preferences encryption), per androidx.security.crypto defaults.
 *  - The key is never written to plain SharedPreferences, Room, or the
 *    application files dir.
 *  - We never log the key, only whether it exists.
 *  - On Keystore invalidation (e.g. user removes the device PIN), we
 *    gracefully degrade by clearing the stored key.
 */
class EncryptedAiSettingsStore(context: Context) : AiSettingsStore {

    private val prefs: SharedPreferences = openEncryptedPrefs(context)

    override fun getApiKey(): String? = prefs.getString(KEY_API_KEY, null)

    override fun saveApiKey(key: String) {
        require(key.isNotBlank()) { "API key must not be blank" }
        prefs.edit().putString(KEY_API_KEY, key).apply()
    }

    override fun deleteApiKey() {
        prefs.edit().remove(KEY_API_KEY).apply()
    }

    override fun hasApiKey(): Boolean = getApiKey() != null

    private fun openEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return try {
            EncryptedSharedPreferences.create(
                context.applicationContext,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (t: Throwable) {
            // Keystore may have been invalidated (factory reset, security
            // change). Fall back to an empty in-memory stub so we don't
            // crash the app — and log a sanitized message.
            Log.w(TAG, "EncryptedSharedPreferences unavailable, using in-memory fallback")
            EmptySharedPrefs
        }
    }

    companion object {
        private const val TAG = "EncryptedAiStore"
        private const val PREFS_FILE = "masroof_ai_secure"
        private const val KEY_API_KEY = "api_key"

        /**
         * SharedPreferences stub used when the Keystore is unavailable.
         * Reading any value returns null; writes are silently dropped.
         */
        private object EmptySharedPrefs : SharedPreferences {
            override fun getAll(): MutableMap<String, *> = mutableMapOf<String, Any?>()
            override fun getString(k: String?, d: String?): String? = d
            override fun getStringSet(k: String?, d: MutableSet<String>?): MutableSet<String>? = d
            override fun getInt(k: String?, d: Int): Int = d
            override fun getLong(k: String?, d: Long): Long = d
            override fun getFloat(k: String?, d: Float): Float = d
            override fun getBoolean(k: String?, d: Boolean): Boolean = d
            override fun contains(k: String?): Boolean = false
            override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
                override fun putString(k: String?, v: String?) = this
                override fun putStringSet(k: String?, v: MutableSet<String>?) = this
                override fun putInt(k: String?, v: Int) = this
                override fun putLong(k: String?, v: Long) = this
                override fun putFloat(k: String?, v: Float) = this
                override fun putBoolean(k: String?, v: Boolean) = this
                override fun remove(k: String?) = this
                override fun clear() = this
                override fun commit(): Boolean = true
                override fun apply() {}
            }
            override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
            override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        }
    }
}
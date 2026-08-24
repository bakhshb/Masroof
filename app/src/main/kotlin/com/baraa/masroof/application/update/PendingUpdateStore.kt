package com.baraa.masroof.application.update

import android.content.SharedPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PendingUpdateStore(
    private val prefs: SharedPreferences,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun saveAvailable(manifest: UpdateManifest) {
        prefs.edit()
            .putString(KEY_MANIFEST, json.encodeToString(manifest))
            .apply()
    }

    fun readAvailable(): UpdateManifest? {
        val raw = prefs.getString(KEY_MANIFEST, null) ?: return null
        return runCatching { json.decodeFromString(UpdateManifest.serializer(), raw) }.getOrNull()
    }

    fun clear() {
        prefs.edit().remove(KEY_MANIFEST).apply()
    }

    companion object {
        const val PREFS_NAME: String = "pending_update_prefs"
        private const val KEY_MANIFEST: String = "available_manifest"
    }
}

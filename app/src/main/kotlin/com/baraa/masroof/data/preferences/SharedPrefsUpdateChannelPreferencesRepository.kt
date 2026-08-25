package com.baraa.masroof.data.preferences

import android.content.SharedPreferences
import com.baraa.masroof.application.update.UpdateChannel
import com.baraa.masroof.application.update.UpdateChannelPreferencesRepository

class SharedPrefsUpdateChannelPreferencesRepository(
    private val prefs: SharedPreferences,
) : UpdateChannelPreferencesRepository {
    override fun getUpdateChannel(): UpdateChannel =
        UpdateChannel.fromStorage(prefs.getString(KEY_UPDATE_CHANNEL, null))

    override fun setUpdateChannel(channel: UpdateChannel) {
        prefs.edit().putString(KEY_UPDATE_CHANNEL, channel.storageValue()).apply()
    }

    companion object {
        const val PREFS_NAME: String = "update_channel_prefs"
        const val KEY_UPDATE_CHANNEL: String = "update_channel"
    }
}

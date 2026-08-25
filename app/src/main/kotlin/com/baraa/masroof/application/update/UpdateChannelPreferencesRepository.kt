package com.baraa.masroof.application.update

interface UpdateChannelPreferencesRepository {
    fun getUpdateChannel(): UpdateChannel

    fun setUpdateChannel(channel: UpdateChannel)
}

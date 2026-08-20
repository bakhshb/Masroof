package com.baraa.masroof.application.notification

interface NotificationPreferencesRepository {
    fun getReadIds(): Set<String>

    fun markRead(id: String)

    fun clearRead(id: String)

    fun setReadIds(ids: Set<String>)
}

package com.baraa.masroof.application.backup

import kotlinx.serialization.json.Json

/**
 * Pure encode/decode helpers for the .masroof ZIP payload (no Android APIs).
 */
object BackupPackageCodec {
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encodeManifest(manifest: BackupManifest): String =
        json.encodeToString(BackupManifest.serializer(), manifest)

    fun decodeManifest(raw: String): BackupManifest =
        json.decodeFromString(BackupManifest.serializer(), raw)

    fun encodePreferences(snapshot: BackupPreferencesSnapshot): String =
        json.encodeToString(BackupPreferencesSnapshot.serializer(), snapshot)

    fun decodePreferences(raw: String): BackupPreferencesSnapshot =
        json.decodeFromString(BackupPreferencesSnapshot.serializer(), raw)

    fun validateManifest(
        manifest: BackupManifest,
        expectedRoomVersion: Int,
        expectedIdentityHash: String,
    ): Boolean =
        manifest.formatVersion == BackupPackageFormat.FORMAT_VERSION &&
            manifest.roomVersion == expectedRoomVersion &&
            manifest.identityHash == expectedIdentityHash
}

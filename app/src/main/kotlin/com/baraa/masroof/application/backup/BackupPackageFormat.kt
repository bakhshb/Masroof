package com.baraa.masroof.application.backup

import kotlinx.serialization.Serializable

@Serializable
data class BackupManifest(
    val formatVersion: Int,
    val appVersionName: String,
    val roomVersion: Int,
    val identityHash: String,
    val exportedAtEpochMillis: Long,
)

@Serializable
data class BackupPreferencesSnapshot(
    val onboardingStarted: Boolean,
    val onboardingCompleted: Boolean,
    val historicalImportStartEpochMillis: Long? = null,
    val historicalImportCompleted: Boolean,
    val languageTag: String,
    val themeMode: String,
)

enum class BackupImportOutcome {
    SuccessNeedsRestart,
    InvalidPackage,
    Failed,
}

object BackupPackageFormat {
    const val FORMAT_VERSION: Int = 1
    const val FILE_EXTENSION: String = "masroof"
    const val MIME_TYPE: String = "application/octet-stream"
    const val MANIFEST_ENTRY: String = "manifest.json"
    const val DATABASE_ENTRY: String = "masroof.db"
    const val PREFERENCES_ENTRY: String = "preferences.json"

    fun defaultExportFileName(exportedAtEpochMillis: Long): String =
        "masroof-backup-$exportedAtEpochMillis.$FILE_EXTENSION"
}

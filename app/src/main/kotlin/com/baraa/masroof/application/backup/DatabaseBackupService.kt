package com.baraa.masroof.application.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.database.sqlite.SQLiteDatabase
import com.baraa.masroof.application.logging.AppLogCategories
import com.baraa.masroof.application.logging.AppLogService
import com.baraa.masroof.application.locale.AppLocale
import com.baraa.masroof.application.theme.ThemeMode
import com.baraa.masroof.data.preferences.SharedPrefsAppLocaleRepository
import com.baraa.masroof.data.preferences.SharedPrefsOnboardingPreferencesRepository
import com.baraa.masroof.data.preferences.SharedPrefsThemePreferencesRepository
import com.baraa.masroof.data.room.MasroofDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class DatabaseBackupService(
    private val appContext: Context,
    private val database: MasroofDatabase,
    private val closeDatabase: () -> Unit,
    private val appVersionName: String,
    private val appLogService: AppLogService? = null,
    private val clockEpochMillis: () -> Long = { System.currentTimeMillis() },
    private val restartProcess: () -> Unit = { defaultRestartProcess(appContext) },
) : DatabaseBackupGateway {
    override suspend fun exportTo(destination: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            checkpointWal()
            val staging = createStagingDir("export")
            try {
                val dbCopy = File(staging, BackupPackageFormat.DATABASE_ENTRY)
                val liveDb = appContext.getDatabasePath(MasroofDatabase.NAME)
                require(liveDb.exists()) { "Database file missing" }
                liveDb.copyTo(dbCopy, overwrite = true)

                val exportedAt = clockEpochMillis()
                val manifest = BackupManifest(
                    formatVersion = BackupPackageFormat.FORMAT_VERSION,
                    appVersionName = appVersionName,
                    roomVersion = MasroofDatabase.VERSION,
                    identityHash = MasroofDatabase.IDENTITY_HASH,
                    exportedAtEpochMillis = exportedAt,
                )
                File(staging, BackupPackageFormat.MANIFEST_ENTRY)
                    .writeText(BackupPackageCodec.encodeManifest(manifest))
                File(staging, BackupPackageFormat.PREFERENCES_ENTRY)
                    .writeText(BackupPackageCodec.encodePreferences(capturePreferences()))

                writeZip(staging, destination)
            } finally {
                staging.deleteRecursively()
            }
        }.onSuccess {
            appLogService?.info(AppLogCategories.BACKUP, "Database export succeeded")
        }.onFailure { error ->
            appLogService?.error(
                AppLogCategories.BACKUP,
                "Database export failed: ${error.message ?: error::class.java.simpleName}",
            )
        }
    }

    override suspend fun importFrom(source: Uri): BackupImportOutcome = withContext(Dispatchers.IO) {
        val staging = createStagingDir("import")
        try {
            unzipTo(source, staging)
            val manifestFile = File(staging, BackupPackageFormat.MANIFEST_ENTRY)
            val dbFile = File(staging, BackupPackageFormat.DATABASE_ENTRY)
            val prefsFile = File(staging, BackupPackageFormat.PREFERENCES_ENTRY)
            if (!manifestFile.exists() || !dbFile.exists() || !prefsFile.exists()) {
                return@withContext BackupImportOutcome.InvalidPackage
            }

            val manifest = runCatching {
                BackupPackageCodec.decodeManifest(manifestFile.readText())
            }.getOrElse { return@withContext BackupImportOutcome.InvalidPackage }

            val importableVersions = mapOf(
                MasroofDatabase.LEGACY_VERSION_5 to MasroofDatabase.LEGACY_IDENTITY_HASH_5,
                MasroofDatabase.PREVIOUS_VERSION to MasroofDatabase.PREVIOUS_IDENTITY_HASH,
            )
            if (!BackupPackageCodec.validateManifestForImport(
                    manifest = manifest,
                    targetRoomVersion = MasroofDatabase.VERSION,
                    targetIdentityHash = MasroofDatabase.IDENTITY_HASH,
                    importableVersions = importableVersions,
                )
            ) {
                return@withContext BackupImportOutcome.InvalidPackage
            }

            val expectedDbIdentityHash = BackupPackageCodec.expectedIdentityHashForImport(
                manifest = manifest,
                targetRoomVersion = MasroofDatabase.VERSION,
                targetIdentityHash = MasroofDatabase.IDENTITY_HASH,
                importableVersions = importableVersions,
            ) ?: return@withContext BackupImportOutcome.InvalidPackage

            val identityFromDb = readIdentityHash(dbFile)
            if (identityFromDb != expectedDbIdentityHash) {
                return@withContext BackupImportOutcome.InvalidPackage
            }

            val preferences = runCatching {
                BackupPackageCodec.decodePreferences(prefsFile.readText())
            }.getOrElse { return@withContext BackupImportOutcome.InvalidPackage }

            closeDatabase()

            val liveDb = appContext.getDatabasePath(MasroofDatabase.NAME)
            liveDb.parentFile?.mkdirs()
            deleteSidecarFiles(liveDb)
            dbFile.copyTo(liveDb, overwrite = true)
            restorePreferences(preferences)
            restartProcess()
            appLogService?.info(AppLogCategories.BACKUP, "Database import succeeded; restart required")
            BackupImportOutcome.SuccessNeedsRestart
        } catch (error: Exception) {
            appLogService?.error(
                AppLogCategories.BACKUP,
                "Database import failed: ${error.message ?: error::class.java.simpleName}",
            )
            BackupImportOutcome.Failed
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun checkpointWal() {
        database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { cursor ->
            cursor.moveToFirst()
        }
    }

    private fun capturePreferences(): BackupPreferencesSnapshot {
        val onboarding = appContext.getSharedPreferences(
            SharedPrefsOnboardingPreferencesRepository.PREFS_NAME,
            Context.MODE_PRIVATE,
        )
        val locale = appContext.getSharedPreferences(
            SharedPrefsAppLocaleRepository.PREFS_NAME,
            Context.MODE_PRIVATE,
        )
        val theme = appContext.getSharedPreferences(
            SharedPrefsThemePreferencesRepository.PREFS_NAME,
            Context.MODE_PRIVATE,
        )
        val importStart = if (onboarding.contains(KEY_IMPORT_START_EPOCH_MILLIS)) {
            onboarding.getLong(KEY_IMPORT_START_EPOCH_MILLIS, 0L)
        } else {
            null
        }
        return BackupPreferencesSnapshot(
            onboardingStarted = onboarding.getBoolean(KEY_ONBOARDING_STARTED, false),
            onboardingCompleted = onboarding.getBoolean(KEY_ONBOARDING_COMPLETED, false),
            historicalImportStartEpochMillis = importStart,
            historicalImportCompleted = onboarding.getBoolean(KEY_IMPORT_COMPLETED, false),
            languageTag = locale.getString(
                SharedPrefsAppLocaleRepository.KEY_LANGUAGE_TAG,
                AppLocale.DEFAULT_TAG,
            ) ?: AppLocale.DEFAULT_TAG,
            themeMode = theme.getString(
                SharedPrefsThemePreferencesRepository.KEY_THEME_MODE,
                ThemeMode.DEFAULT.name,
            ) ?: ThemeMode.DEFAULT.name,
        )
    }

    private fun restorePreferences(snapshot: BackupPreferencesSnapshot) {
        val onboarding = appContext.getSharedPreferences(
            SharedPrefsOnboardingPreferencesRepository.PREFS_NAME,
            Context.MODE_PRIVATE,
        )
        val locale = appContext.getSharedPreferences(
            SharedPrefsAppLocaleRepository.PREFS_NAME,
            Context.MODE_PRIVATE,
        )
        val theme = appContext.getSharedPreferences(
            SharedPrefsThemePreferencesRepository.PREFS_NAME,
            Context.MODE_PRIVATE,
        )

        onboarding.edit().apply {
            putBoolean(KEY_ONBOARDING_STARTED, snapshot.onboardingStarted)
            putBoolean(KEY_ONBOARDING_COMPLETED, snapshot.onboardingCompleted)
            putBoolean(KEY_IMPORT_COMPLETED, snapshot.historicalImportCompleted)
            val start = snapshot.historicalImportStartEpochMillis
            if (start == null) {
                remove(KEY_IMPORT_START_EPOCH_MILLIS)
            } else {
                putLong(KEY_IMPORT_START_EPOCH_MILLIS, start)
            }
        }.commit()

        locale.edit().putString(
            SharedPrefsAppLocaleRepository.KEY_LANGUAGE_TAG,
            when (snapshot.languageTag) {
                AppLocale.TAG_EN -> AppLocale.TAG_EN
                else -> AppLocale.TAG_AR
            },
        ).commit()

        theme.edit().putString(
            SharedPrefsThemePreferencesRepository.KEY_THEME_MODE,
            ThemeMode.fromStorage(snapshot.themeMode).name,
        ).commit()
    }

    private fun writeZip(staging: File, destination: Uri) {
        val output = appContext.contentResolver.openOutputStream(destination)
            ?: error("Cannot open export destination")
        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            listOf(
                BackupPackageFormat.MANIFEST_ENTRY,
                BackupPackageFormat.DATABASE_ENTRY,
                BackupPackageFormat.PREFERENCES_ENTRY,
            ).forEach { name ->
                val file = File(staging, name)
                zip.putNextEntry(ZipEntry(name))
                FileInputStream(file).use { input -> input.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    private fun unzipTo(source: Uri, staging: File) {
        val input = appContext.contentResolver.openInputStream(source)
            ?: error("Cannot open import source")
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name.substringAfterLast('/').substringAfterLast('\\')
                if (name in ALLOWED_ENTRIES && !entry.isDirectory) {
                    val outFile = File(staging, name)
                    FileOutputStream(outFile).use { output -> zip.copyTo(output) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    private fun createStagingDir(label: String): File {
        val dir = File(appContext.cacheDir, "masroof-backup-$label-${clockEpochMillis()}")
        if (dir.exists()) dir.deleteRecursively()
        check(dir.mkdirs()) { "Cannot create staging directory" }
        return dir
    }

    private fun deleteSidecarFiles(dbFile: File) {
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-shm").delete()
        File(dbFile.path + "-journal").delete()
    }

    private fun readIdentityHash(dbFile: File): String? {
        return runCatching {
            SQLiteDatabase.openDatabase(
                dbFile.path,
                null,
                SQLiteDatabase.OPEN_READONLY,
            ).use { db ->
                db.rawQuery(
                    "SELECT identity_hash FROM room_master_table WHERE id = 42 LIMIT 1",
                    null,
                ).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }
        }.getOrNull()
    }

    companion object {
        internal fun defaultRestartProcess(appContext: Context) {
            val launchIntent = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)
                ?: return
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            appContext.startActivity(launchIntent)
            Runtime.getRuntime().exit(0)
        }

        private val ALLOWED_ENTRIES = setOf(
            BackupPackageFormat.MANIFEST_ENTRY,
            BackupPackageFormat.DATABASE_ENTRY,
            BackupPackageFormat.PREFERENCES_ENTRY,
        )

        // Mirror SharedPrefsOnboardingPreferencesRepository private keys for backup I/O.
        private const val KEY_ONBOARDING_STARTED = "onboarding_started"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_IMPORT_START_EPOCH_MILLIS = "historical_import_start_epoch_millis"
        private const val KEY_IMPORT_COMPLETED = "historical_import_completed"
    }
}

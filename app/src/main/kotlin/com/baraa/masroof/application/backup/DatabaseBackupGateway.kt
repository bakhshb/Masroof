package com.baraa.masroof.application.backup

import android.net.Uri

interface DatabaseBackupGateway {
    suspend fun exportTo(destination: Uri): Result<Unit>

    suspend fun importFrom(source: Uri): BackupImportOutcome
}

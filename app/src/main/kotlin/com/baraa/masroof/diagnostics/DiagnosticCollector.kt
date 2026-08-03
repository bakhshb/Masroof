package com.baraa.masroof.diagnostics

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.baraa.masroof.data.db.MasroofDatabase
import com.baraa.masroof.rules.RuleEngineFactory
import com.baraa.masroof.transaction.BankParserRegistry
import kotlinx.coroutines.flow.first

/**
 * Builds a [DiagnosticSnapshot] from the live repositories + system
 * state. All fields are sanitized — no SMS body, no merchant name, no
 * exact amount, no card / account digits, no API key.
 *
 * Singleton-style — there is one global error log + collector per
 * process; UI and services report through [DiagnosticErrorLog.record].
 */
class DiagnosticCollector(
    private val context: Context,
    private val database: MasroofDatabase,
    private val merchantMemoryRepository: com.baraa.masroof.data.repository.MerchantMemoryRepository,
    private val categoryRepository: com.baraa.masroof.data.repository.CategoryRepository,
    private val aiSettingsRepository: com.baraa.masroof.ai.AiSettingsRepository,
    private val errorLog: DiagnosticErrorLog = DiagnosticErrorLog(),
    private val buildTimestamp: String = java.time.Instant.now().toString(),
) {

    fun errorLog(): DiagnosticErrorLog = errorLog

    suspend fun snapshot(): DiagnosticSnapshot {
        val pkgInfo = try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (_: Throwable) {
            null
        }
        val versionName = pkgInfo?.versionName ?: "?"
        val versionCode = pkgInfo?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.longVersionCode else @Suppress("DEPRECATION") it.versionCode.toLong()
        } ?: 0L

        val smsPermissionGranted = ContextCompatCompat.checkSelfPermission(
            context, "android.permission.READ_SMS",
        ) == PackageManager.PERMISSION_GRANTED

        // Pull sanitized counts from the database.
        val savedTransactionsCount = database.transactionDao().count().toLong()
        val categories = categoryRepository.getAll()
        val merchantMemories = merchantMemoryRepository.getAll()

        val aiCfg = aiSettingsRepository.load()
        val lastOutcome = aiLastOutcome()

        return DiagnosticSnapshot(
            appVersionName = versionName,
            appVersionCode = versionCode,
            databaseSchemaVersion = MasroofDatabase.ALL_MIGRATIONS.lastOrNull()?.endVersion ?: 0,
            androidVersion = "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
            deviceManufacturer = Build.MANUFACTURER ?: "?",
            deviceModel = Build.MODEL ?: "?",
            smsPermissionGranted = smsPermissionGranted,
            smsScannedCount = metrics.scannedCount,
            smsFinancialDetectedCount = metrics.financialDetectedCount,
            smsParsedCount = metrics.parsedCount,
            smsParseFailureCount = metrics.parseFailureCount,
            savedTransactionsCount = savedTransactionsCount,
            exactDuplicatesCount = metrics.exactDuplicatesCount,
            possibleDuplicatesCount = metrics.possibleDuplicatesCount,
            needsReviewCount = metrics.needsReviewCount,
            categoryCount = categories.size.toLong(),
            merchantMemoryCount = merchantMemories.size.toLong(),
            aiEnabled = aiCfg.enabled,
            aiProviderName = if (aiCfg.enabled) aiCfg.providerLabel else null,
            aiModelName = if (aiCfg.enabled) aiCfg.modelName else null,
            lastAiOutcome = lastOutcome,
            parserNames = BankParserRegistry.parsers.map { it.javaClass.simpleName },
            ruleNames = RuleEngineFactory.documentedPriorities.map { it.name },
            recentErrors = errorLog.snapshot(),
            buildTimestamp = buildTimestamp,
            diagnosticReportVersion = DiagnosticSnapshot.REPORT_VERSION,
        )
    }

    /**
     * Mutable counters updated from the import flow / AI service.
     * They are process-local; lost when the app is killed. The
     * diagnostics screen shows them only.
     */
    val metrics = Metrics()

    class Metrics {
        @Volatile var scannedCount: Long = 0L
        @Volatile var financialDetectedCount: Long = 0L
        @Volatile var parsedCount: Long = 0L
        @Volatile var parseFailureCount: Long = 0L
        @Volatile var exactDuplicatesCount: Long = 0L
        @Volatile var possibleDuplicatesCount: Long = 0L
        @Volatile var needsReviewCount: Long = 0L
        @Volatile var lastAiOutcome: String = DiagnosticSnapshot.EMPTY_OUTCOME

        fun reset() {
            scannedCount = 0L
            financialDetectedCount = 0L
            parsedCount = 0L
            parseFailureCount = 0L
            exactDuplicatesCount = 0L
            possibleDuplicatesCount = 0L
            needsReviewCount = 0L
            lastAiOutcome = DiagnosticSnapshot.EMPTY_OUTCOME
        }
    }

    private fun aiLastOutcome(): String = metrics.lastAiOutcome
}

/**
 * Tiny compatibility shim to keep the file readable when min-SDK
 * differences matter. The actual call uses the well-known string id so
 * we don't need a constant from the platform.
 */
private object ContextCompatCompat {
    fun checkSelfPermission(context: Context, perm: String): Int =
        androidx.core.content.ContextCompat.checkSelfPermission(context, perm)
}
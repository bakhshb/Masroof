package com.baraa.masroof.sms.scanner

import com.baraa.masroof.sms.datasource.InboxRow
import com.baraa.masroof.sms.datasource.SmsDataSource
import com.baraa.masroof.sms.datasource.SmsPermissionException
import com.baraa.masroof.sms.datasource.SmsProviderException
import com.baraa.masroof.sms.ingestion.SmsIngestionResult
import com.baraa.masroof.sms.ingestion.SmsIngestionService
import com.baraa.masroof.sms.mapper.AndroidSmsMapper
import java.time.Instant

/**
 * Operational summary of a historical inbox scan (not financial domain state).
 */
data class SmsScanResult(
    val scanned: Int = 0,
    val inserted: Int = 0,
    val duplicates: Int = 0,
    val parsed: Int = 0,
    val reviewRequired: Int = 0,
    val nonFinancial: Int = 0,
    val unsupported: Int = 0,
    val notRelevant: Int = 0,
    val skippedMalformed: Int = 0,
    val failed: Int = 0,
    val failure: SmsScanFailure? = null,
)

sealed interface SmsScanFailure {
    data object PermissionDenied : SmsScanFailure

    data class ProviderError(
        val message: String,
    ) : SmsScanFailure
}

/**
 * Historical inbox scan → shared [SmsIngestionService].
 *
 * Processes rows incrementally (oldest → newest). Catches permission/provider
 * failures from both sequence creation and lazy iteration, preserving partial
 * counters when failure occurs mid-scan.
 */
class HistoricalSmsScanner(
    private val dataSource: SmsDataSource,
    private val ingestionService: SmsIngestionService,
) {
    suspend fun scan(receivedAfter: Instant? = null): SmsScanResult {
        var scanned = 0
        var inserted = 0
        var duplicates = 0
        var parsed = 0
        var reviewRequired = 0
        var nonFinancial = 0
        var unsupported = 0
        var notRelevant = 0
        var skippedMalformed = 0
        var failed = 0

        fun snapshot(failure: SmsScanFailure?) = SmsScanResult(
            scanned = scanned,
            inserted = inserted,
            duplicates = duplicates,
            parsed = parsed,
            reviewRequired = reviewRequired,
            nonFinancial = nonFinancial,
            unsupported = unsupported,
            notRelevant = notRelevant,
            skippedMalformed = skippedMalformed,
            failed = failed,
            failure = failure,
        )

        try {
            val rows = dataSource.queryInbox(receivedAfter)
            for (row in rows) {
                scanned++
                when (row) {
                    is InboxRow.Malformed -> {
                        skippedMalformed++
                    }
                    is InboxRow.Valid -> {
                        val rawSms = try {
                            AndroidSmsMapper.toRawSms(row.record)
                        } catch (_: IllegalArgumentException) {
                            skippedMalformed++
                            continue
                        }

                        when (val outcome = ingestionService.ingest(rawSms)) {
                            is SmsIngestionResult.Duplicate -> duplicates++
                            is SmsIngestionResult.NotRelevant -> notRelevant++
                            is SmsIngestionResult.Parsed -> {
                                inserted++
                                parsed++
                            }
                            is SmsIngestionResult.ReviewRequired -> {
                                inserted++
                                reviewRequired++
                            }
                            is SmsIngestionResult.NonFinancial -> {
                                inserted++
                                nonFinancial++
                            }
                            is SmsIngestionResult.Unsupported -> {
                                inserted++
                                unsupported++
                            }
                            is SmsIngestionResult.Invalid -> {
                                inserted++
                                failed++
                            }
                            is SmsIngestionResult.Failed -> {
                                // rawSmsId == null → RawSms never persisted
                                // rawSmsId != null → RawSms already stored; later step failed
                                if (outcome.rawSmsId != null) {
                                    inserted++
                                }
                                failed++
                            }
                        }
                    }
                }
            }
        } catch (_: SmsPermissionException) {
            return snapshot(SmsScanFailure.PermissionDenied)
        } catch (e: SmsProviderException) {
            return snapshot(SmsScanFailure.ProviderError(e.message ?: "provider_error"))
        }

        return snapshot(failure = null)
    }
}

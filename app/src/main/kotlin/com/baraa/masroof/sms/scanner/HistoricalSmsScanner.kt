package com.baraa.masroof.sms.scanner

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
 * Processes rows incrementally (oldest → newest). Does not parse or touch Room
 * entities directly.
 */
class HistoricalSmsScanner(
    private val dataSource: SmsDataSource,
    private val ingestionService: SmsIngestionService,
) {
    suspend fun scan(receivedAfter: Instant? = null): SmsScanResult {
        val rows = try {
            dataSource.queryInbox(receivedAfter)
        } catch (_: SmsPermissionException) {
            return SmsScanResult(failure = SmsScanFailure.PermissionDenied)
        } catch (e: SmsProviderException) {
            return SmsScanResult(
                failure = SmsScanFailure.ProviderError(e.message ?: "provider_error"),
            )
        }

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

        for (record in rows) {
            scanned++
            val rawSms = try {
                AndroidSmsMapper.toRawSms(record)
            } catch (_: IllegalArgumentException) {
                skippedMalformed++
                continue
            }

            when (ingestionService.ingest(rawSms)) {
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
                    // RawSms may already exist; count as failure after insert path.
                    inserted++
                    failed++
                }
            }
        }

        return SmsScanResult(
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
            failure = null,
        )
    }
}

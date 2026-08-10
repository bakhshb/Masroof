package com.baraa.masroof.sms.ingestion

import com.baraa.masroof.bank.aljazira.AlJaziraBankDetector
import com.baraa.masroof.bank.aljazira.AlJaziraParsingPipeline
import com.baraa.masroof.domain.model.ParsedEvent
import com.baraa.masroof.domain.model.RawSms
import com.baraa.masroof.domain.ownership.OwnershipDiscoveryService
import com.baraa.masroof.domain.repository.RawSmsInsertResult
import com.baraa.masroof.domain.repository.RawSmsRepository
import com.baraa.masroof.parsing.model.BankDetectionResult
import com.baraa.masroof.parsing.model.ParseResult
import com.baraa.masroof.parsing.model.SmsParseInput
import com.baraa.masroof.parsing.parser.SmsParseGateway
import com.baraa.masroof.parsing.repository.ParsedEventRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration

/**
 * Shared RawSms → persist → parse → store pipeline used by historical scan
 * and live BroadcastReceiver. Idempotent: duplicates do not re-parse.
 *
 * Bank scope is decided with the existing P4 [AlJaziraBankDetector] before
 * persistence so unrelated personal SMS are not stored.
 *
 * Cross-source live↔historical reconciliation uses exact sender + bodyHash and
 * a [CROSS_SOURCE_RECEIVED_AT_TOLERANCE] window. Same-source nearby rows are
 * never merged by that rule.
 *
 * Optional [ownershipDiscovery] runs after a ParsedEvent is saved; discovery
 * failure does not roll back RawSms/ParsedEvent evidence.
 */
class SmsIngestionService(
    private val rawSmsRepository: RawSmsRepository,
    private val parsedEventRepository: ParsedEventRepository,
    private val bankDetector: AlJaziraBankDetector = AlJaziraBankDetector(),
    private val parseGateway: SmsParseGateway = AlJaziraParsingPipeline(),
    private val ownershipDiscovery: OwnershipDiscoveryService? = null,
) {
    private val insertMutex = Mutex()

    suspend fun ingest(rawSms: RawSms): SmsIngestionResult {
        val detection = bankDetector.detect(rawSms.sender, rawSms.body)
        if (detection !is BankDetectionResult.Detected) {
            return SmsIngestionResult.NotRelevant(
                reason = (detection as? BankDetectionResult.Unknown)
                    ?.reasons
                    ?.firstOrNull()
                    ?: "sender_not_in_scope",
            )
        }

        val insertOutcome = try {
            insertMutex.withLock {
                if (hasCrossSourceNearDuplicate(rawSms)) {
                    return@withLock RawSmsInsertResult.AlreadyExists
                }
                rawSmsRepository.insertIfAbsent(rawSms)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return SmsIngestionResult.Failed(
                rawSmsId = null,
                message = e.message ?: e::class.java.simpleName,
                cause = e,
            )
        }

        return when (insertOutcome) {
            RawSmsInsertResult.AlreadyExists -> SmsIngestionResult.Duplicate
            RawSmsInsertResult.Inserted -> parseAndPersist(rawSms)
        }
    }

    private suspend fun hasCrossSourceNearDuplicate(rawSms: RawSms): Boolean {
        val receivedAt = rawSms.receivedAt
        val from = receivedAt.minus(CROSS_SOURCE_RECEIVED_AT_TOLERANCE)
        val to = receivedAt.plus(CROSS_SOURCE_RECEIVED_AT_TOLERANCE)
        // Incoming live (null device id) looks for historical (non-null) peers.
        // Incoming historical looks for live peers.
        val lookingForLiveRow = rawSms.deviceMessageId != null
        return rawSmsRepository.findCrossSourceNearDuplicate(
            sender = rawSms.sender,
            bodyHash = rawSms.bodyHash,
            fromInclusive = from,
            toInclusive = to,
            lookingForLiveRow = lookingForLiveRow,
        ) != null
    }

    private suspend fun parseAndPersist(rawSms: RawSms): SmsIngestionResult {
        val parseResult = try {
            parseGateway.parse(
                SmsParseInput(
                    rawSmsId = rawSms.id,
                    sender = rawSms.sender,
                    body = rawSms.body,
                    receivedAt = rawSms.receivedAt,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return SmsIngestionResult.Failed(
                rawSmsId = rawSms.id,
                message = e.message ?: e::class.java.simpleName,
                cause = e,
            )
        }

        return try {
            mapAndSave(rawSms, parseResult)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SmsIngestionResult.Failed(
                rawSmsId = rawSms.id,
                message = e.message ?: e::class.java.simpleName,
                cause = e,
            )
        }
    }

    private suspend fun mapAndSave(rawSms: RawSms, parseResult: ParseResult): SmsIngestionResult =
        when (parseResult) {
            is ParseResult.Success -> {
                parsedEventRepository.save(parseResult.event, parseResult.details)
                discoverOwnership(parseResult.event)
                SmsIngestionResult.Parsed(
                    rawSmsId = rawSms.id,
                    event = parseResult.event,
                    details = parseResult.details,
                )
            }

            is ParseResult.Partial -> {
                if (parseResult.event != null) {
                    parsedEventRepository.save(parseResult.event, parseResult.details)
                    discoverOwnership(parseResult.event)
                    SmsIngestionResult.Parsed(
                        rawSmsId = rawSms.id,
                        event = parseResult.event,
                        details = parseResult.details,
                    )
                } else {
                    SmsIngestionResult.Invalid(
                        rawSmsId = rawSms.id,
                        findings = parseResult.findings,
                    )
                }
            }

            is ParseResult.ReviewRequired -> {
                if (parseResult.event != null) {
                    parsedEventRepository.save(parseResult.event, parseResult.details)
                    discoverOwnership(parseResult.event)
                }
                SmsIngestionResult.ReviewRequired(
                    rawSmsId = rawSms.id,
                    event = parseResult.event,
                    details = parseResult.details,
                    reasons = parseResult.reasons,
                )
            }

            is ParseResult.NonFinancial -> {
                if (parseResult.event != null) {
                    parsedEventRepository.save(parseResult.event, parseResult.details)
                    discoverOwnership(parseResult.event)
                }
                SmsIngestionResult.NonFinancial(
                    rawSmsId = rawSms.id,
                    event = parseResult.event,
                    details = parseResult.details,
                    reason = parseResult.reason,
                )
            }

            is ParseResult.Unsupported ->
                SmsIngestionResult.Unsupported(
                    rawSmsId = rawSms.id,
                    reason = parseResult.reason,
                )

            is ParseResult.Invalid ->
                SmsIngestionResult.Invalid(
                    rawSmsId = rawSms.id,
                    findings = parseResult.findings,
                )
        }

    private suspend fun discoverOwnership(event: ParsedEvent) {
        val discovery = ownershipDiscovery ?: return
        try {
            discovery.observe(event)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Discovery is best-effort; RawSms/ParsedEvent evidence stays.
        }
    }

    companion object {
        /**
         * Maximum |live receipt − historical DATE| for opposite-source reconciliation.
         * Same-source rows are never merged by this window alone.
         */
        val CROSS_SOURCE_RECEIVED_AT_TOLERANCE: Duration = Duration.ofSeconds(5)
    }
}

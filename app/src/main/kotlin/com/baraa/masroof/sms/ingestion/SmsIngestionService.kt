package com.baraa.masroof.sms.ingestion

import com.baraa.masroof.bank.aljazira.AlJaziraBankDetector
import com.baraa.masroof.bank.aljazira.AlJaziraParsingPipeline
import com.baraa.masroof.domain.model.RawSms
import com.baraa.masroof.domain.repository.RawSmsInsertResult
import com.baraa.masroof.domain.repository.RawSmsRepository
import com.baraa.masroof.parsing.model.BankDetectionResult
import com.baraa.masroof.parsing.model.ParseResult
import com.baraa.masroof.parsing.model.SmsParseInput
import com.baraa.masroof.parsing.parser.SmsParseGateway
import com.baraa.masroof.parsing.repository.ParsedEventRepository

/**
 * Shared RawSms → persist → parse → store pipeline used by historical scan
 * and live BroadcastReceiver. Idempotent: duplicates do not re-parse.
 *
 * Bank scope is decided with the existing P4 [AlJaziraBankDetector] before
 * persistence so unrelated personal SMS are not stored.
 */
class SmsIngestionService(
    private val rawSmsRepository: RawSmsRepository,
    private val parsedEventRepository: ParsedEventRepository,
    private val bankDetector: AlJaziraBankDetector = AlJaziraBankDetector(),
    private val parseGateway: SmsParseGateway = AlJaziraParsingPipeline(),
) {
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

        return when (rawSmsRepository.insertIfAbsent(rawSms)) {
            RawSmsInsertResult.AlreadyExists -> SmsIngestionResult.Duplicate
            RawSmsInsertResult.Inserted -> parseAndPersist(rawSms)
        }
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
        } catch (t: Throwable) {
            return SmsIngestionResult.Failed(
                rawSmsId = rawSms.id,
                message = t.message ?: t::class.java.simpleName,
                cause = t,
            )
        }

        return when (parseResult) {
            is ParseResult.Success -> {
                parsedEventRepository.save(parseResult.event, parseResult.details)
                SmsIngestionResult.Parsed(
                    rawSmsId = rawSms.id,
                    event = parseResult.event,
                    details = parseResult.details,
                )
            }

            is ParseResult.Partial -> {
                if (parseResult.event != null) {
                    parsedEventRepository.save(parseResult.event, parseResult.details)
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
    }
}

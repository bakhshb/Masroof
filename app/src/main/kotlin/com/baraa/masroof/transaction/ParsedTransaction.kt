package com.baraa.masroof.transaction

import java.math.BigDecimal
import java.time.LocalDate
import com.baraa.masroof.data.db.AccountIdentifierType
import java.time.LocalTime

/**
 * Structured representation of a single bank SMS, produced by a [BankSmsParser].
 *
 * Fields are nullable / [TransactionType.UNKNOWN] when the parser could not
 * extract them. [confidence] is a 0..100 score the UI can use to decide whether
 * to render this as a structured transaction card or fall back to the raw
 * message.
 *
 * @param originalSender                  raw sender, copied from the SMS
 * @param originalMessage                 raw body, copied from the SMS (never logged)
 * @param transactionType                 coarse type
 * @param amount                          parsed amount as [BigDecimal]; null if not found
 * @param currency                        parsed currency; [Currency.UNKNOWN] if not found
 * @param merchant                        merchant / beneficiary / counterparty name if found
 * @param accountOrCardLastFourDigits     last four digits only — never the full number
 * @param transactionDate                 date parsed from the message body, or null
 * @param transactionTime                 time parsed from the message body, or null
 * @param status                          transaction status
 * @param confidence                      0..100 score; higher is better
 * @param parsingNotes                    human-readable notes about how the parser
 *                                        reached its verdict (origin of date, missing
 *                                        fields, etc.) — never include sensitive data
 */
enum class IdentifierRole { SOURCE, DESTINATION, UNSPECIFIED }

data class ParsedIdentifierEvidence(
    val type: AccountIdentifierType,
    val lastFour: String,
    val role: IdentifierRole = IdentifierRole.UNSPECIFIED,
    val confidence: Int,
    val extractionRule: String,
)

data class ParsedTransaction(
    val originalSender: String?,
    val originalMessage: String?,
    val transactionType: TransactionType,
    val amount: BigDecimal?,
    val currency: Currency,
    val merchant: String?,
    val accountOrCardLastFourDigits: String?,
    val transactionDate: LocalDate?,
    val transactionTime: LocalTime?,
    val status: TransactionStatus,
    val confidence: Int,
    val parsingNotes: List<String>,
    /**
     * Name of the [BankSmsParser] that produced this result (e.g. "AlRajhi",
     * "Generic"). Always present.
     */
    val parserName: String = "Unknown",
    /**
     * Parser version string. Bumped when a parser's extraction logic changes
     * in a way that could re-parse old messages differently.
     */
    val parserVersion: String = "0.0.0",
    /**
     * Diagnostic list of rules the parser fired while extracting the message
     * (e.g. "sender_alias_match", "amount_pattern", "currency_sar"). Never
     * includes sensitive data.
     */
    val matchedRules: List<String> = emptyList(),
    /**
     * Diagnostic list of fields the parser could not extract. Used to surface
     * a "needs review" hint in the UI and to inform future parser work.
     */
    val missingFields: List<String> = emptyList(),
    /** Strict label-qualified account evidence. No amount, balance, OTP, or reference values. */
    val identifierEvidence: List<ParsedIdentifierEvidence> = emptyList(),
)

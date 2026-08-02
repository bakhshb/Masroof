package com.baraa.masroof.transaction

import java.math.BigDecimal
import java.time.LocalDate
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
)

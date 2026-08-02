package com.baraa.masroof.transaction

/**
 * Contract for a bank SMS parser. Each concrete parser decides whether it can
 * handle a given [sender] / [body] pair, and produces a [ParsedTransaction].
 *
 * To add support for a new bank format, implement this interface and register
 * the new parser in [BankSmsParserRegistry] ahead of [GenericBankSmsParser].
 */
interface BankSmsParser {

    /**
     * Return true if this parser wants to handle the message. The registry
     * uses this to short-circuit before calling [parse].
     */
    fun canParse(sender: String?, body: String?): Boolean

    /**
     * Parse the message and return a [ParsedTransaction]. The parser MUST NOT
     * throw on unknown / malformed input — it should return a low-confidence
     * result with [TransactionType.UNKNOWN] / [TransactionStatus.UNKNOWN] /
     * [Currency.UNKNOWN] and notes explaining the gaps.
     *
     * @param sender              raw SMS sender (may be null)
     * @param body                raw SMS body (may be null)
     * @param smsTimestampMillis  SMS received timestamp in epoch millis, used
     *                            as a fallback for the transaction date when
     *                            no date is found in the body. May be null.
     */
    fun parse(
        sender: String?,
        body: String?,
        smsTimestampMillis: Long?,
    ): ParsedTransaction
}

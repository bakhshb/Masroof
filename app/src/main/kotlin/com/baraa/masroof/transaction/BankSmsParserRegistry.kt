package com.baraa.masroof.transaction

/**
 * Registry of [BankSmsParser] implementations. The first parser whose
 * [BankSmsParser.canParse] returns true gets to handle the message.
 *
 * To support a new bank format, add a concrete [BankSmsParser] implementation
 * and insert it ahead of [GenericBankSmsParser] in [PARSERS].
 */
object BankSmsParserRegistry {

    private val PARSERS: List<BankSmsParser> = listOf(
        // Add specific bank parsers here as they become available.
        GenericBankSmsParser(), // fallback: accepts everything
    )

    /**
     * Find the first parser that can handle the message and delegate to it.
     * If none matches (impossible in practice because [GenericBankSmsParser]
     * always returns true), the fallback is used.
     */
    fun parse(
        sender: String?,
        body: String?,
        smsTimestampMillis: Long?,
    ): ParsedTransaction {
        for (parser in PARSERS) {
            if (parser.canParse(sender, body)) {
                return parser.parse(sender, body, smsTimestampMillis)
            }
        }
        return GenericBankSmsParser().parse(sender, body, smsTimestampMillis)
    }
}

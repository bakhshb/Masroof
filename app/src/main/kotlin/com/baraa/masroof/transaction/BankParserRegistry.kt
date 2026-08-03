package com.baraa.masroof.transaction

import com.baraa.masroof.transaction.banks.AlRajhiParser
import com.baraa.masroof.transaction.banks.AlinmaParser
import com.baraa.masroof.transaction.banks.BSFParser
import com.baraa.masroof.transaction.banks.BankAlJaziraParser
import com.baraa.masroof.transaction.banks.BankAlbiladParser
import com.baraa.masroof.transaction.banks.D360Parser
import com.baraa.masroof.transaction.banks.MeemParser
import com.baraa.masroof.transaction.banks.RiyadBankParser
import com.baraa.masroof.transaction.banks.SABParser
import com.baraa.masroof.transaction.banks.SAIBParser
import com.baraa.masroof.transaction.banks.SNBParser
import com.baraa.masroof.transaction.banks.STCBankParser
import com.baraa.masroof.transaction.banks.UrpayParser

/**
 * Ordered registry of [BankSmsParser]s. Dedicated bank parsers come first
 * (priority 100); the [GenericBankSmsParser] is the final fallback.
 *
 * On every [parse] call the registry:
 *  1. sorts parsers by descending [BankSmsParser.priority]
 *  2. calls [BankSmsParser.canParse] on each in order
 *  3. delegates to the first one that claims the message
 *
 * If no parser claims the message (which should not happen because
 * [GenericBankSmsParser] always returns `true`), the registry falls back
 * to [GenericBankSmsParser] so we never throw.
 */
object BankParserRegistry {

    /** Parsers sorted by descending priority. */
    private val PARSERS: List<BankSmsParser> = listOf(
        // Dedicated bank parsers (priority 100 each) — listed in priority
        // declaration order; the registry will re-sort by `priority` at call
        // time so the explicit order here is informational.
        AlRajhiParser(),
        AlinmaParser(),
        SNBParser(),
        RiyadBankParser(),
        BankAlbiladParser(),
        BSFParser(),
        SABParser(),
        SAIBParser(),
        BankAlJaziraParser(),
        MeemParser(),
        D360Parser(),
        STCBankParser(),
        UrpayParser(),
        // Fallback (priority 0) — always last.
        GenericBankSmsParser(),
    ).sortedByDescending { it.priority }

    /** Public read-only view of the registered parsers. */
    val parsers: List<BankSmsParser> get() = PARSERS

    /**
     * Parse the message using the highest-priority parser that claims it.
     * The returned [ParsedTransaction.parserName] identifies the parser that
     * handled the message so the UI / diagnostics can show it.
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
        // Should be unreachable because the generic parser matches anything.
        return GenericBankSmsParser().parse(sender, body, smsTimestampMillis)
    }

    /** Names of all registered parsers, in the order they are tried. */
    fun registeredParserNames(): List<String> = PARSERS.map { "${it.name}@${it.priority}" }
}

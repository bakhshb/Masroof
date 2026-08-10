package com.baraa.masroof.parsing.parser

import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.parsing.model.NormalizedSms
import com.baraa.masroof.parsing.model.ParseResult
import com.baraa.masroof.parsing.model.SmsParseInput

/**
 * Bank-specific SMS parser adapter.
 *
 * Stops at structured parse facts ([ParseResult] / [com.baraa.masroof.domain.model.ParsedEvent]).
 * Must not resolve ownership, self-transfer, expense/income, or final financial treatment.
 */
interface BankMessageParser {
    val bank: Bank

    fun canHandle(message: NormalizedSms, sender: String): Boolean

    fun parse(input: SmsParseInput, normalized: NormalizedSms): ParseResult
}

package com.baraa.masroof.parsing.parser

import com.baraa.masroof.parsing.model.ParseResult
import com.baraa.masroof.parsing.model.SmsParseInput

/**
 * Narrow parse entry used by SMS ingestion and bank pipelines.
 */
fun interface SmsParseGateway {
    fun parse(input: SmsParseInput): ParseResult
}

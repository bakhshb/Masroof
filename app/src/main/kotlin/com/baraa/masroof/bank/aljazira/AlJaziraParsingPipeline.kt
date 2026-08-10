package com.baraa.masroof.bank.aljazira

import com.baraa.masroof.parsing.model.NormalizedSms
import com.baraa.masroof.parsing.model.ParseResult
import com.baraa.masroof.parsing.model.SmsParseInput
import com.baraa.masroof.parsing.normalizer.MessageNormalizer
import com.baraa.masroof.parsing.parser.SmsParseGateway

/**
 * Convenience entry for normalize → AlJazira parse.
 */
class AlJaziraParsingPipeline(
    private val normalizer: MessageNormalizer = MessageNormalizer(),
    private val parser: AlJaziraMessageParser = AlJaziraMessageParser(),
) : SmsParseGateway {
    override fun parse(input: SmsParseInput): ParseResult {
        val normalized: NormalizedSms = normalizer.normalize(input.body)
        if (!parser.canHandle(normalized, input.sender)) {
            return ParseResult.Unsupported(reason = "not_bank_aljazira")
        }
        return parser.parse(input, normalized)
    }
}

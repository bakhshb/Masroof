package com.baraa.masroof.bank.aljazira

import com.baraa.masroof.parsing.model.NormalizedSms
import com.baraa.masroof.parsing.model.ParseResult
import com.baraa.masroof.parsing.model.SmsParseInput
import com.baraa.masroof.parsing.normalizer.MessageNormalizer

/**
 * Convenience entry for normalize → AlJazira parse.
 */
class AlJaziraParsingPipeline(
    private val normalizer: MessageNormalizer = MessageNormalizer(),
    private val parser: AlJaziraMessageParser = AlJaziraMessageParser(),
) {
    fun parse(input: SmsParseInput): ParseResult {
        val normalized: NormalizedSms = normalizer.normalize(input.body)
        if (!parser.canHandle(normalized, input.sender)) {
            return ParseResult.Unsupported(reason = "not_bank_aljazira")
        }
        return parser.parse(input, normalized)
    }
}

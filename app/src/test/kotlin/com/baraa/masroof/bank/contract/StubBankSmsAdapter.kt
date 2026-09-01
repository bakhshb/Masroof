package com.baraa.masroof.bank.contract

import com.baraa.masroof.bank.BankSmsAdapter
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.Confidence
import com.baraa.masroof.parsing.model.BankDetectionResult
import com.baraa.masroof.parsing.model.ParseResult
import com.baraa.masroof.parsing.model.SmsParseInput

/**
 * Minimal second-bank adapter used to prove the shared adapter contract is bank-agnostic.
 */
class StubBankSmsAdapter : BankSmsAdapter {
    override val bank: Bank = Bank("STUB_BANK")

    override fun detect(sender: String, body: String): BankDetectionResult =
        if (sender.equals("StubBank", ignoreCase = true)) {
            BankDetectionResult.Detected(
                bank = bank,
                confidence = Confidence(score = 1.0),
                evidence = emptyList(),
            )
        } else {
            BankDetectionResult.Unknown(
                reasons = listOf("sender_not_recognized_as_stub_bank"),
            )
        }

    override fun parse(input: SmsParseInput): ParseResult =
        ParseResult.Unsupported(reason = "stub_bank_parse_not_implemented")
}

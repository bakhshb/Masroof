package com.baraa.masroof.bank.contract

import com.baraa.masroof.bank.BankSmsAdapter
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.parsing.model.BankDetectionResult
import com.baraa.masroof.parsing.model.ParseResult
import com.baraa.masroof.parsing.model.SmsParseInput
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

/**
 * Shared behavioral contract every [BankSmsAdapter] implementation must satisfy.
 */
object BankSmsAdapterContract {
    fun verify(adapter: BankSmsAdapter) {
        assertFalse(
            "Adapter bank must be a known registry identity",
            adapter.bank == Bank.UNKNOWN,
        )

        val unknownDetection = adapter.detect("not-a-bank-sender", "irrelevant body")
        when (unknownDetection) {
            is BankDetectionResult.Detected ->
                assertEquals(adapter.bank, unknownDetection.bank)
            is BankDetectionResult.Unknown ->
                assertTrue(unknownDetection.reasons.isNotEmpty())
        }

        val parseResult = adapter.parse(
            SmsParseInput(
                rawSmsId = "contract-test",
                sender = "contract-sender",
                body = "contract body",
                receivedAt = Instant.parse("2026-08-11T08:00:00Z"),
            ),
        )
        assertNotNull(parseResult)
        parsedEventBank(parseResult)?.let { eventBank ->
            assertEquals(adapter.bank, eventBank)
        }
    }

    private fun parsedEventBank(result: ParseResult): Bank? =
        when (result) {
            is ParseResult.Success -> result.event.bank
            is ParseResult.Partial -> result.event?.bank
            is ParseResult.ReviewRequired -> result.event?.bank
            is ParseResult.NonFinancial -> result.event?.bank
            is ParseResult.Unsupported,
            is ParseResult.Invalid,
            -> null
        }
}

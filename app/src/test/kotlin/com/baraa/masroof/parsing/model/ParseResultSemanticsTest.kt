package com.baraa.masroof.parsing.model

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.Confidence
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.ParseStatus
import com.baraa.masroof.parsing.validator.ValidationFinding
import com.baraa.masroof.parsing.validator.ValidationSeverity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ParseResultSemanticsTest {

    @Test
    fun success_isDistinctFromReviewAndInvalid() {
        val event = ParsedEventDraft(
            rawSmsId = "sms-1",
            bank = Bank.BANK_ALJAZIRA,
            messageFamily = MessageFamily.PURCHASE,
            amount = Money.of("10.00", Currency.SAR),
            confidence = Confidence(0.9),
            parseStatus = ParseStatus.SUCCESS,
        ).toParsedEvent("evt-1")

        val success: ParseResult = ParseResult.Success(event)
        val review: ParseResult = ParseResult.ReviewRequired(
            draft = null,
            event = null,
            findings = emptyList(),
            reasons = listOf("unknown_format"),
        )
        val invalid: ParseResult = ParseResult.Invalid(
            findings = listOf(
                ValidationFinding("V-009", "missing amount", ValidationSeverity.ERROR),
            ),
        )
        val nonFinancial: ParseResult = ParseResult.NonFinancial("otp")
        val unsupported: ParseResult = ParseResult.Unsupported("other_bank")

        assertTrue(success is ParseResult.Success)
        assertTrue(review is ParseResult.ReviewRequired)
        assertTrue(invalid is ParseResult.Invalid)
        assertTrue(nonFinancial is ParseResult.NonFinancial)
        assertTrue(unsupported is ParseResult.Unsupported)
        assertFalse(success::class == review::class)
        assertFalse(success::class == invalid::class)
    }

    @Test
    fun draft_cannotCarryOccurredAtWithoutExplicitValue() {
        val draft = ParsedEventDraft(
            rawSmsId = "sms-2",
            bank = Bank.BANK_ALJAZIRA,
            messageFamily = MessageFamily.OTP,
            parseStatus = ParseStatus.NON_FINANCIAL,
            confidence = Confidence(1.0),
            occurredAt = Instant.parse("2026-08-01T00:00:00Z"),
        )
        assertTrue(draft.amount == null)
        assertTrue(draft.messageFamily == MessageFamily.OTP)
    }
}

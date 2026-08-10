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
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ParseResultSemanticsTest {

    private fun event(status: ParseStatus, amount: Money? = Money.of("10.00", Currency.SAR)) =
        ParsedEventDraft(
            rawSmsId = "sms-1",
            bank = Bank.BANK_ALJAZIRA,
            messageFamily = MessageFamily.PURCHASE,
            amount = amount,
            merchant = "Shop",
            confidence = Confidence(0.9),
            parseStatus = status,
        ).toParsedEvent("evt-1")

    @Test
    fun success_isDistinctFromReviewAndInvalid() {
        val success: ParseResult = ParseResult.Success(event(ParseStatus.SUCCESS))
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
        assertTrue(success is ParseResult.Success)
        assertTrue(review is ParseResult.ReviewRequired)
        assertTrue(invalid is ParseResult.Invalid)
        assertFalse(success::class == review::class)
    }

    @Test
    fun success_rejectsNonSuccessParseStatus() {
        assertThrows(IllegalArgumentException::class.java) {
            ParseResult.Success(event(ParseStatus.REVIEW_REQUIRED))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ParseResult.Success(event(ParseStatus.PARTIAL))
        }
    }

    @Test
    fun partial_rejectsNonPartialEventStatus() {
        assertThrows(IllegalArgumentException::class.java) {
            ParseResult.Partial(
                draft = ParsedEventDraft(rawSmsId = "sms-x"),
                event = event(ParseStatus.SUCCESS),
                findings = emptyList(),
            )
        }
    }

    @Test
    fun reviewRequired_rejectsNonReviewEventStatus() {
        assertThrows(IllegalArgumentException::class.java) {
            ParseResult.ReviewRequired(
                draft = null,
                event = event(ParseStatus.SUCCESS),
                findings = emptyList(),
                reasons = listOf("x"),
            )
        }
    }

    @Test
    fun toParsedEvent_rejectsSuccessWithoutAmountForFinancialFamily() {
        assertThrows(IllegalArgumentException::class.java) {
            ParsedEventDraft(
                rawSmsId = "sms-2",
                bank = Bank.BANK_ALJAZIRA,
                messageFamily = MessageFamily.PURCHASE,
                confidence = Confidence(0.5),
                parseStatus = ParseStatus.SUCCESS,
            ).toParsedEvent("evt-bad")
        }
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

package com.baraa.masroof.parsing.validator

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.CardReference
import com.baraa.masroof.domain.model.Confidence
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.ParseStatus
import com.baraa.masroof.parsing.model.ParsedEventDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultParsedEventValidatorTest {
    private val validator = DefaultParsedEventValidator()

    @Test
    fun missingAmountOnPurchase_isV009Error() {
        val result = validator.validate(
            ParsedEventDraft(
                rawSmsId = "sms-1",
                bank = Bank.BANK_ALJAZIRA,
                messageFamily = MessageFamily.PURCHASE,
                merchant = "Shop",
                confidence = Confidence(0.8),
                parseStatus = ParseStatus.PARTIAL,
            ),
        )
        assertTrue(result.errors.any { it.code == "V-009" })
        assertFalse(result.isAcceptableForAutomaticUse)
    }

    @Test
    fun missingMerchantOnPurchase_isV008WarningOnly() {
        val result = validator.validate(
            ParsedEventDraft(
                rawSmsId = "sms-2",
                bank = Bank.BANK_ALJAZIRA,
                messageFamily = MessageFamily.PURCHASE,
                amount = Money.of("10.00", Currency.SAR),
                confidence = Confidence(0.8),
                parseStatus = ParseStatus.SUCCESS,
            ),
        )
        assertTrue(result.warnings.any { it.code == "V-008" })
        assertTrue(result.errors.none { it.code == "V-008" })
        assertTrue(result.isAcceptableForAutomaticUse)
    }

    @Test
    fun amountEqualToCardLast4_isV001Error() {
        val result = validator.validate(
            ParsedEventDraft(
                rawSmsId = "sms-3",
                bank = Bank.BANK_ALJAZIRA,
                messageFamily = MessageFamily.PURCHASE,
                amount = Money.of("7271.00", Currency.SAR),
                cardRef = CardReference(Bank.BANK_ALJAZIRA, "7271"),
                merchant = "Shop",
                confidence = Confidence(0.5),
                parseStatus = ParseStatus.PARTIAL,
            ),
        )
        assertTrue(result.errors.any { it.code == "V-001" })
    }

    @Test
    fun amountEqualToAccountLast4_isV002Error() {
        val result = validator.validate(
            ParsedEventDraft(
                rawSmsId = "sms-4",
                bank = Bank.BANK_ALJAZIRA,
                messageFamily = MessageFamily.TRANSFER_OUT,
                amount = Money.of("3001", Currency.SAR),
                sourceAccountRef = AccountReference(Bank.BANK_ALJAZIRA, "3001"),
                confidence = Confidence(0.5),
                parseStatus = ParseStatus.PARTIAL,
            ),
        )
        assertTrue(result.errors.any { it.code == "V-002" })
    }

    @Test
    fun validPurchaseDraft_hasNoErrors() {
        val result = validator.validate(
            ParsedEventDraft(
                rawSmsId = "sms-5",
                bank = Bank.BANK_ALJAZIRA,
                messageFamily = MessageFamily.PURCHASE,
                amount = Money.of("51.99", Currency.SAR),
                cardRef = CardReference(Bank.BANK_ALJAZIRA, "7271"),
                merchant = "Keeta",
                confidence = Confidence(0.95),
                parseStatus = ParseStatus.SUCCESS,
            ),
        )
        assertEquals(0, result.errors.size)
        assertTrue(result.isAcceptableForAutomaticUse)
    }

    @Test
    fun otpFamily_doesNotRequireAmount() {
        val result = validator.validate(
            ParsedEventDraft(
                rawSmsId = "sms-6",
                bank = Bank.BANK_ALJAZIRA,
                messageFamily = MessageFamily.OTP,
                confidence = Confidence(1.0),
                parseStatus = ParseStatus.NON_FINANCIAL,
            ),
        )
        assertTrue(result.errors.none { it.code == "V-009" })
    }
}

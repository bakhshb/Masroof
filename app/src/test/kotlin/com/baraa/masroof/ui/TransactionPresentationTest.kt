package com.baraa.masroof.ui

import com.baraa.masroof.data.db.AccountIdentifierType
import com.baraa.masroof.ledger.InstitutionIdentificationSource
import com.baraa.masroof.transaction.AccountType
import com.baraa.masroof.transaction.Currency
import com.baraa.masroof.transaction.FinancialTreatment
import com.baraa.masroof.transaction.TransactionType
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

class TransactionPresentationTest {
    @Test fun creditCardShowsCreditCardInstrument() {
        val presentation = TransactionPresentationFactory.create(
            transactionId = 1,
            amount = BigDecimal("51.99"),
            type = TransactionType.ONLINE_PURCHASE,
            treatment = FinancialTreatment.EXPENSE,
            currency = "SAR",
            merchantOrBeneficiary = "Keeta",
            accountOrCardLastFourDigits = "7271",
            accountType = AccountType.CREDIT_CARD,
            transactionDate = LocalDate.of(2026, 8, 3),
            transactionTime = LocalTime.of(22, 50),
            requiresReview = false,
            exclusionReason = null,
            isBeforeTrackingStart = false,
            needsAttention = false,
            institution = com.baraa.masroof.ledger.InstitutionResolution(
                institutionDisplayName = "البنك الأهلي السعودي",
                source = InstitutionIdentificationSource.USER_CONFIRMED_MAPPING,
                confidence = 95,
                requiresReview = false,
                senderKey = "alrajhi",
            ),
            showTechnical = false,
            parserName = "GenericBankSmsParser",
            confidence = 90,
        )
        assertTrue(presentation.accountOrInstrumentLabel.contains("بطاقة ائتمانية"))
        assertTrue(presentation.accountOrInstrumentLabel.contains("7271"))
        assertEquals("البنك الأهلي السعودي", presentation.institutionDisplayName)
        assertEquals("شراء عبر الإنترنت", presentation.friendlyType)
        assertEquals("عبر الإنترنت", presentation.channelLabel)
        assertTrue(presentation.isExpense == true)
    }

    @Test fun madaPurchaseShowsBankAndMada() {
        val presentation = TransactionPresentationFactory.create(
            transactionId = 2,
            amount = BigDecimal("127.00"),
            type = TransactionType.PURCHASE,
            treatment = FinancialTreatment.EXPENSE,
            currency = "SAR",
            merchantOrBeneficiary = "MALAYSIA FOODS RESTA",
            accountOrCardLastFourDigits = "8219",
            accountType = AccountType.BANK_ACCOUNT,
            transactionDate = LocalDate.of(2026, 8, 3),
            transactionTime = LocalTime.of(13, 24),
            requiresReview = false,
            exclusionReason = null,
            isBeforeTrackingStart = false,
            needsAttention = false,
            institution = com.baraa.masroof.ledger.InstitutionResolution(
                institutionDisplayName = "البنك الأهلي السعودي",
                source = InstitutionIdentificationSource.PARSED_INSTITUTION,
                confidence = 80,
                requiresReview = false,
                senderKey = "alrajhi",
            ),
            showTechnical = false,
            parserName = "GenericBankSmsParser",
            confidence = 80,
        )
        assertEquals("نقاط البيع", presentation.channelLabel)
        // We don't surface Mada explicitly in the label (treat it as a debit instrument).
        // We DO keep the channel badge.
        assertTrue(presentation.isExpense == true)
    }

    @Test fun incomingTransferShowsReceivingAccount() {
        val presentation = TransactionPresentationFactory.create(
            transactionId = 3,
            amount = BigDecimal("4445.67"),
            type = TransactionType.TRANSFER_IN,
            treatment = FinancialTreatment.INCOME,
            currency = "SAR",
            merchantOrBeneficiary = "Account 3001",
            accountOrCardLastFourDigits = "3003",
            accountType = AccountType.BANK_ACCOUNT,
            transactionDate = LocalDate.of(2026, 8, 3),
            transactionTime = LocalTime.of(10, 38),
            requiresReview = false,
            exclusionReason = null,
            isBeforeTrackingStart = false,
            needsAttention = false,
            institution = com.baraa.masroof.ledger.InstitutionResolution(
                institutionDisplayName = "بنك الجزيرة",
                source = InstitutionIdentificationSource.USER_CONFIRMED_MAPPING,
                confidence = 95,
                requiresReview = false,
                senderKey = "jazira",
            ),
            showTechnical = false,
            parserName = "GenericBankSmsParser",
            confidence = 90,
        )
        assertTrue(presentation.isExpense == false)
        assertEquals("حوالة واردة", presentation.friendlyType)
        assertEquals("بنك الجزيرة", presentation.institutionDisplayName)
        assertTrue(presentation.accountOrInstrumentLabel.contains("3003"))
    }

    @Test fun cardPaymentIsNeverExpensePresentation() {
        val presentation = TransactionPresentationFactory.create(
            transactionId = 4,
            amount = BigDecimal("200"),
            type = TransactionType.CARD_PAYMENT,
            treatment = FinancialTreatment.CREDIT_CARD_PAYMENT,
            currency = "SAR",
            merchantOrBeneficiary = null,
            accountOrCardLastFourDigits = "3001",
            accountType = AccountType.BANK_ACCOUNT,
            transactionDate = LocalDate.of(2026, 8, 3),
            transactionTime = null,
            requiresReview = false,
            exclusionReason = null,
            isBeforeTrackingStart = false,
            needsAttention = false,
            institution = com.baraa.masroof.ledger.InstitutionResolution.Unknown,
            showTechnical = false,
            parserName = "GenericBankSmsParser",
            confidence = 50,
        )
        // Card payment must not present itself as a spending row.
        assertEquals("سداد بطاقة", presentation.friendlyType)
        assertNull("Card payment must not flag as expense", presentation.isExpense)
    }

    @Test fun technicalDetailsHiddenByDefault() {
        val presentation = TransactionPresentationFactory.create(
            transactionId = 5,
            amount = BigDecimal("20"),
            type = TransactionType.PURCHASE,
            treatment = FinancialTreatment.EXPENSE,
            currency = "SAR",
            merchantOrBeneficiary = null,
            accountOrCardLastFourDigits = "1001",
            accountType = AccountType.BANK_ACCOUNT,
            transactionDate = LocalDate.of(2026, 8, 3),
            transactionTime = null,
            requiresReview = false,
            exclusionReason = null,
            isBeforeTrackingStart = false,
            needsAttention = false,
            institution = com.baraa.masroof.ledger.InstitutionResolution.Unknown,
            showTechnical = false,
            parserName = "GenericBankSmsParser",
            confidence = 50,
        )
        assertNull(presentation.technicalDetails)
    }

    @Test fun technicalDetailsVisibleAfterToggle() {
        val presentation = TransactionPresentationFactory.create(
            transactionId = 6,
            amount = BigDecimal("30"),
            type = TransactionType.PURCHASE,
            treatment = FinancialTreatment.EXPENSE,
            currency = "SAR",
            merchantOrBeneficiary = null,
            accountOrCardLastFourDigits = "1111",
            accountType = AccountType.BANK_ACCOUNT,
            transactionDate = LocalDate.of(2026, 8, 3),
            transactionTime = null,
            requiresReview = false,
            exclusionReason = null,
            isBeforeTrackingStart = false,
            needsAttention = false,
            institution = com.baraa.masroof.ledger.InstitutionResolution.Unknown,
            showTechnical = true,
            parserName = "GenericBankSmsParser",
            confidence = 50,
        )
        assertNotNull(presentation.technicalDetails)
        assertEquals("GenericBankSmsParser", presentation.technicalDetails!!.parserName)
    }

    @Test fun unknownInstitutionReturnsArabicLabel() {
        val presentation = TransactionPresentationFactory.create(
            transactionId = 7,
            amount = BigDecimal("50"),
            type = TransactionType.PURCHASE,
            treatment = FinancialTreatment.EXPENSE,
            currency = "SAR",
            merchantOrBeneficiary = null,
            accountOrCardLastFourDigits = "1001",
            accountType = AccountType.BANK_ACCOUNT,
            transactionDate = LocalDate.now(),
            transactionTime = null,
            requiresReview = false,
            exclusionReason = null,
            isBeforeTrackingStart = false,
            needsAttention = false,
            institution = com.baraa.masroof.ledger.InstitutionResolution.Unknown,
            showTechnical = false,
            parserName = "GenericBankSmsParser",
            confidence = 50,
        )
        assertEquals("مرسل مالي غير معروف", presentation.institutionDisplayName)
        assertEquals(InstitutionIdentificationSource.UNKNOWN, presentation.institutionSource)
    }
}

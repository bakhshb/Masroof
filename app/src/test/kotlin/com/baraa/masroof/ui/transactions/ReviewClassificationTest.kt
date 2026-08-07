package com.baraa.masroof.ui.transactions

import com.baraa.masroof.data.db.DateSource
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.ledger.AccountLinkSource
import com.baraa.masroof.ledger.TransactionPostingStatus
import com.baraa.masroof.transaction.CategorySource
import com.baraa.masroof.transaction.Currency
import com.baraa.masroof.transaction.FinancialTreatment
import com.baraa.masroof.transaction.TransactionStatus
import com.baraa.masroof.transaction.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class ReviewClassificationTest {
    private fun tx(
        type: TransactionType,
        treatment: FinancialTreatment = FinancialTreatment.PENDING_REVIEW,
    ) = TransactionEntity(
        id = 1,
        uniqueFingerprint = "fp",
        smsTimestamp = 1,
        originalSender = "bank",
        transactionType = type,
        amount = BigDecimal.TEN,
        currency = Currency.SAR,
        merchantOrBeneficiary = null,
        accountOrCardLastFourDigits = "7271",
        transactionDate = null,
        transactionTime = null,
        status = TransactionStatus.COMPLETED,
        confidence = 90,
        parsingNotes = emptyList(),
        dateSource = DateSource.FROM_BODY,
        createdAt = 1,
        updatedAt = 1,
        financialTreatment = treatment,
        categorySource = CategorySource.UNCLASSIFIED,
        needsReview = true,
        accountLinkSource = AccountLinkSource.UNLINKED,
        accountLinkNeedsReview = true,
        postingStatus = TransactionPostingStatus.NEEDS_REVIEW,
    )

    @Test
    fun suggestsExpenseForPurchase() {
        assertEquals(
            FinancialTreatment.EXPENSE,
            ReviewClassification.suggestedTreatment(tx(TransactionType.PURCHASE)),
        )
    }

    @Test
    fun suggestsIncomeForIncomingTransfer() {
        assertEquals(
            FinancialTreatment.INCOME,
            ReviewClassification.suggestedTreatment(tx(TransactionType.TRANSFER_IN)),
        )
    }

    @Test
    fun suggestsExpenseForOutgoingTransferUntilUserPicksInternal() {
        assertEquals(
            FinancialTreatment.EXPENSE,
            ReviewClassification.suggestedTreatment(tx(TransactionType.TRANSFER_OUT)),
        )
    }

    @Test
    fun suggestedChoiceHighlightsOutgoingExternalTransfer() {
        val choice = ReviewClassification.suggestedChoice(tx(TransactionType.TRANSFER_OUT))
        assertEquals("transfer_out_external", choice.id)
        assertEquals(FinancialTreatment.EXPENSE, choice.treatment)
        assertTrue(choice.label.contains("صادرة"))
    }

    @Test
    fun suggestedChoiceHighlightsIncomingExternalTransfer() {
        val choice = ReviewClassification.suggestedChoice(tx(TransactionType.TRANSFER_IN))
        assertEquals("transfer_in_external", choice.id)
        assertEquals(FinancialTreatment.INCOME, choice.treatment)
        assertTrue(choice.label.contains("واردة"))
    }

    @Test
    fun choosableChoicesIncludeOutgoingAndInternalTransfers() {
        val labels = ReviewClassification.choosableChoices.map { it.label }
        assertTrue(labels.any { it.contains("حوالة صادرة خارجية") })
        assertTrue(labels.any { it.contains("حوالة واردة خارجية") })
        assertTrue(labels.any { it.contains("داخلي") })
    }

    @Test
    fun suggestsCardPaymentTreatment() {
        assertEquals(
            FinancialTreatment.CREDIT_CARD_PAYMENT,
            ReviewClassification.suggestedTreatment(tx(TransactionType.CARD_PAYMENT)),
        )
    }

    @Test
    fun preservesResolvedTreatment() {
        assertEquals(
            FinancialTreatment.INTERNAL_TRANSFER,
            ReviewClassification.suggestedTreatment(
                tx(TransactionType.TRANSFER_OUT, FinancialTreatment.INTERNAL_TRANSFER),
            ),
        )
    }

    @Test
    fun arabicLabelsArePresent() {
        assertTrue(ReviewClassification.friendlyType(tx(TransactionType.TRANSFER_IN)).contains("واردة"))
        assertTrue(ReviewClassification.treatmentLabel(FinancialTreatment.INTERNAL_TRANSFER).contains("داخلي"))
        assertTrue(ReviewClassification.treatmentLabel(FinancialTreatment.EXPENSE).contains("صادرة"))
    }

    @Test
    fun reviewReasonMentionsAmbiguousSenderOrClassification() {
        val ambiguous = reviewReason(tx(TransactionType.TRANSFER_OUT))
        assertTrue(ambiguous.contains("حساب") || ambiguous.contains("مرسل") || ambiguous.contains("مصروف"))
        val pendingOnly = reviewReason(
            tx(TransactionType.TRANSFER_OUT).copy(
                accountLinkNeedsReview = false,
                accountLinkSource = AccountLinkSource.LAST_FOUR_MATCH,
                sourceAccountId = 1L,
            ),
        )
        assertTrue(pendingOnly.contains("مصروف") || pendingOnly.contains("تحويل") || pendingOnly.contains("سداد"))
    }
}

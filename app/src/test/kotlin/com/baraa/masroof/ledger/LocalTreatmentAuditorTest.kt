package com.baraa.masroof.ledger

import com.baraa.masroof.transaction.FinancialTreatment
import com.baraa.masroof.transaction.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalTreatmentAuditorTest {
    @Test
    fun purchaseSmsAuditsToExpense() {
        val body = """
            عملية شراء
            بمبلغ: 51.99 ريال
            بطاقة: 7271
            التاجر: Store
        """.trimIndent()
        val audit = LocalTreatmentAuditor.audit(TransactionType.PURCHASE, body)
        assertEquals(FinancialTreatment.EXPENSE, audit.treatment)
        assertTrue(audit.autoApply)
    }

    @Test
    fun outgoingTransferAuditsToExpense() {
        val body = """
            حوالة صادرة
            بمبلغ: 500 ريال
            حساب: 7271
        """.trimIndent()
        val audit = LocalTreatmentAuditor.audit(TransactionType.TRANSFER_OUT, body)
        assertEquals(FinancialTreatment.EXPENSE, audit.treatment)
        assertTrue(audit.autoApply)
    }

    @Test
    fun incomingExternalTransferAuditsToIncome() {
        val body = """
            حوالة واردة
            بمبلغ: 1000 ريال
            إلى: 3003
        """.trimIndent()
        val audit = LocalTreatmentAuditor.audit(TransactionType.TRANSFER_IN, body)
        assertEquals(FinancialTreatment.INCOME, audit.treatment)
        assertTrue(audit.autoApply)
    }

    @Test
    fun internalIncomingTransferNeedsTwoAccounts() {
        val body = """
            حوالة واردة داخلية
            مبلغ: SAR 100.00
            إلى: 3003
        """.trimIndent()
        val withoutSides = LocalTreatmentAuditor.audit(
            TransactionType.INTERNAL_TRANSFER,
            body,
            hasConfirmedTwoOwnedSides = false,
        )
        assertEquals(FinancialTreatment.INTERNAL_TRANSFER, withoutSides.treatment)
        assertFalse(withoutSides.autoApply)
        val withSides = LocalTreatmentAuditor.audit(
            TransactionType.INTERNAL_TRANSFER,
            body,
            hasConfirmedTwoOwnedSides = true,
        )
        assertTrue(withSides.autoApply)
    }

    @Test
    fun cardPaymentAuditsToCreditCardPaymentWithoutAutoApply() {
        val body = """
            سداد بطاقة ائتمانية
            بمبلغ: 1250 ر.س
            بطاقة: 4444
        """.trimIndent()
        val audit = LocalTreatmentAuditor.audit(TransactionType.CARD_PAYMENT, body)
        assertEquals(FinancialTreatment.CREDIT_CARD_PAYMENT, audit.treatment)
        assertFalse(audit.autoApply)
    }

    @Test
    fun bodyCueOverridesTransferOutToInternal() {
        val audit = LocalTreatmentAuditor.audit(
            type = TransactionType.TRANSFER_OUT,
            body = "تحويل داخلي بمبلغ 200",
            hasConfirmedTwoOwnedSides = false,
        )
        assertEquals(FinancialTreatment.INTERNAL_TRANSFER, audit.treatment)
        assertFalse(audit.autoApply)
    }

    @Test
    fun walletTopUpBodyCueForcesInternalTransfer() {
        val audit = LocalTreatmentAuditor.audit(
            type = TransactionType.TRANSFER_OUT,
            body = "شحن المحفظة بمبلغ 100 من البطاقة",
            hasConfirmedTwoOwnedSides = false,
        )
        assertEquals(FinancialTreatment.INTERNAL_TRANSFER, audit.treatment)
        assertFalse(audit.autoApply)
        assertTrue(audit.reasonAr.contains("محفظة"))
    }

    @Test
    fun feeBodyCueOverridesPurchaseType() {
        val audit = LocalTreatmentAuditor.audit(
            type = TransactionType.PURCHASE,
            body = "رسوم خدمة بمبلغ 5 ريال",
        )
        assertEquals(FinancialTreatment.BANK_FEE, audit.treatment)
        assertTrue(audit.autoApply)
    }

    @Test
    fun salaryBodyCueOnUnknownTypeIsIncome() {
        val audit = LocalTreatmentAuditor.audit(
            type = TransactionType.OTHER_FINANCIAL,
            body = "إيداع راتب شهر أغسطس",
        )
        assertEquals(FinancialTreatment.INCOME, audit.treatment)
        assertTrue(audit.autoApply)
    }

    @Test
    fun cashWithdrawalBodyCue() {
        val audit = LocalTreatmentAuditor.audit(
            type = TransactionType.OTHER_FINANCIAL,
            body = "سحب نقدي من الصراف الآلي",
        )
        assertEquals(FinancialTreatment.CASH_WITHDRAWAL, audit.treatment)
    }
}

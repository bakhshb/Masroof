package com.baraa.masroof.ui

import com.baraa.masroof.data.db.DateSource
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.ledger.TransactionPostingStatus
import com.baraa.masroof.transaction.Currency
import com.baraa.masroof.transaction.FinancialTreatment
import com.baraa.masroof.transaction.TransactionStatus
import com.baraa.masroof.transaction.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class HomeDayDrillDownTest {

    @Test
    fun spendingTransactionsForDay_filtersSpendOnlyOnThatDay() {
        val day = LocalDate.of(2026, 8, 6)
        val txs = listOf(
            tx(1, day, FinancialTreatment.EXPENSE, BigDecimal("40")),
            tx(2, day, FinancialTreatment.BANK_FEE, BigDecimal("2")),
            tx(3, day, FinancialTreatment.INCOME, BigDecimal("100")),
            tx(4, day.minusDays(1), FinancialTreatment.EXPENSE, BigDecimal("9")),
            tx(5, day, FinancialTreatment.IGNORED, BigDecimal("25000")),
            tx(6, day, FinancialTreatment.EXPENSE, BigDecimal("15"), needsReview = true),
            tx(7, day, FinancialTreatment.EXPENSE, BigDecimal("8"), posting = TransactionPostingStatus.VOIDED),
        )
        val result = spendingTransactionsForDay(txs, day)
        assertEquals(listOf(2L, 1L), result.map { it.id })
    }

    private fun tx(
        id: Long,
        date: LocalDate,
        treatment: FinancialTreatment,
        amount: BigDecimal,
        needsReview: Boolean = false,
        posting: TransactionPostingStatus = TransactionPostingStatus.POSTED,
    ) = TransactionEntity(
        id = id,
        uniqueFingerprint = "fp-$id",
        smsTimestamp = id * 1_000L,
        originalSender = "Bank",
        transactionType = TransactionType.PURCHASE,
        amount = amount,
        currency = Currency.SAR,
        merchantOrBeneficiary = "Store$id",
        accountOrCardLastFourDigits = "1234",
        transactionDate = date,
        transactionTime = null,
        status = TransactionStatus.COMPLETED,
        confidence = 90,
        parsingNotes = emptyList(),
        dateSource = DateSource.FROM_BODY,
        createdAt = 0L,
        updatedAt = 0L,
        financialTreatment = treatment,
        needsReview = needsReview,
        postingStatus = posting,
    )
}

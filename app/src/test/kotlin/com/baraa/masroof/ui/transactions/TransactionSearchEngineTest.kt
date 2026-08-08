package com.baraa.masroof.ui.transactions

import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.ledger.TransactionPostingStatus
import com.baraa.masroof.transaction.AccountNature
import com.baraa.masroof.transaction.AccountType
import com.baraa.masroof.transaction.Currency
import com.baraa.masroof.transaction.FinancialTreatment
import com.baraa.masroof.transaction.TransactionStatus
import com.baraa.masroof.transaction.TransactionType
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class TransactionSearchEngineTest {
    private fun tx(id: Long, merchant: String? = null, account: Long? = null, category: Long? = null, treatment: FinancialTreatment = FinancialTreatment.EXPENSE, status: TransactionPostingStatus = TransactionPostingStatus.NEEDS_REVIEW, rawSms: String? = null) = TransactionEntity(id = id, uniqueFingerprint = "u$id", smsTimestamp = 0, originalSender = rawSms, transactionType = TransactionType.PURCHASE, amount = BigDecimal.TEN, currency = Currency.SAR, merchantOrBeneficiary = merchant, accountOrCardLastFourDigits = null, transactionDate = LocalDate.now(), transactionTime = null, status = TransactionStatus.COMPLETED, confidence = 80, parsingNotes = emptyList(), dateSource = com.baraa.masroof.data.db.DateSource.FROM_BODY, createdAt = 0, updatedAt = 0, sourceAccountId = account, financialTreatment = treatment, categoryId = category, postingStatus = status)
    private fun account(id: Long) = FinancialAccount(
        id, "حساب $id", null, AccountType.BANK_ACCOUNT, AccountNature.ASSET, Currency.SAR,
        BigDecimal.ZERO, 1, true, true, true, null, true, null,
    )
    private val categories = mapOf(99L to "مطاعم")
    @Test fun merchantSearchMatches() {
        val list = listOf(tx(1, merchant = "STARBUCKS"), tx(2, merchant = "CARREFOUR"))
        val found = TransactionSearchEngine.search(list, listOf(account(1)), categories, TransactionFilter(query = "star"))
        assertEquals(1, found.size); assertEquals(1L, found.single().id)
    }
    @Test fun categorySearchMatches() {
        val list = listOf(tx(1, category = 99L))
        val found = TransactionSearchEngine.search(list, listOf(account(1)), categories, TransactionFilter(query = "مطاعم"))
        assertEquals(1, found.size)
    }
    @Test fun accountSearchMatches() {
        val list = listOf(tx(1, account = 1L))
        val found = TransactionSearchEngine.search(list, listOf(account(1)), categories, TransactionFilter(query = "حساب 1"))
        assertEquals(1, found.size)
    }
    @Test fun senderOrInstitutionSearchMatches() {
        val list = listOf(tx(1, rawSms = "JAZIRA-BANK"))
        val found = TransactionSearchEngine.search(list, emptyList(), emptyMap(), TransactionFilter(query = "jazira"))
        assertEquals(1, found.size)
    }
    @Test fun combinedFiltersCombine() {
        val list = listOf(tx(1, treatment = FinancialTreatment.EXPENSE, status = TransactionPostingStatus.NEEDS_REVIEW), tx(2, treatment = FinancialTreatment.INCOME))
        val filter = TransactionFilter(expenses = true, needsReview = true)
        val found = TransactionSearchEngine.search(list, emptyList(), emptyMap(), filter)
        assertEquals(1, found.size); assertEquals(1L, found.single().id)
    }
    @Test fun dateRangeFilterWorks() {
        val list = listOf(tx(1).copy(transactionDate = LocalDate.now().minusDays(7)), tx(2).copy(transactionDate = LocalDate.now()))
        val filter = TransactionFilter(fromDate = LocalDate.now().minusDays(2), toDate = LocalDate.now())
        val found = TransactionSearchEngine.search(list, emptyList(), emptyMap(), filter)
        assertEquals(1, found.size); assertEquals(2L, found.single().id)
    }
    @Test fun reviewAndUnlinkedFiltersWork() {
        val list = listOf(tx(1, status = TransactionPostingStatus.NEEDS_REVIEW), tx(2, status = TransactionPostingStatus.POSTED))
        val found = TransactionSearchEngine.search(list, emptyList(), emptyMap(), TransactionFilter(needsReview = true))
        assertEquals(1, found.size)
    }
    @Test fun clearFilterResets() {
        val filter = TransactionFilter().copy(needsReview = true); assertTrue(filter.needsReview)
        val cleared = TransactionFilter(); assertFalse(cleared.needsReview); assertTrue(cleared.isEmpty)
    }
    @Test fun filterIsEmptyByDefault() {
        assertTrue(TransactionFilter().isEmpty); assertFalse(TransactionFilter(needsReview = true).isEmpty)
    }
    @Test fun searchResultCountMatches() {
        val list = listOf(tx(1, merchant = "STARBUCKS"), tx(2, merchant = "MCDONALDS"), tx(3, merchant = "CARREFOUR"))
        val found = TransactionSearchEngine.search(list, emptyList(), emptyMap(), TransactionFilter(query = "STARBUCKS"))
        assertEquals(1, found.size)
    }

    @Test fun newestSortUsesFinancialDateThenId() {
        val old = tx(9).copy(transactionDate = LocalDate.now().minusDays(1))
        val newestLowId = tx(1).copy(transactionDate = LocalDate.now())
        val newestHighId = tx(2).copy(transactionDate = LocalDate.now())
        val found = TransactionSearchEngine.search(
            listOf(old, newestLowId, newestHighId),
            emptyList(),
            emptyMap(),
            TransactionFilter(sort = TransactionSort.NEWEST),
        )
        assertEquals(listOf(2L, 1L, 9L), found.map { it.id })
    }

    @Test fun explicitAmountSortsAreAppliedAfterFiltering() {
        val low = tx(1, merchant = "متجر").copy(amount = BigDecimal.ONE)
        val high = tx(2, merchant = "متجر").copy(amount = BigDecimal("100"))
        val descending = TransactionSearchEngine.search(
            listOf(low, high),
            emptyList(),
            emptyMap(),
            TransactionFilter(query = "متجر", sort = TransactionSort.AMOUNT_HIGH_TO_LOW),
        )
        assertEquals(listOf(2L, 1L), descending.map { it.id })
    }

    @Test fun normalizedSearchIncludesSenderFriendlyTypeLast4AndAmount() {
        val row = tx(1, rawSms = "مصرف الجزيرة").copy(
            accountOrCardLastFourDigits = "1234",
            amount = BigDecimal("10000"),
            transactionType = TransactionType.PURCHASE,
        )
        listOf("الجزيره", "شراء", "1234", "10000").forEach { query ->
            assertEquals(
                query,
                1,
                TransactionSearchEngine.search(
                    listOf(row),
                    emptyList(),
                    emptyMap(),
                    TransactionFilter(query = query),
                ).size,
            )
        }
    }
}
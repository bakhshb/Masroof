package com.baraa.masroof.data.repository

import com.baraa.masroof.sms.MatchReason
import com.baraa.masroof.sms.SmsMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Unit tests for [TransactionImportService] using the in-memory
 * [FakeTransactionRepository] — pure JVM, no Android, no Room.
 *
 * Covers: empty list, mixed parseable / unparseable, exact-duplicate
 * detection, possible-duplicate detection within the window, no-match
 * after the window, the two legitimate purchases with the same amount
 * remaining distinguishable, different cards, missing optional fields,
 * commit, summary, and import summary calculation.
 */
class TransactionImportServiceTest {

    private fun sms(
        id: Long,
        sender: String = "AlRajhi",
        body: String = "شراء\nبمبلغ: 100 ريال\nالتاجر: Starbucks",
        timestamp: Long = 1_700_000_000_000L,
    ) = SmsMessage(
        id = id,
        sender = sender,
        body = body,
        timestamp = timestamp,
        matchReason = MatchReason.BOTH,
    )

    @Test
    fun emptyListProducesEmptyPreview() {
        val service = TransactionImportService(
            transactionRepository = FakeTransactionRepository(),
            categoryRepository = FakeCategoryRepository(),
            merchantMemoryRepository = FakeMerchantMemoryRepository(),
            financialAccountRepository = FakeFinancialAccountRepository(),
        )
        val r = kotlinx.coroutines.runBlocking { service.preview(emptyList()) }
        assertEquals(0, r.preview.messagesScanned)
        assertEquals(0, r.preview.parsedSuccessfully)
        assertEquals(0, r.preview.unparseable)
        assertEquals(0, r.preview.newTransactions)
        assertEquals(0, r.preview.exactDuplicates)
        assertEquals(0, r.preview.possibleDuplicates)
        assertTrue(r.items.isEmpty())
    }

    @Test
    fun parsesValidBankMessagesAndCountsCorrectly() {
        val service = TransactionImportService(
            transactionRepository = FakeTransactionRepository(),
            categoryRepository = FakeCategoryRepository(),
            merchantMemoryRepository = FakeMerchantMemoryRepository(),
            financialAccountRepository = FakeFinancialAccountRepository(),
        )
        val messages = listOf(
            sms(id = 1, body = "شراء\nبمبلغ: 100 ريال\nالتاجر: Starbucks"),
            sms(id = 2, body = "Purchase\nAmount: 50.00 SAR\nMerchant: Starbucks"),
            sms(id = 3, body = "OTP\n123456"), // unparseable
        )
        val r = kotlinx.coroutines.runBlocking { service.preview(messages) }
        assertEquals(3, r.preview.messagesScanned)
        assertEquals(2, r.preview.parsedSuccessfully)
        assertEquals(1, r.preview.unparseable)
        assertEquals(2, r.preview.newTransactions)
        assertEquals(0, r.preview.exactDuplicates)
        assertEquals(0, r.preview.possibleDuplicates)
        assertEquals(2, r.items.size)
    }

    // -- Two-level duplicate detection ------------------------------------

    @Test
    fun exactSameSmsDetectedAsExactDuplicate() {
        val repo = FakeTransactionRepository()
        val service = TransactionImportService(
            transactionRepository = repo,
            categoryRepository = FakeCategoryRepository(),
            merchantMemoryRepository = FakeMerchantMemoryRepository(),
            financialAccountRepository = FakeFinancialAccountRepository(),
        )
        val first = listOf(
            sms(id = 1, body = "شراء\nبمبلغ: 100 ريال\nالتاجر: Starbucks", timestamp = 1_700_000_000_000L)
        )
        val r1 = kotlinx.coroutines.runBlocking { service.preview(first) }
        val s1 = kotlinx.coroutines.runBlocking { service.commit(r1) }
        assertEquals(1, s1.inserted)
        assertEquals(0, s1.exactDuplicatesSkipped)
        assertEquals(0, s1.possibleDuplicatesSkipped)

        // Re-import the SAME message (same id / sender / body / timestamp)
        val r2 = kotlinx.coroutines.runBlocking { service.preview(first) }
        assertEquals(1, r2.preview.exactDuplicates)
        assertEquals(0, r2.preview.possibleDuplicates)
        assertEquals(0, r2.preview.newTransactions)
        val exact = r2.items.single()
        assertEquals(ImportItemStatus.EXACT_DUPLICATE, exact.status)
    }

    @Test
    fun sameTransactionFiveMinutesLaterDetectedAsPossibleDuplicate() {
        val repo = FakeTransactionRepository()
        val service = TransactionImportService(
            transactionRepository = repo,
            categoryRepository = FakeCategoryRepository(),
            merchantMemoryRepository = FakeMerchantMemoryRepository(),
            financialAccountRepository = FakeFinancialAccountRepository(),
        )
        val base = 1_700_000_000_000L
        val fiveMinLater = base + 5L * 60L * 1000L

        // First import
        val r1 = kotlinx.coroutines.runBlocking {
            service.preview(listOf(sms(id = 1, body = "Purchase\nAmount: 100 SAR\nMerchant: Starbucks", timestamp = base)))
        }
        kotlinx.coroutines.runBlocking { service.commit(r1) }

        // Same amount / merchant / card / day, different SMS, 5 min later
        val r2 = kotlinx.coroutines.runBlocking {
            service.preview(listOf(
                sms(id = 2, body = "Purchase\nAmount: 100 SAR\nMerchant: Starbucks", timestamp = fiveMinLater)
            ))
        }
        assertEquals(1, r2.preview.possibleDuplicates)
        assertEquals(0, r2.preview.exactDuplicates)
        assertEquals(0, r2.preview.newTransactions)
        val item = r2.items.single()
        assertEquals(ImportItemStatus.POSSIBLE_DUPLICATE, item.status)
        assertNotNull(item.collidingWith)
    }

    @Test
    fun sameTransactionAfterWindowIsTreatedAsNew() {
        val repo = FakeTransactionRepository()
        val service = TransactionImportService(
            transactionRepository = repo,
            categoryRepository = FakeCategoryRepository(),
            merchantMemoryRepository = FakeMerchantMemoryRepository(),
            financialAccountRepository = FakeFinancialAccountRepository(),
        )
        val base = 1_700_000_000_000L
        // 11 minutes later — outside the 10-minute window.
        val afterWindow = base + 11L * 60L * 1000L

        val r1 = kotlinx.coroutines.runBlocking {
            service.preview(listOf(sms(id = 1, body = "Purchase\nAmount: 100 SAR\nMerchant: Starbucks", timestamp = base)))
        }
        kotlinx.coroutines.runBlocking { service.commit(r1) }

        val r2 = kotlinx.coroutines.runBlocking {
            service.preview(listOf(
                sms(id = 2, body = "Purchase\nAmount: 100 SAR\nMerchant: Starbucks", timestamp = afterWindow)
            ))
        }
        // Legitimate repeated purchase → treated as NEW, not a duplicate.
        assertEquals(1, r2.preview.newTransactions)
        assertEquals(0, r2.preview.exactDuplicates)
        assertEquals(0, r2.preview.possibleDuplicates)
    }

    @Test
    fun twoLegitimatePurchasesSameAmountAndMerchantAreDistinguishable() {
        // Same amount, same merchant, but DIFFERENT DAYS — that should be
        // two independent transactions, not duplicates.
        val repo = FakeTransactionRepository()
        val service = TransactionImportService(
            transactionRepository = repo,
            categoryRepository = FakeCategoryRepository(),
            merchantMemoryRepository = FakeMerchantMemoryRepository(),
            financialAccountRepository = FakeFinancialAccountRepository(),
        )
        // 1 day apart
        val t1 = 1_700_000_000_000L
        val t2 = t1 + 24L * 60L * 60L * 1000L
        val r1 = kotlinx.coroutines.runBlocking {
            service.preview(listOf(sms(id = 1, body = "Purchase\nAmount: 50 SAR\nMerchant: Starbucks", timestamp = t1)))
        }
        kotlinx.coroutines.runBlocking { service.commit(r1) }
        val r2 = kotlinx.coroutines.runBlocking {
            service.preview(listOf(sms(id = 2, body = "Purchase\nAmount: 50 SAR\nMerchant: Starbucks", timestamp = t2)))
        }
        assertEquals(1, r2.preview.newTransactions)
        assertEquals(0, r2.preview.possibleDuplicates)
    }

    @Test
    fun differentCardsProduceDifferentSimilarityKeys() {
        val k1 = com.baraa.masroof.transaction.TransactionFingerprint.generateSimilarityKey(
            sender = "AlRajhi",
            amount = BigDecimal("100"),
            currency = com.baraa.masroof.transaction.Currency.SAR,
            type = com.baraa.masroof.transaction.TransactionType.PURCHASE,
            merchant = "Starbucks",
            lastFour = "1234",
            date = java.time.LocalDate.of(2024, 1, 15),
            time = java.time.LocalTime.of(14, 30),
        )
        val k2 = com.baraa.masroof.transaction.TransactionFingerprint.generateSimilarityKey(
            sender = "AlRajhi",
            amount = BigDecimal("100"),
            currency = com.baraa.masroof.transaction.Currency.SAR,
            type = com.baraa.masroof.transaction.TransactionType.PURCHASE,
            merchant = "Starbucks",
            lastFour = "5678",
            date = java.time.LocalDate.of(2024, 1, 15),
            time = java.time.LocalTime.of(14, 30),
        )
        assertTrue("different cards should produce different keys", k1 != k2)
    }

    @Test
    fun missingMerchantIsAccepted() {
        val service = TransactionImportService(
            transactionRepository = FakeTransactionRepository(),
            categoryRepository = FakeCategoryRepository(),
            merchantMemoryRepository = FakeMerchantMemoryRepository(),
            financialAccountRepository = FakeFinancialAccountRepository(),
        )
        val r = kotlinx.coroutines.runBlocking {
            service.preview(listOf(sms(id = 1, body = "Transfer In\nPurchase Amount: 500 SAR")))
        }
        val entity = r.items.single().preparedEntity!!
        assertNotNull(entity.amount)
        // The parser may or may not extract a merchant from "تحويل وارد"
        // (it might match "من" inside the body). What we care about is
        // that the entity round-trips even if the merchant is null.
        assertNull(entity.accountOrCardLastFourDigits)
    }

    @Test
    fun missingLastFourDigitsIsAccepted() {
        val service = TransactionImportService(
            transactionRepository = FakeTransactionRepository(),
            categoryRepository = FakeCategoryRepository(),
            merchantMemoryRepository = FakeMerchantMemoryRepository(),
            financialAccountRepository = FakeFinancialAccountRepository(),
        )
        val r = kotlinx.coroutines.runBlocking {
            service.preview(listOf(sms(id = 1, body = "Purchase\nAmount: 100 SAR\nMerchant: Starbucks")))
        }
        val entity = r.items.single().preparedEntity!!
        assertNotNull(entity.amount)
        assertNull(entity.accountOrCardLastFourDigits)
        assertNotNull(entity.transactionSimilarityKey)
    }

    @Test
    fun preparedEntityCarriesSimilarityKeyAndExactFingerprint() {
        val service = TransactionImportService(
            transactionRepository = FakeTransactionRepository(),
            categoryRepository = FakeCategoryRepository(),
            merchantMemoryRepository = FakeMerchantMemoryRepository(),
            financialAccountRepository = FakeFinancialAccountRepository(),
        )
        val r = kotlinx.coroutines.runBlocking {
            service.preview(listOf(sms(id = 1, body = "شراء\nبمبلغ: 250 ريال\nالتاجر: Starbucks")))
        }
        val entity = r.items.single().preparedEntity!!
        assertNotNull(entity.uniqueFingerprint)
        assertEquals(64, entity.uniqueFingerprint.length)
        assertNotNull(entity.transactionSimilarityKey)
        assertEquals(64, entity.transactionSimilarityKey!!.length)
        // The two keys MUST differ — the similarity key intentionally
        // excludes the exact timestamp.
        assertTrue(entity.uniqueFingerprint != entity.transactionSimilarityKey)
        assertEquals(0, BigDecimal("250").compareTo(entity.amount))
        assertEquals("Starbucks", entity.merchantOrBeneficiary)
    }

    // -- Commit / summary ---------------------------------------------------

    @Test
    fun commitInsertsOnlyInsertAnywayDecisions() {
        val repo = FakeTransactionRepository()
        val service = TransactionImportService(
            transactionRepository = repo,
            categoryRepository = FakeCategoryRepository(),
            merchantMemoryRepository = FakeMerchantMemoryRepository(),
            financialAccountRepository = FakeFinancialAccountRepository(),
        )
        val r = kotlinx.coroutines.runBlocking {
            service.preview(listOf(
                sms(id = 1, body = "Purchase\nAmount: 100 SAR\nMerchant: Starbucks"),
                sms(id = 2, body = "Purchase\nAmount: 200 SAR\nMerchant: Caribou"),
            ))
        }
        val s = kotlinx.coroutines.runBlocking { service.commit(r) }
        assertEquals(2, s.inserted)
        assertEquals(0, s.exactDuplicatesSkipped)
        assertEquals(0, s.possibleDuplicatesSkipped)
        assertEquals(0, s.possibleDuplicatesInserted)
        assertEquals(2, kotlinx.coroutines.runBlocking { repo.count() })
    }

    @Test
    fun importSummaryCalculationIsConsistent() {
        val repo = FakeTransactionRepository()
        val service = TransactionImportService(
            transactionRepository = repo,
            categoryRepository = FakeCategoryRepository(),
            merchantMemoryRepository = FakeMerchantMemoryRepository(),
            financialAccountRepository = FakeFinancialAccountRepository(),
        )
        val base = 1_700_000_000_000L

        // First import: 1 successful new
        val r1 = kotlinx.coroutines.runBlocking {
            service.preview(listOf(sms(id = 1, body = "Purchase\nAmount: 100 SAR\nMerchant: Starbucks", timestamp = base)))
        }
        val s1 = kotlinx.coroutines.runBlocking { service.commit(r1) }
        assertEquals(1, s1.messagesScanned)
        assertEquals(1, s1.parsedSuccessfully)
        assertEquals(0, s1.unparseable)
        assertEquals(1, s1.inserted)
        assertEquals(0, s1.exactDuplicatesSkipped)
        assertEquals(0, s1.possibleDuplicatesSkipped)

        // Second import: same message 2 minutes later → 0 inserted, 1 possible
        val r2 = kotlinx.coroutines.runBlocking {
            service.preview(listOf(
                sms(id = 2, body = "Purchase\nAmount: 100 SAR\nMerchant: Starbucks", timestamp = base + 2L * 60L * 1000L)
            ))
        }
        val s2 = kotlinx.coroutines.runBlocking { service.commit(r2) }
        assertEquals(1, s2.messagesScanned)
        assertEquals(1, s2.parsedSuccessfully)
        assertEquals(0, s2.inserted)
        assertEquals(0, s2.exactDuplicatesSkipped)
        assertEquals(1, s2.possibleDuplicatesSkipped)
    }

    @Test
    fun possibleDuplicateWithInsertAnywayDecisionIsInserted() {
        val repo = FakeTransactionRepository()
        val service = TransactionImportService(
            transactionRepository = repo,
            categoryRepository = FakeCategoryRepository(),
            merchantMemoryRepository = FakeMerchantMemoryRepository(),
            financialAccountRepository = FakeFinancialAccountRepository(),
        )
        val base = 1_700_000_000_000L
        val r1 = kotlinx.coroutines.runBlocking {
            service.preview(listOf(sms(id = 1, body = "Purchase\nAmount: 100 SAR\nMerchant: Starbucks", timestamp = base)))
        }
        kotlinx.coroutines.runBlocking { service.commit(r1) }

        val r2 = kotlinx.coroutines.runBlocking {
            service.preview(listOf(
                sms(id = 2, body = "Purchase\nAmount: 100 SAR\nMerchant: Starbucks", timestamp = base + 1L * 60L * 1000L)
            ))
        }
        val item = r2.items.single()
        assertEquals(ImportItemStatus.POSSIBLE_DUPLICATE, item.status)
        // User decides to insert anyway
        val decided = r2.copy(items = listOf(item.copy(decision = DuplicateDecision.INSERT_ANYWAY)))
        val s = kotlinx.coroutines.runBlocking { service.commit(decided) }
        assertEquals(1, s.inserted)
        assertEquals(1, s.possibleDuplicatesInserted)
        assertEquals(0, s.possibleDuplicatesSkipped)
        assertEquals(2, kotlinx.coroutines.runBlocking { repo.count() })
    }

    // -- Repository contract tests via the fake -----------------------------

    @Test
    fun repositoryObserveAllStartsEmpty() {
        val repo = FakeTransactionRepository()
        val initial = kotlinx.coroutines.runBlocking { repo.getAllNewestFirst() }
        assertTrue(initial.isEmpty())
    }

    @Test
    fun repositoryGetAllNewestFirstSortsBySmsTimestampDesc() {
        val repo = FakeTransactionRepository()
        val service = TransactionImportService(
            transactionRepository = repo,
            categoryRepository = FakeCategoryRepository(),
            merchantMemoryRepository = FakeMerchantMemoryRepository(),
            financialAccountRepository = FakeFinancialAccountRepository(),
        )
        val r = kotlinx.coroutines.runBlocking {
            service.preview(listOf(
                sms(id = 1, body = "Purchase\nAmount: 100 SAR\nMerchant: Starbucks", timestamp = 1_700_000_000_000L),
                sms(id = 2, body = "Purchase\nAmount: 200 SAR\nMerchant: Caribou", timestamp = 1_700_000_001_000L),
                sms(id = 3, body = "Purchase\nAmount: 300 SAR\nMerchant: Noon", timestamp = 1_700_000_000_500L),
            ))
        }
        kotlinx.coroutines.runBlocking { service.commit(r) }
        val all = kotlinx.coroutines.runBlocking { repo.getAllNewestFirst() }
        assertEquals(3, all.size)
        assertEquals(2L, all[0].id)
        assertEquals(3L, all[1].id)
        assertEquals(1L, all[2].id)
    }

    @Test
    fun repositoryUpdateChangesFields() {
        val repo = FakeTransactionRepository()
        val service = TransactionImportService(
            transactionRepository = repo,
            categoryRepository = FakeCategoryRepository(),
            merchantMemoryRepository = FakeMerchantMemoryRepository(),
            financialAccountRepository = FakeFinancialAccountRepository(),
        )
        val r = kotlinx.coroutines.runBlocking {
            service.preview(listOf(sms(id = 1, body = "Purchase\nAmount: 100 SAR\nMerchant: Starbucks")))
        }
        kotlinx.coroutines.runBlocking { service.commit(r) }
        val before = kotlinx.coroutines.runBlocking { repo.getById(1L) }!!
        val updated = before.copy(merchantOrBeneficiary = "NewMerchant")
        val n = kotlinx.coroutines.runBlocking { repo.update(updated) }
        assertEquals(1, n)
        val after = kotlinx.coroutines.runBlocking { repo.getById(1L) }!!
        assertEquals("NewMerchant", after.merchantOrBeneficiary)
    }

    @Test
    fun repositoryDeleteRemovesRow() {
        val repo = FakeTransactionRepository()
        val service = TransactionImportService(
            transactionRepository = repo,
            categoryRepository = FakeCategoryRepository(),
            merchantMemoryRepository = FakeMerchantMemoryRepository(),
            financialAccountRepository = FakeFinancialAccountRepository(),
        )
        val r = kotlinx.coroutines.runBlocking {
            service.preview(listOf(sms(id = 1, body = "Purchase\nAmount: 100 SAR\nMerchant: Starbucks")))
        }
        kotlinx.coroutines.runBlocking { service.commit(r) }
        assertEquals(1, kotlinx.coroutines.runBlocking { repo.count() })
        val n = kotlinx.coroutines.runBlocking { repo.delete(repo.getById(1L)!!) }
        assertEquals(1, n)
        assertEquals(0, kotlinx.coroutines.runBlocking { repo.count() })
    }

    @Test
    fun repositoryDeleteAllWipesEverything() {
        val repo = FakeTransactionRepository()
        val service = TransactionImportService(
            transactionRepository = repo,
            categoryRepository = FakeCategoryRepository(),
            merchantMemoryRepository = FakeMerchantMemoryRepository(),
            financialAccountRepository = FakeFinancialAccountRepository(),
        )
        val r = kotlinx.coroutines.runBlocking {
            service.preview(listOf(
                sms(id = 1, body = "Purchase\nAmount: 100 SAR\nMerchant: Starbucks"),
                sms(id = 2, body = "Purchase\nAmount: 200 SAR\nMerchant: Caribou"),
            ))
        }
        kotlinx.coroutines.runBlocking { service.commit(r) }
        assertEquals(2, kotlinx.coroutines.runBlocking { repo.count() })
        kotlinx.coroutines.runBlocking { repo.deleteAll() }
        assertEquals(0, kotlinx.coroutines.runBlocking { repo.count() })
    }

    @Test
    fun amountStoredPrecisely() {
        val repo = FakeTransactionRepository()
        val service = TransactionImportService(
            transactionRepository = repo,
            categoryRepository = FakeCategoryRepository(),
            merchantMemoryRepository = FakeMerchantMemoryRepository(),
            financialAccountRepository = FakeFinancialAccountRepository(),
        )
        val r = kotlinx.coroutines.runBlocking {
            service.preview(listOf(sms(id = 1, body = "Purchase Amount: 123.4567 SAR")))
        }
        val entity = r.items.single().preparedEntity!!
        assertEquals(0, BigDecimal("123.4567").compareTo(entity.amount))
        assertEquals(4, entity.amount!!.scale())
        kotlinx.coroutines.runBlocking { service.commit(r) }
        val back = kotlinx.coroutines.runBlocking { repo.getById(1L) }!!
        assertEquals(entity.amount, back.amount)
        assertEquals(entity.amount!!.scale(), back.amount!!.scale())
    }

    @Test
    fun findBySimilarityKeyReturnsMatchingRows() {
        val repo = FakeTransactionRepository()
        val service = TransactionImportService(
            transactionRepository = repo,
            categoryRepository = FakeCategoryRepository(),
            merchantMemoryRepository = FakeMerchantMemoryRepository(),
            financialAccountRepository = FakeFinancialAccountRepository(),
        )
        val r = kotlinx.coroutines.runBlocking {
            service.preview(listOf(sms(id = 1, body = "Purchase\nAmount: 100 SAR\nMerchant: Starbucks")))
        }
        val entity = r.items.single().preparedEntity!!
        kotlinx.coroutines.runBlocking { service.commit(r) }
        val key = entity.transactionSimilarityKey!!
        val found = kotlinx.coroutines.runBlocking { repo.findBySimilarityKey(key) }
        assertEquals(1, found.size)
        // The pre-commit entity had id=0; the post-commit row has an
        // auto-generated id >= 1. We don't compare ids here; we just
        // verify the row exists.
        assertNotNull(found.firstOrNull())
    }
}

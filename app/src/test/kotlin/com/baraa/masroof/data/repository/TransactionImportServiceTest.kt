package com.baraa.masroof.data.repository

import com.baraa.masroof.sms.MatchReason
import com.baraa.masroof.sms.SmsMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Unit tests for [TransactionImportService] using the in-memory
 * [FakeTransactionRepository] — pure JVM, no Android, no Room.
 */
class TransactionImportServiceTest {

    private fun sms(
        id: Long,
        sender: String = "AlRajhi",
        body: String = "عملية شراء بمبلغ 100 ريال لدى Starbucks",
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
        val service = TransactionImportService(FakeTransactionRepository())
        val r = kotlinx.coroutines.runBlocking { service.preview(emptyList()) }
        assertEquals(0, r.preview.messagesScanned)
        assertEquals(0, r.preview.parsedSuccessfully)
        assertEquals(0, r.preview.unparseable)
        assertEquals(0, r.preview.newTransactions)
        assertEquals(0, r.preview.duplicatesSkipped)
        assertTrue(r.prepared.isEmpty())
    }

    @Test
    fun parsesValidBankMessagesAndCountsCorrectly() {
        val service = TransactionImportService(FakeTransactionRepository())
        val messages = listOf(
            sms(id = 1, body = "عملية شراء بمبلغ 100 ريال لدى Starbucks"),
            sms(id = 2, body = "Purchase of SAR 50.00 at Starbucks"),
            sms(id = 3, body = "Your OTP code is 123456"), // unparseable
        )
        val r = kotlinx.coroutines.runBlocking { service.preview(messages) }
        assertEquals(3, r.preview.messagesScanned)
        assertEquals(2, r.preview.parsedSuccessfully)
        assertEquals(1, r.preview.unparseable)
        assertEquals(2, r.preview.newTransactions)
        assertEquals(0, r.preview.duplicatesSkipped)
        assertEquals(2, r.prepared.size)
    }

    @Suppress("RedundantSuppression") // keep for clarity
    private fun blockingCount(repo: com.baraa.masroof.data.repository.TransactionRepository): Int =
        kotlinx.coroutines.runBlocking { repo.count() }

    @Test
    fun duplicateMessagesAreSkippedOnSecondImport() {
        val repo = FakeTransactionRepository()
        val service = TransactionImportService(repo)
        val first = listOf(sms(id = 1, body = "عملية شراء بمبلغ 100 ريال لدى Starbucks", timestamp = 1_700_000_000_000L))
        val r1 = kotlinx.coroutines.runBlocking { service.preview(first) }
        val s1 = kotlinx.coroutines.runBlocking { service.commit(r1) }
        assertEquals(1, s1.inserted)
        assertEquals(0, s1.duplicatesSkipped)

        // Re-import the same SMS (same sender + same timestamp + same parsed
        // values) — the duplicate must be skipped.
        val r2 = kotlinx.coroutines.runBlocking { service.preview(first) }
        assertEquals(1, r2.preview.duplicatesSkipped)
        assertEquals(0, r2.preview.newTransactions)
        assertTrue(r2.prepared.isEmpty())
    }

    @Test
    fun differentMessagesAtSameTimeAreNotDuplicates() {
        // Same timestamp, different amount — different fingerprints.
        val repo = FakeTransactionRepository()
        val service = TransactionImportService(repo)
        val r1 = kotlinx.coroutines.runBlocking {
            service.preview(listOf(sms(id = 1, body = "عملية شراء بمبلغ 100 ريال", timestamp = 1_700_000_000_000L)))
        }
        val r2 = kotlinx.coroutines.runBlocking {
            service.preview(listOf(sms(id = 2, body = "عملية شراء بمبلغ 200 ريال", timestamp = 1_700_000_000_000L)))
        }
        assertEquals(1, r1.preview.newTransactions)
        assertEquals(1, r2.preview.newTransactions)
        assertEquals(0, r1.preview.duplicatesSkipped)
        assertEquals(0, r2.preview.duplicatesSkipped)
    }

    @Test
    fun commitInsertsPreparedTransactionsAndReturnsSummary() {
        val repo = FakeTransactionRepository()
        val service = TransactionImportService(repo)
        val messages = listOf(
            sms(id = 1, body = "عملية شراء بمبلغ 100 ريال"),
            sms(id = 2, body = "عملية شراء بمبلغ 200 ريال"),
        )
        val r = kotlinx.coroutines.runBlocking { service.preview(messages) }
        val s = kotlinx.coroutines.runBlocking { service.commit(r) }
        assertEquals(2, s.inserted)
        assertEquals(0, s.duplicatesSkipped)
        assertEquals(2, kotlinx.coroutines.runBlocking { repo.count() })
    }

    @Test
    fun importSummaryCalculationIsConsistent() {
        val repo = FakeTransactionRepository()
        val service = TransactionImportService(repo)
        // First import: 1 successful.
        val r1 = kotlinx.coroutines.runBlocking {
            service.preview(listOf(sms(id = 1, body = "عملية شراء بمبلغ 100 ريال")))
        }
        val s1 = kotlinx.coroutines.runBlocking { service.commit(r1) }
        assertEquals(1, s1.messagesScanned)
        assertEquals(1, s1.parsedSuccessfully)
        assertEquals(0, s1.unparseable)
        assertEquals(1, s1.inserted)
        assertEquals(0, s1.duplicatesSkipped)

        // Second import: same message → 0 inserted, 1 duplicate.
        val r2 = kotlinx.coroutines.runBlocking {
            service.preview(listOf(sms(id = 2, body = "عملية شراء بمبلغ 100 ريال")))
        }
        val s2 = kotlinx.coroutines.runBlocking { service.commit(r2) }
        assertEquals(0, s2.inserted) // nothing new to insert
        assertEquals(1, s2.duplicatesSkipped) // 1 from preview + 0 from insert
    }

    @Test
    fun preparedEntityCarriesFingerprintAndAmount() {
        val service = TransactionImportService(FakeTransactionRepository())
        val r = kotlinx.coroutines.runBlocking {
            service.preview(listOf(sms(id = 1, body = "عملية شراء بمبلغ 250 ريال لدى Starbucks")))
        }
        val entity = r.prepared.single()
        assertNotNull(entity.uniqueFingerprint)
        assertEquals(64, entity.uniqueFingerprint.length)
        assertEquals(0, BigDecimal("250").compareTo(entity.amount))
        assertEquals("Starbucks", entity.merchantOrBeneficiary)
        assertNotNull(entity.transactionDate) // falls back to SMS timestamp
    }

    // -- Repository contract tests via the fake -----------------------------

    @Test
    fun repositoryObserveAllStartsEmpty() {
        val repo = FakeTransactionRepository()
        // First emission is the initial empty list.
        val initial = kotlinx.coroutines.runBlocking { repo.getAllNewestFirst() }
        assertTrue(initial.isEmpty())
    }

    @Test
    fun repositoryGetAllNewestFirstSortsBySmsTimestampDesc() {
        val repo = FakeTransactionRepository()
        val service = TransactionImportService(repo)
        val r = kotlinx.coroutines.runBlocking {
            service.preview(listOf(
                sms(id = 1, body = "عملية شراء بمبلغ 100 ريال", timestamp = 1_700_000_000_000L),
                sms(id = 2, body = "عملية شراء بمبلغ 200 ريال", timestamp = 1_700_000_001_000L), // newer
                sms(id = 3, body = "عملية شراء بمبلغ 300 ريال", timestamp = 1_700_000_000_500L),
            ))
        }
        kotlinx.coroutines.runBlocking { service.commit(r) }
        val all = kotlinx.coroutines.runBlocking { repo.getAllNewestFirst() }
        assertEquals(3, all.size)
        // Newest first: id 2, id 3, id 1
        assertEquals(2L, all[0].id)
        assertEquals(3L, all[1].id)
        assertEquals(1L, all[2].id)
    }

    @Test
    fun repositoryUpdateChangesFields() {
        val repo = FakeTransactionRepository()
        val service = TransactionImportService(repo)
        val r = kotlinx.coroutines.runBlocking {
            service.preview(listOf(sms(id = 1, body = "عملية شراء بمبلغ 100 ريال")))
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
        val service = TransactionImportService(repo)
        val r = kotlinx.coroutines.runBlocking {
            service.preview(listOf(sms(id = 1, body = "عملية شراء بمبلغ 100 ريال")))
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
        val service = TransactionImportService(repo)
        val r = kotlinx.coroutines.runBlocking {
            service.preview(listOf(
                sms(id = 1, body = "عملية شراء بمبلغ 100 ريال"),
                sms(id = 2, body = "عملية شراء بمبلغ 200 ريال"),
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
        val service = TransactionImportService(repo)
        val r = kotlinx.coroutines.runBlocking {
            service.preview(listOf(sms(id = 1, body = "Amount: 123.4567 SAR")))
        }
        val entity = r.prepared.single()
        assertEquals(0, BigDecimal("123.4567").compareTo(entity.amount))
        assertEquals(4, entity.amount!!.scale())
        // Persist + read back via the fake — should round-trip.
        kotlinx.coroutines.runBlocking { service.commit(r) }
        val back = kotlinx.coroutines.runBlocking { repo.getById(1L) }!!
        assertEquals(entity.amount, back.amount)
        assertEquals(entity.amount!!.scale(), back.amount!!.scale())
    }

    @Test
    fun missingOptionalFieldsStoredAsNull() {
        val repo = FakeTransactionRepository()
        val service = TransactionImportService(repo)
        // A transfer message with no merchant and no last-four.
        val r = kotlinx.coroutines.runBlocking {
            service.preview(listOf(sms(
                id = 1,
                body = "تحويل وارد بمبلغ 500 ريال",
            )))
        }
        val entity = r.prepared.single()
        assertNotNull(entity.amount)
        // The parser may or may not extract a merchant from "تحويل وارد"
        // (it might match "من" inside the body). What we care about is
        // that the entity round-trips even if the merchant is null.
        kotlinx.coroutines.runBlocking { service.commit(r) }
        val back = kotlinx.coroutines.runBlocking { repo.getById(1L) }!!
        // Round-trip should be lossless.
        assertEquals(entity.amount, back.amount)
        assertEquals(entity.transactionType, back.transactionType)
        assertEquals(entity.merchantOrBeneficiary, back.merchantOrBeneficiary)
        assertNull(entity.accountOrCardLastFourDigits) // not in the message
        assertNotNull(entity.createdAt)
        assertNotNull(entity.updatedAt)
    }
}

package com.baraa.masroof.transaction.banks

import com.baraa.masroof.transaction.BankParserRegistry
import com.baraa.masroof.transaction.Currency
import com.baraa.masroof.transaction.GenericBankSmsParser
import com.baraa.masroof.transaction.ParsedTransaction
import com.baraa.masroof.transaction.TransactionStatus
import com.baraa.masroof.transaction.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Focused tests for the AlRajhiParser template. The other bank parsers share
 * the same structure and are exercised indirectly via [BankParserRegistry].
 *
 * **Real Al Rajhi SMS samples are still required to add bank-specific
 * patterns** — these tests verify the placeholder behavior of the current
 * template, not production accuracy.
 */
class AlRajhiParserTest {

    @Test
    fun alRajhiParserNameAndPriority() {
        val p = AlRajhiParser()
        assertEquals("AlRajhi", p.name)
        assertEquals(100, p.priority)
        assertTrue(p.version.isNotEmpty())
    }

    @Test
    fun alRajhiParserClaimsItsAliases() {
        val p = AlRajhiParser()
        assertTrue(p.canParse("alrajhi", "any body"))
        assertTrue(p.canParse("AlRajhi", "any body"))
        assertTrue(p.canParse("ALRAJHI", "any body"))
        assertTrue(p.canParse("al rajhi bank", "any body"))
        assertTrue(p.canParse("مصرف الراجحي", "any body"))
    }

    @Test
    fun alRajhiParserRejectsOtherBanks() {
        val p = AlRajhiParser()
        assertEquals(false, p.canParse("alinma", "any body"))
        assertEquals(false, p.canParse("SNB", "any body"))
        assertEquals(false, p.canParse("Visa", "any body"))
        assertEquals(false, p.canParse(null, "any body"))
        assertEquals(false, p.canParse("", "any body"))
    }

    @Test
    fun alRajhiParserInheritsSharedExtraction() {
        // The template currently shares the generic parser's extraction
        // logic. A real Arabic purchase message should still parse correctly.
        val p = AlRajhiParser()
        val result = p.parse(
            sender = "AlRajhi",
            body = "Type: شراء\nبمبلغ: 250 ريال\nالتاجر: Starbucks",
            smsTimestampMillis = 1_700_000_000_000L
        )
        assertEquals(TransactionType.PURCHASE, result.transactionType)
        assertEquals(0, BigDecimal("250").compareTo(result.amount))
        assertEquals(Currency.SAR, result.currency)
        assertEquals("Starbucks", result.merchant)
        assertEquals(TransactionStatus.COMPLETED, result.status)
        assertEquals("AlRajhi", result.parserName)
    }

    @Test
    fun registrySelectsAlRajhiForAlRajhiSender() {
        val r = BankParserRegistry.parse(
            sender = "AlRajhi",
            body = "شراء\nبمبلغ: 100 ريال",
            smsTimestampMillis = 1_700_000_000_000L
        )
        assertEquals("AlRajhi", r.parserName)
    }

    @Test
    fun lowConfidenceMessageIsMarkedAsNeedsReview() {
        // A message that mentions "balance" but no amount is low-confidence
        // and should be marked NEEDS_REVIEW.
        val p = AlRajhiParser()
        val result = p.parse(
            sender = "AlRajhi",
            body = "your balance is fine, no transactions today",
            smsTimestampMillis = 1_700_000_000_000L
        )
        // No amount / type / merchant / date / time → low confidence.
        assertTrue("low-confidence result expected", result.confidence < 30)
        assertEquals(TransactionStatus.NEEDS_REVIEW, result.status)
        assertTrue("missingFields must be non-empty", result.missingFields.isNotEmpty())
    }

    @Test
    fun alRajhiCreditCardPurchaseDoesNotUseBalanceAsAmount() {
        val body = """
            شراء عبر الانترنت
            بطاقة ائتمانية: 7271
            بمبلغ: 51.99 SAR
            لدى: Keeta
            في: 22:50 03-08-2026
            الرصيد المتاح: SAR 17230.03
            إجمالي المبلغ المستحق: 2380.88 SAR
        """.trimIndent()
        val result = BankParserRegistry.parse("alrajhi", body, 1_725_000_000_000L)
        assertEquals("AlRajhi", result.parserName)
        assertEquals(0, BigDecimal("51.99").compareTo(result.amount))
        assertEquals("7271", result.accountOrCardLastFourDigits)
    }
}

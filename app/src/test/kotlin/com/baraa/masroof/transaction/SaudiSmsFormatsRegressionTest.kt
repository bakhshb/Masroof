package com.baraa.masroof.transaction

import com.baraa.masroof.data.db.AccountIdentifierType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

/**
 * Anonymized Saudi bank SMS format regressions (POS, loan installment,
 * external transfer, internal transfer).
 */
class SaudiSmsFormatsRegressionTest {
    private val parser = GenericBankSmsParser()

    @Test
    fun parsesMultilinePosApplePayPurchase() {
        val r = parser.parse(
            sender = "AlRajhi",
            body = """
                POS Purchase (Apple Pay)
                Credit Card: 3478
                at :Market(D183)
                of: 121.85 SAR
                on : 2026-07-27 14:37
                Available Balance: 19247.72 SAR
                Due Amount: 0 SAR
            """.trimIndent(),
            smsTimestampMillis = null,
        )
        assertEquals(TransactionType.PURCHASE, r.transactionType)
        assertEquals(0, BigDecimal("121.85").compareTo(r.amount))
        assertEquals("3478", r.accountOrCardLastFourDigits)
        assertTrue(r.merchant!!.contains("Market"))
        assertEquals(LocalDate.of(2026, 7, 27), r.transactionDate)
        assertEquals(LocalTime.of(14, 37), r.transactionTime)
        assertTrue(r.amount!!.compareTo(BigDecimal("19247.72")) != 0)
    }

    @Test
    fun parsesLoanInstallmentDebit() {
        val r = parser.parse(
            sender = "SNB",
            body = """
                خصم: قسط تمويل
                من: 3001
                القسط: SAR 3,036.11
                المبلغ المتبقي: SAR 36,433.36
                لـ: تمويل شخصي
                في: 2026-07-27 01:12
            """.trimIndent(),
            smsTimestampMillis = null,
        )
        assertEquals(TransactionType.LOAN_INSTALLMENT, r.transactionType)
        assertEquals(0, BigDecimal("3036.11").compareTo(r.amount))
        assertEquals("3001", r.accountOrCardLastFourDigits)
        assertEquals(LocalDate.of(2026, 7, 27), r.transactionDate)
        // Remaining balance must never become the installment amount.
        assertTrue(r.amount!!.compareTo(BigDecimal("36433.36")) != 0)
        assertTrue(
            r.identifierEvidence.any {
                it.type == AccountIdentifierType.ACCOUNT_LAST4 && it.lastFour == "3001"
            },
        )
    }

    @Test
    fun parsesExternalOutgoingTransferAccepted() {
        val r = BankParserRegistry.parse(
            "البنك الأهلي السعودي",
            """
                عملية حوالة مالية صادرة مقبولة
                خصمت من حساب: 3001
                الى: RECV NAME**
                مبلغ العملية: 500.00 SAR
                المعرف البديل \الايبان : 0107
                [البنك الأهلي السعودي]
                في: 2026-07-27 07:42
                رقم المعاملة: 2BTMS10742701397
            """.trimIndent(),
            1_725_000_000_000L,
        )
        assertEquals("SNB", r.parserName)
        assertEquals(TransactionType.TRANSFER_OUT, r.transactionType)
        assertEquals(0, BigDecimal("500.00").compareTo(r.amount))
        assertTrue(r.identifierEvidence.any { it.lastFour == "3001" })
        // IBAN last-4 is evidence, not the transfer amount.
        assertTrue(r.amount!!.compareTo(BigDecimal("0107")) != 0)
        assertEquals(LocalDate.of(2026, 7, 27), r.transactionDate)
    }

    @Test
    fun parsesInternalTransferBetweenOwnAccounts() {
        val r = parser.parse(
            sender = "SNB",
            body = """
                حوالة صادرة بين حساباتك
                من: 3001
                مبلغ: SAR 5,500.00
                إلى: 3002
                في: 2026-07-27 07:48
            """.trimIndent(),
            smsTimestampMillis = null,
        )
        assertEquals(TransactionType.INTERNAL_TRANSFER, r.transactionType)
        assertEquals(0, BigDecimal("5500.00").compareTo(r.amount))
        assertEquals(LocalDate.of(2026, 7, 27), r.transactionDate)
        assertTrue(
            r.identifierEvidence.any {
                it.lastFour == "3001" && it.role == IdentifierRole.SOURCE
            },
        )
        assertTrue(
            r.identifierEvidence.any {
                it.lastFour == "3002" && it.role == IdentifierRole.DESTINATION
            },
        )
        assertNotNull(r.amount)
    }

    @Test
    fun parsesExternalIncomingTransfer() {
        val r = parser.parse(
            sender = "SNB",
            body = """
                عملية حوالة مالية واردة
                أودعت إلى حساب: 3001
                القيمة: 1,000.00 SAR
                من: نجاه ط. بنتن
                [بنك الرياض]
                خصمت من حساب : 9941
                في: 2026-07-30 21:21
                رقم المعاملة: 2BTMS12121410751
            """.trimIndent(),
            smsTimestampMillis = null,
        )
        assertEquals(TransactionType.TRANSFER_IN, r.transactionType)
        assertEquals(0, BigDecimal("1000.00").compareTo(r.amount))
        assertEquals(LocalDate.of(2026, 7, 30), r.transactionDate)
        assertTrue(r.merchant!!.contains("نجاه"))
        assertTrue(
            r.identifierEvidence.any {
                it.lastFour == "3001" && it.role == IdentifierRole.DESTINATION
            },
        )
        // Counterparty account at other bank may appear, but user's credit is 3001.
        assertEquals("3001", r.accountOrCardLastFourDigits)
    }

    @Test
    fun parsesBillPaymentSadadStyle() {
        val r = parser.parse(
            sender = "SNB",
            body = """
                سداد فاتورة
                من: 3001
                مبلغ: SAR 2,336.00
                مفوتر: 153
                الخدمة: ايجار
                رقم الفاتورة: 10026519633
                في: 2026-08-01 12:19
            """.trimIndent(),
            smsTimestampMillis = null,
        )
        assertEquals(TransactionType.BILL_PAYMENT, r.transactionType)
        assertEquals(0, BigDecimal("2336.00").compareTo(r.amount))
        assertEquals("3001", r.accountOrCardLastFourDigits)
        assertEquals("ايجار", r.merchant)
        assertEquals(LocalDate.of(2026, 8, 1), r.transactionDate)
        // Invoice / biller codes must not become the account last-four.
        assertTrue(r.identifierEvidence.none { it.lastFour == "153" })
    }
}

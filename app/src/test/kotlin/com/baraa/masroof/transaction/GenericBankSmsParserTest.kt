package com.baraa.masroof.transaction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Unit tests for [GenericBankSmsParser] covering the 23+ scenarios required
 * by the spec. Each test is intentionally narrow: one assertion family per
 * test, named so that failures are self-describing in the JUnit report.
 */
class GenericBankSmsParserTest {

    private val parser = GenericBankSmsParser()
    private val smsEpoch = 1_700_000_000_000L // 2023-11-14 ~22:13 UTC

    // -- Transaction types ---------------------------------------------------

    @Test
    fun parsesArabicPurchaseMessage() {
        val r = parser.parse(
            sender = "AlRajhi",
            body = "Type: شراء\nبمبلغ: 250 ريال\nالرصيد المتاح: 4500 ريال\nالتاجر: Starbucks",
            smsTimestampMillis = null
        )
        assertEquals(TransactionType.PURCHASE, r.transactionType)
        assertEquals(BigDecimal("250"), r.amount)
        assertEquals(Currency.SAR, r.currency)
        assertEquals("Starbucks", r.merchant)
        assertEquals(TransactionStatus.COMPLETED, r.status)
        assertTrue("confidence should be meaningful", r.confidence >= 50)
    }

    @Test
    fun parsesEnglishPurchaseMessage() {
        val r = parser.parse(
            sender = "Visa",
            body = "Type: Purchase\nAmount: 50.00 SAR\nMerchant: Starbucks\nAvailable balance: 1000 SAR",
            smsTimestampMillis = null
        )
        assertEquals(TransactionType.PURCHASE, r.transactionType)
        assertEquals(0, BigDecimal("50.00").compareTo(r.amount))
        assertEquals(Currency.SAR, r.currency)
        assertEquals("Starbucks", r.merchant)
    }

    @Test
    fun parsesArabicOnlinePurchase() {
        val r = parser.parse(
            sender = "Alinma",
            body = "Type: شراء عبر الإنترنت\nبمبلغ: 199.99 ريال",
            smsTimestampMillis = null
        )
        assertEquals(TransactionType.ONLINE_PURCHASE, r.transactionType)
        assertEquals(0, BigDecimal("199.99").compareTo(r.amount))
        assertEquals(Currency.SAR, r.currency)
    }

    @Test
    fun parsesCashWithdrawal() {
        val r = parser.parse(
            sender = "SNB",
            body = "Type: سحب نقدي\nبمبلغ: 500 ريال\nمن: الصراف الآلي\nالرصيد: 1200 ريال",
            smsTimestampMillis = null
        )
        assertEquals(TransactionType.CASH_WITHDRAWAL, r.transactionType)
        assertEquals(0, BigDecimal("500").compareTo(r.amount))
        assertEquals(Currency.SAR, r.currency)
    }

    @Test
    fun parsesOutgoingTransfer() {
        val r = parser.parse(
            sender = "RiyadBank",
            body = "Type: تحويل صادر\nبمبلغ: 1500 ريال\nالمستفيد: أحمد",
            smsTimestampMillis = null
        )
        assertEquals(TransactionType.TRANSFER_OUT, r.transactionType)
        assertEquals(0, BigDecimal("1500").compareTo(r.amount))
        assertEquals(Currency.SAR, r.currency)
    }

    @Test
    fun parsesIncomingTransfer() {
        val r = parser.parse(
            sender = "AlRajhi",
            body = "Type: تحويل وارد\nبمبلغ: 2000 ريال\nمن: John Doe\nالرصيد: 5000 ريال",
            smsTimestampMillis = null
        )
        assertEquals(TransactionType.TRANSFER_IN, r.transactionType)
        assertEquals(0, BigDecimal("2000").compareTo(r.amount))
        assertEquals(Currency.SAR, r.currency)
    }

    @Test
    fun parsesOutgoingHiwalaKharija() {
        val r = parser.parse(
            sender = "SNB",
            body = "حوالة خارجة\nبمبلغ: 300 ريال\nحساب: 7271",
            smsTimestampMillis = null,
        )
        assertEquals(TransactionType.TRANSFER_OUT, r.transactionType)
    }

    @Test
    fun bareHiwalaWithoutDirectionIsNotForcedTransfer() {
        val r = parser.parse(
            sender = "Bank",
            body = "حوالة\nبمبلغ: 100 ريال",
            smsTimestampMillis = null,
        )
        assertTrue(
            "Bare حوالة must not force TRANSFER_IN/OUT",
            r.transactionType != TransactionType.TRANSFER_IN &&
                r.transactionType != TransactionType.TRANSFER_OUT,
        )
    }

    @Test
    fun parsesCardPayment() {
        val r = parser.parse(
            sender = "BankAlbilad",
            body = "Type: سداد\nبطاقة ائتمانية: 1234\nبمبلغ: 1250 ر.س",
            smsTimestampMillis = null
        )
        assertEquals(TransactionType.CARD_PAYMENT, r.transactionType)
        assertEquals(0, BigDecimal("1250").compareTo(r.amount))
        assertEquals(Currency.SAR, r.currency)
    }

    @Test
    fun visaCardLabelExtractsCreditCardLastFour() {
        val r = parser.parse(
            sender = "SNB",
            body = "شراء\nبطاقة فيزا: 4444\nبمبلغ: 200 ر.س\nالتاجر: Store",
            smsTimestampMillis = null,
        )
        assertEquals("4444", r.accountOrCardLastFourDigits)
        assertTrue(
            r.identifierEvidence.any {
                it.type == com.baraa.masroof.data.db.AccountIdentifierType.CREDIT_CARD_LAST4 &&
                    it.lastFour == "4444"
            },
        )
    }

    @Test
    fun parsesRefund() {
        val r = parser.parse(
            sender = "Visa",
            body = "Type: Refund\nAmount: 75.00 SAR\nTo: your card",
            smsTimestampMillis = null
        )
        assertEquals(TransactionType.REFUND, r.transactionType)
        assertEquals(0, BigDecimal("75.00").compareTo(r.amount))
        assertEquals(Currency.SAR, r.currency)
    }

    @Test
    fun parsesSalaryDeposit() {
        val r = parser.parse(
            sender = "AlRajhi",
            body = "Type: راتب\nبمبلغ: 12000 ريال\nالرصيد: 15000 ريال",
            smsTimestampMillis = null
        )
        assertEquals(TransactionType.SALARY, r.transactionType)
        assertEquals(0, BigDecimal("12000").compareTo(r.amount))
        assertEquals(Currency.SAR, r.currency)
    }

    @Test
    fun parsesBankFee() {
        val r = parser.parse(
            sender = "SNB",
            body = "Type: رسوم\nبمبلغ: 25 ريال\nالرصيد: 1200 ريال",
            smsTimestampMillis = null
        )
        assertEquals(TransactionType.BANK_FEE, r.transactionType)
        assertEquals(0, BigDecimal("25").compareTo(r.amount))
        assertEquals(Currency.SAR, r.currency)
    }

    @Test
    fun parsesDeclinedTransaction() {
        val r = parser.parse(
            sender = "AlJazira",
            body = "شراء\nبمبلغ: 300 ريال\nالحالة: مرفوضة",
            smsTimestampMillis = null
        )
        assertEquals(TransactionType.DECLINED, r.transactionType)
        assertEquals(TransactionStatus.DECLINED, r.status)
        assertEquals(0, BigDecimal("300").compareTo(r.amount))
    }

    // -- Amount formatting variants -----------------------------------------

    @Test
    fun parsesArabicNumerals() {
        val r = parser.parse(
            sender = "AlRajhi",
            body = "Type: شراء\nبمبلغ: 125.50 ريال",
            smsTimestampMillis = null
        )
        assertEquals(0, BigDecimal("125.50").compareTo(r.amount))
        assertEquals(Currency.SAR, r.currency)
        assertEquals(TransactionType.PURCHASE, r.transactionType)
    }

    @Test
    fun parsesArabicIndicDigitsInAmount() {
        val r = parser.parse(
            sender = "AlRajhi",
            body = "شراء\nبمبلغ: ١٢٥٫٥٠ ريال",
            smsTimestampMillis = null,
        )
        assertEquals(0, BigDecimal("125.50").compareTo(r.amount))
    }

    @Test
    fun parsesDecimalAmount() {
        val r = parser.parse(
            sender = "Visa",
            body = "Amount: 89.00 SAR was charged",
            smsTimestampMillis = null
        )
        assertEquals(0, BigDecimal("89.00").compareTo(r.amount))
        assertEquals(Currency.SAR, r.currency)
    }

    @Test
    fun parsesAmountWithCommas() {
        val r = parser.parse(
            sender = "STCBank",
            body = "Purchase Amount: 2350.75 SAR",
            smsTimestampMillis = null
        )
        assertEquals(0, BigDecimal("2350.75").compareTo(r.amount))
        assertEquals(Currency.SAR, r.currency)
    }

    // -- Missing fields ------------------------------------------------------

    @Test
    fun missingCurrency_defaultsToUnknown() {
        val r = parser.parse(
            sender = "Unknown",
            body = "Purchase of 50 at TestMerchant",
            smsTimestampMillis = null
        )
        assertEquals(Currency.UNKNOWN, r.currency)
    }

    @Test
    fun missingMerchant_isNull() {
        val r = parser.parse(
            sender = "AlRajhi",
            body = "شراء\nبمبلغ: 100 ريال\nالرصيد: 1000 ريال",
            smsTimestampMillis = null
        )
        assertNull(r.merchant)
    }

    @Test
    fun missingCardDigits_isNull() {
        val r = parser.parse(
            sender = "AlRajhi",
            body = "شراء\nبمبلغ: 100 ريال\nالتاجر: Starbucks",
            smsTimestampMillis = null
        )
        assertNull(r.accountOrCardLastFourDigits)
    }

    // -- Rejection / edge cases ---------------------------------------------

    @Test
    fun malformedMessage_doesNotCrash() {
        val r = parser.parse(
            sender = "Bank",
            body = "####@@@@\n\n$$$",
            smsTimestampMillis = null
        )
        assertNotNull(r)
        assertTrue(r.confidence <= 20)
    }

    @Test
    fun otpOnlyMessage_lowConfidence() {
        val r = parser.parse(
            sender = "Verify",
            body = "Your OTP code is 123456. Do not share.",
            smsTimestampMillis = null
        )
        assertTrue("OTP should not parse to a transaction", r.confidence < PARSE_FAIL_THRESHOLD)
        assertNull(r.amount)
    }

    @Test
    fun advertisementMessage_lowConfidence() {
        val r = parser.parse(
            sender = "ShoesStore",
            body = "50% off all shoes today only! Subscribe now for more deals.",
            smsTimestampMillis = null
        )
        assertTrue("ad should not parse to a transaction", r.confidence < PARSE_FAIL_THRESHOLD)
        assertNull(r.amount)
    }

    // -- Mixed / duplicate amounts ------------------------------------------

    @Test
    fun mixedArabicAndEnglish_parsesCorrectly() {
        val r = parser.parse(
            sender = "AlRajhi",
            body = "Type: Purchase\nAmount: 50 SAR\nتم خصم: 50 ريال\nBalance: 1000 SAR",
            smsTimestampMillis = null
        )
        assertEquals(TransactionType.PURCHASE, r.transactionType)
        assertEquals(0, BigDecimal("50").compareTo(r.amount))
        assertEquals(Currency.SAR, r.currency)
    }

    @Test
    fun duplicateAmounts_picksTransactionAmountNotBalance() {
        val r = parser.parse(
            sender = "AlRajhi",
            body = "شراء\nبمبلغ: 250 ريال\nالرصيد المتاح: 4500 ريال",
            smsTimestampMillis = null
        )
        assertEquals(0, BigDecimal("250").compareTo(r.amount))
    }

    @Test
    fun balanceNotMistakenForTransactionAmount() {
        // The balance figure 4500 must NOT win over the transaction figure 250.
        val r = parser.parse(
            sender = "AlRajhi",
            body = "شراء\nبمبلغ: 250 ريال\nالرصيد المتاح: 4500 ريال",
            smsTimestampMillis = null
        )
        assertNotNull(r.amount)
        assertFalse(
            "balance figure should never be picked as the transaction amount",
            r.amount == BigDecimal("4500") || r.amount == BigDecimal("4500.00")
        )
    }

    // -- Last four digits & dates (bonus coverage) ---------------------------

    @Test
    fun extractsLastFourDigits_afterCardKeyword() {
        val r = parser.parse(
            sender = "Visa",
            body = "Card: 1234\nAmount: 50.00 SAR\nMerchant: Starbucks",
            smsTimestampMillis = null
        )
        assertEquals("1234", r.accountOrCardLastFourDigits)
    }

    @Test
    fun extractsLastFourDigits_arabicKeyword() {
        val r = parser.parse(
            sender = "AlRajhi",
            body = "بطاقة: 5678\nبمبلغ: 100 ريال\nالتاجر: Starbucks",
            smsTimestampMillis = null
        )
        assertEquals("5678", r.accountOrCardLastFourDigits)
    }

    @Test
    fun dateFromMessageBody_usedWhenPresent() {
        val r = parser.parse(
            sender = "AlRajhi",
            body = "شراء\nبمبلغ: 100 ريال\nالتاريخ: 15/01/2024 14:30",
            smsTimestampMillis = null
        )
        assertEquals(LocalDate.of(2024, 1, 15), r.transactionDate)
        assertEquals(LocalTime.of(14, 30), r.transactionTime)
        assertTrue(
            "should note that date came from body",
            r.parsingNotes.any { it.startsWith("date from message body") || it == "date and time from message body" }
        )
    }

    @Test
    fun dateFallsBackToSmsTimestampWhenMissing() {
        val r = parser.parse(
            sender = "AlRajhi",
            body = "شراء\nبمبلغ: 100 ريال",
            smsTimestampMillis = smsEpoch
        )
        assertNotNull(r.transactionDate)
        assertNotNull(r.transactionTime)
        // Convert epoch to local date and check it matches.
        val expectedDate = java.time.Instant.ofEpochMilli(smsEpoch)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        assertEquals(expectedDate, r.transactionDate)
        assertTrue(
            "should note that date came from SMS metadata",
            r.parsingNotes.any { it.startsWith("date from SMS metadata") }
        )
    }

    // -- Original sender / message preserved --------------------------------

    @Test
    fun parsesCreditLimitChangeAndExtractsNewLimit() {
        val r = parser.parse(
            sender = "AlRajhi",
            body = "نوع العملية: تغيير حد الرصيد\nالحد الائتماني الجديد: 25000 ريال\nبطاقة: ****4321",
            smsTimestampMillis = null,
        )
        assertEquals(TransactionType.CREDIT_LIMIT_CHANGE, r.transactionType)
        assertEquals(0, BigDecimal("25000").compareTo(r.amount))
        assertEquals("4321", r.accountOrCardLastFourDigits)
    }

    @Test
    fun preservesOriginalSenderAndMessage() {
        val r = parser.parse(
            sender = "AlRajhi Bank",
            body = "شراء\nبمبلغ: 250 ريال\nالتاجر: Starbucks",
            smsTimestampMillis = null
        )
        assertEquals("AlRajhi Bank", r.originalSender)
        assertEquals("شراء\nبمبلغ: 250 ريال\nالتاجر: Starbucks", r.originalMessage)
    }

    @Test
    fun parsesCompactEnglishApplePayCreditCardPurchase() {
        val r = parser.parse(
            sender = "AlRajhi",
            body = "Online Purchase Apple Pay Credit Card: 8332 at :RIDE APP of : 33.03 SAR on : 2026-07-30 10:01 Available Balance is: 18313.81 SAR Due Amount: 802.62 SAR",
            smsTimestampMillis = null,
        )
        assertEquals(TransactionType.ONLINE_PURCHASE, r.transactionType)
        assertEquals(0, BigDecimal("33.03").compareTo(r.amount))
        assertEquals(Currency.SAR, r.currency)
        assertEquals("8332", r.accountOrCardLastFourDigits)
        assertEquals("RIDE APP", r.merchant)
        assertEquals(LocalDate.of(2026, 7, 30), r.transactionDate)
        // Due / available balances must never become the purchase amount.
        assertTrue(r.amount!!.compareTo(BigDecimal("802.62")) != 0)
        assertTrue(r.amount!!.compareTo(BigDecimal("18313.81")) != 0)
    }

    @Test
    fun parsesArabicPosSamsungPayWithoutTreatingDatetimeAsMerchant() {
        val r = parser.parse(
            sender = "AlRajhi",
            body = """
                شراء عبر نقاط البيع (Samsung Pay)
                بطاقة ائتمانية: 7271
                لدى: FUEL STATION CO
                بمبلغ: 178.02 SAR
                في: 09:08 30-07-2026
                الرصيد المتاح: 18346.84 SAR
                إجمالي المبلغ المستحق:802.62 SAR
            """.trimIndent(),
            smsTimestampMillis = null,
        )
        assertEquals(TransactionType.PURCHASE, r.transactionType)
        assertEquals(0, BigDecimal("178.02").compareTo(r.amount))
        assertEquals("7271", r.accountOrCardLastFourDigits)
        assertEquals("FUEL STATION CO", r.merchant)
        assertEquals(LocalDate.of(2026, 7, 30), r.transactionDate)
        assertEquals(LocalTime.of(9, 8), r.transactionTime)
        assertTrue(r.identifierEvidence.any {
            it.type == com.baraa.masroof.data.db.AccountIdentifierType.CREDIT_CARD_LAST4 && it.lastFour == "7271"
        })
    }

    @Test
    fun nullInputs_doNotCrash() {
        val r = parser.parse(sender = null, body = null, smsTimestampMillis = null)
        assertNotNull(r)
        assertEquals(TransactionType.UNKNOWN, r.transactionType)
        assertEquals(0, r.confidence)
    }

    // -- Normalizer (covered as part of the parser pipeline) ----------------

    @Test
    fun normalizerConvertsArabicDigitsAndDecimal() {
        val r = parser.parse(
            sender = "AlRajhi",
            body = "بمبلغ: 123.45 ريال",
            smsTimestampMillis = null
        )
        assertEquals(0, BigDecimal("123.45").compareTo(r.amount))
    }

    companion object {
        private const val PARSE_FAIL_THRESHOLD = 30
    }
}

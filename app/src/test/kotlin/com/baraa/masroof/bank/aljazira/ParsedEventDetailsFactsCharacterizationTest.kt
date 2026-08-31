package com.baraa.masroof.bank.aljazira

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.parsing.model.CardSmsChannel
import com.baraa.masroof.parsing.model.ParseResult
import com.baraa.masroof.parsing.model.SmsParseInput
import com.baraa.masroof.parsing.normalizer.MessageNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Locks parse-time dashboard facts on [com.baraa.masroof.parsing.model.ParsedEventDetails].
 */
class ParsedEventDetailsFactsCharacterizationTest {
    private val parser = AlJaziraMessageParser()
    private val normalizer = MessageNormalizer()

    @Test
    fun creditPurchase_populatesCreditChannelAndBalances() {
        val body = """
            شراء عبر نقاط البيع (Google Pay)
            بطاقة ائتمانية: 7271
            لدى: Ramadan Gifts
            بمبلغ: 75.00 SAR
            الرصيد المتاح: 14569.09 SAR
            إجمالي المبلغ المستحق:3921.11 SAR
        """.trimIndent()
        val details = parseDetails(body)
        assertEquals(CardSmsChannel.CREDIT, details.cardSmsChannel)
        assertNotNull(details.availableBalance)
        assertNotNull(details.outstandingBalance)
    }

    @Test
    fun madaPurchase_populatesDebitChannel() {
        val body = """
            شراء عبر نقاط البيع (Google Pay)
            بطاقة مدى: 8219
            بمبلغ: 127.00 SAR
        """.trimIndent()
        val details = parseDetails(body)
        assertEquals(CardSmsChannel.DEBIT, details.cardSmsChannel)
    }

    @Test
    fun internationalPurchase_populatesExchangeRateAndFee() {
        val body = """
            بمبلغ: USD 23.00
            رسوم العمليات الدولية: 1.99
            سعر الصرف: 3.756957
        """.trimIndent()
        val details = parseDetails(body)
        assertNotNull(details.exchangeRate)
        assertNotNull(details.internationalFee)
        assertEquals(Currency.USD, details.labeledForeignAmount?.currency)
    }

    @Test
    fun statementSms_populatesStatementChannelAndDueDate() {
        val body = """
            إصدار كشف حساب
            المبلغ المستحق: 3921.11 SAR
            تاريخ الاستحقاق: 15/08/2026
        """.trimIndent()
        val details = parseDetails(body)
        assertEquals(CardSmsChannel.STATEMENT, details.cardSmsChannel)
        assertEquals(java.time.LocalDate.of(2026, 8, 15), details.paymentDueDate)
    }

    private fun parseDetails(body: String) =
        when (
            val result = parser.parse(
                SmsParseInput(
                    rawSmsId = "sms-1",
                    sender = "AlJazira",
                    body = body,
                    receivedAt = Instant.parse("2026-08-03T10:24:00Z"),
                ),
                normalizer.normalize(body),
            )
        ) {
            is ParseResult.Success -> result.details
            is ParseResult.Partial -> result.details
            is ParseResult.ReviewRequired -> result.details
            is ParseResult.NonFinancial -> result.details
            else -> error("Unexpected parse result: $result")
        }.also { details ->
            assertTrue(details.cardSmsChannel != null || details.exchangeRate != null || details.paymentDueDate != null)
        }
}

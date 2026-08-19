package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.ParseStatus
import com.baraa.masroof.domain.model.ParsedEvent
import com.baraa.masroof.domain.model.RawSms
import com.baraa.masroof.parsing.repository.ParsedEventRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.Instant

class TransactionSarEquivalentResolverTest {
    @Test
    fun usdRefund_usesHistoricalRateFromPriorPurchase() {
        val purchaseBody = """
            شراء عبر الانترنت
            بطاقة ائتمانية: 7271
            لدى: CURSOR, AI POWERED IDE
            بمبلغ: USD 23.00
            في: 2026-08-06 20:22
            رسوم العمليات الدولية: 1.99
            سعر الصرف: 3.756957
        """.trimIndent()
        val refundBody = """
            بطاقة إئتمانية: إسترداد مبلغ
            بطاقة: Credit
            رقم: 7271
            من: CURSOR, AI POWERED IDE
            مبلغ: 6.51 USD
            في: 18:23 17-08-2026
        """.trimIndent()

        val purchaseEvent = parsedEvent(
            id = "pe-purchase",
            rawSmsId = "sms-purchase",
            family = MessageFamily.PURCHASE,
            merchant = "CURSOR, AI POWERED IDE",
        )
        val refundEvent = parsedEvent(
            id = "pe-refund",
            rawSmsId = "sms-refund",
            family = MessageFamily.REFUND,
            merchant = "CURSOR, AI POWERED IDE",
        )
        val refundTx = FinancialTransaction(
            id = "tx-refund",
            type = FinancialTransactionType.REFUND,
            amount = Money.of("6.51", Currency.USD),
            occurredAt = Instant.parse("2026-08-17T15:23:00Z"),
            sourceContainerId = null,
            destinationContainerId = "card:bank_aljazira:7271",
            merchant = null,
            counterparty = null,
            categoryId = null,
            linkedParsedEventIds = listOf("pe-refund"),
        )

        val sar = TransactionSarEquivalentResolver.resolve(
            transactions = listOf(refundTx),
            parsedRecords = listOf(
                ParsedEventRecord(purchaseEvent, com.baraa.masroof.parsing.model.ParsedEventDetails()),
                ParsedEventRecord(refundEvent, com.baraa.masroof.parsing.model.ParsedEventDetails()),
            ),
            rawSmsById = mapOf(
                "sms-purchase" to raw("sms-purchase", purchaseBody),
                "sms-refund" to raw("sms-refund", refundBody),
            ),
        )["tx-refund"]

        assertNotNull(sar)
        assertEquals(Money.of("24.46", Currency.SAR), sar)
    }

    private fun parsedEvent(
        id: String,
        rawSmsId: String,
        family: MessageFamily,
        merchant: String,
    ) = ParsedEvent(
        id = id,
        rawSmsId = rawSmsId,
        bank = Bank.BANK_ALJAZIRA,
        messageFamily = family,
        direction = null,
        amount = null,
        purchaseChannel = null,
        sourceAccountRef = null,
        destinationAccountRef = null,
        cardRef = null,
        merchant = merchant,
        counterparty = null,
        occurredAt = null,
        bankNetworkType = null,
        confidence = com.baraa.masroof.domain.model.Confidence(1.0, emptyList()),
        parseStatus = ParseStatus.SUCCESS,
    )

    private fun raw(id: String, body: String) = RawSms(
        id = id,
        sender = "AlJazira",
        body = body,
        receivedAt = Instant.parse("2026-08-17T15:23:00Z"),
        deviceMessageId = id,
        bodyHash = "hash-$id",
    )
}

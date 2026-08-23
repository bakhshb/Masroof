package com.baraa.masroof.application.dashboard

import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.CardReference
import com.baraa.masroof.domain.model.Confidence
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.MoneyDirection
import com.baraa.masroof.domain.model.ParseStatus
import com.baraa.masroof.domain.model.ParsedEvent
import com.baraa.masroof.domain.model.RawSms
import com.baraa.masroof.parsing.model.ParsedEventDetails
import com.baraa.masroof.parsing.repository.ParsedEventRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class DebitLinkedAccountInferrerTest {
    @Test
    fun inferAccountLast4_fromPurchaseSms() {
        val parsedRecords = listOf(
            parsedRecord(
                cardLast4 = "2210",
                body = "شراء من نقاط البيع\nبطاقة مدى: 2210\nخصمت من حساب: 3001",
            ),
        )
        val rawSmsById = rawSmsMap(parsedRecords)

        assertEquals(
            "3001",
            DebitLinkedAccountInferrer.inferAccountLast4(
                bank = Bank.BANK_ALJAZIRA,
                cardLast4 = "2210",
                parsedRecords = parsedRecords,
                rawSmsById = rawSmsById,
            ),
        )
    }

    @Test
    fun inferAccountLast4_nullWhenSmsOmitsAccount() {
        val parsedRecords = listOf(
            parsedRecord(
                cardLast4 = "8219",
                body = "شراء عبر نقاط البيع (Google Pay)\nبطاقة مدى: 8219",
            ),
        )
        val rawSmsById = rawSmsMap(parsedRecords)

        assertNull(
            DebitLinkedAccountInferrer.inferAccountLast4(
                bank = Bank.BANK_ALJAZIRA,
                cardLast4 = "8219",
                parsedRecords = parsedRecords,
                rawSmsById = rawSmsById,
            ),
        )
    }

    private fun parsedRecord(cardLast4: String, body: String): ParsedEventRecord {
        val event = ParsedEvent(
            id = "evt-$cardLast4",
            rawSmsId = "sms-$cardLast4",
            bank = Bank.BANK_ALJAZIRA,
            messageFamily = MessageFamily.PURCHASE,
            direction = MoneyDirection.OUTGOING,
            amount = null,
            purchaseChannel = null,
            cardRef = CardReference(Bank.BANK_ALJAZIRA, cardLast4),
            sourceAccountRef = null,
            destinationAccountRef = null,
            merchant = null,
            counterparty = body,
            occurredAt = Instant.parse("2026-08-03T10:24:00Z"),
            bankNetworkType = null,
            confidence = Confidence(1.0),
            parseStatus = ParseStatus.SUCCESS,
        )
        return ParsedEventRecord(event = event, details = ParsedEventDetails())
    }

    private fun rawSmsMap(parsedRecords: List<ParsedEventRecord>): Map<String, RawSms> =
        parsedRecords.associate { record ->
            record.event.rawSmsId to RawSms(
                id = record.event.rawSmsId,
                sender = "AlJazira",
                body = record.event.counterparty.orEmpty(),
                receivedAt = Instant.parse("2026-08-03T10:24:00Z"),
                deviceMessageId = record.event.id,
                bodyHash = record.event.id,
            )
        }
}

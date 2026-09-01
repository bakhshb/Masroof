package com.baraa.masroof.application.dashboard

import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.CardReference
import com.baraa.masroof.domain.model.Confidence
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.MoneyDirection
import com.baraa.masroof.domain.model.ParseStatus
import com.baraa.masroof.domain.model.ParsedEvent
import com.baraa.masroof.parsing.model.ParsedEventDetails
import com.baraa.masroof.parsing.repository.ParsedEventRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class DebitLinkedAccountInferrerTest {
    @Test
    fun inferAccountLast4_fromPersistedFact() {
        val parsedRecords = listOf(
            parsedRecord(
                cardLast4 = "2210",
                debitSourceAccountLast4 = "3001",
            ),
        )

        assertEquals(
            "3001",
            DebitLinkedAccountInferrer.inferAccountLast4(
                bank = Bank.BANK_ALJAZIRA,
                cardLast4 = "2210",
                parsedRecords = parsedRecords,
            ),
        )
    }

    @Test
    fun inferAccountLast4_nullWhenFactMissing() {
        val parsedRecords = listOf(
            parsedRecord(
                cardLast4 = "8219",
                debitSourceAccountLast4 = null,
            ),
        )

        assertNull(
            DebitLinkedAccountInferrer.inferAccountLast4(
                bank = Bank.BANK_ALJAZIRA,
                cardLast4 = "8219",
                parsedRecords = parsedRecords,
            ),
        )
    }

    private fun parsedRecord(
        cardLast4: String,
        debitSourceAccountLast4: String?,
    ): ParsedEventRecord {
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
            counterparty = null,
            occurredAt = Instant.parse("2026-08-03T10:24:00Z"),
            bankNetworkType = null,
            confidence = Confidence(1.0),
            parseStatus = ParseStatus.SUCCESS,
        )
        return ParsedEventRecord(
            event = event,
            details = ParsedEventDetails(debitSourceAccountLast4 = debitSourceAccountLast4),
        )
    }
}

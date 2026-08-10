package com.baraa.masroof.domain.model

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DomainModelInvariantTest {

    @Test
    fun confidence_rejectsScoreOutsideUnitInterval() {
        assertThrows(IllegalArgumentException::class.java) {
            Confidence(score = -0.1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Confidence(score = 1.1)
        }
        Confidence(score = 0.0)
        Confidence(score = 1.0, reasons = listOf("exact match"))
    }

    @Test
    fun parsedEvent_canExpressIntraBankIncomingWithoutSelfTransfer() {
        // Wife AlJazira → user AlJazira: parse output only. Ownership/self-transfer
        // classification belongs to later domain rules, not ParsedEvent.
        val event = ParsedEvent(
            id = "evt-1",
            rawSmsId = "sms-1",
            bank = Bank.BANK_ALJAZIRA,
            messageFamily = MessageFamily.TRANSFER_IN,
            direction = MoneyDirection.INCOMING,
            amount = Money.of("500.00", Currency.SAR),
            purchaseChannel = null,
            sourceAccountRef = AccountReference(Bank.BANK_ALJAZIRA, "9999"),
            destinationAccountRef = AccountReference(Bank.BANK_ALJAZIRA, "3001"),
            cardRef = null,
            merchant = null,
            counterparty = "Wife Name",
            occurredAt = Instant.parse("2026-08-03T10:38:00Z"),
            bankNetworkType = BankNetworkType.INTRA_BANK,
            confidence = Confidence(0.9, listOf("incoming transfer labels")),
            parseStatus = ParseStatus.SUCCESS,
        )

        assertEquals(MessageFamily.TRANSFER_IN, event.messageFamily)
        assertEquals(BankNetworkType.INTRA_BANK, event.bankNetworkType)
        assertNull(event.purchaseChannel)
        // ParsedEvent has no FinancialTransactionType / TransferOwnershipType field.
        assertTrue(TransferOwnershipType.EXTERNAL_INCOMING != TransferOwnershipType.SELF_TRANSFER)
    }

    @Test
    fun financialTransaction_canExpressExternalIncomingAndSelfTransferSeparately() {
        val externalIn = FinancialTransaction(
            id = "tx-ext",
            type = FinancialTransactionType.EXTERNAL_TRANSFER_IN,
            amount = Money.of("500.00", Currency.SAR),
            occurredAt = Instant.parse("2026-08-03T10:38:00Z"),
            sourceContainerId = null,
            destinationContainerId = "acct-3001",
            merchant = null,
            counterparty = "Wife Name",
            categoryId = null,
            linkedParsedEventIds = listOf("evt-1"),
            status = TransactionStatus.CONFIRMED,
        )
        val selfTransfer = FinancialTransaction(
            id = "tx-self",
            type = FinancialTransactionType.SELF_TRANSFER,
            amount = Money.of("100.00", Currency.SAR),
            occurredAt = Instant.parse("2026-08-03T11:00:00Z"),
            sourceContainerId = "acct-3001",
            destinationContainerId = "acct-3002",
            merchant = null,
            counterparty = null,
            categoryId = null,
            linkedParsedEventIds = listOf("evt-a", "evt-b"),
            status = TransactionStatus.CONFIRMED,
        )

        assertEquals(FinancialTransactionType.EXTERNAL_TRANSFER_IN, externalIn.type)
        assertEquals(FinancialTransactionType.SELF_TRANSFER, selfTransfer.type)
        assertEquals(2, selfTransfer.linkedParsedEventIds.size)
    }

    @Test
    fun purchase_usesChannelNotSeparateFamily() {
        val event = ParsedEvent(
            id = "evt-pos",
            rawSmsId = "sms-pos",
            bank = Bank.BANK_ALJAZIRA,
            messageFamily = MessageFamily.PURCHASE,
            direction = MoneyDirection.OUTGOING,
            amount = Money.of("51.99", Currency.SAR),
            purchaseChannel = PurchaseChannel.POS,
            sourceAccountRef = null,
            destinationAccountRef = null,
            cardRef = CardReference(Bank.BANK_ALJAZIRA, "7271"),
            merchant = "Keeta",
            counterparty = null,
            occurredAt = Instant.parse("2026-08-03T14:32:00Z"),
            bankNetworkType = null,
            confidence = Confidence(0.95),
            parseStatus = ParseStatus.SUCCESS,
        )

        assertEquals(MessageFamily.PURCHASE, event.messageFamily)
        assertEquals(PurchaseChannel.POS, event.purchaseChannel)
    }

    @Test
    fun accountAndCard_areFinancialContainersWithIndependentOwnership() {
        val owned: FinancialContainer = Account(
            id = "acct-1",
            bank = Bank.BANK_ALJAZIRA,
            maskedNumber = "3001",
            displayName = "Main",
            ownership = OwnershipStatus.OWNED,
            type = AccountType.CURRENT,
        )
        val external: FinancialContainer = Account(
            id = "acct-wife",
            bank = Bank.BANK_ALJAZIRA,
            maskedNumber = "8888",
            displayName = null,
            ownership = OwnershipStatus.EXTERNAL,
            type = AccountType.CURRENT,
        )
        val card: FinancialContainer = Card(
            id = "card-1",
            bank = Bank.BANK_ALJAZIRA,
            last4 = "7271",
            displayName = "CC",
            ownership = OwnershipStatus.OWNED,
            type = CardType.CREDIT,
            linkedAccountId = "acct-1",
        )

        assertEquals(OwnershipStatus.OWNED, owned.ownership)
        assertEquals(OwnershipStatus.EXTERNAL, external.ownership)
        assertEquals(Bank.BANK_ALJAZIRA, card.bank)
        assertTrue(owned is Account)
        assertTrue(card is Card)
    }

    @Test
    fun userCorrection_doesNotRequireMutatingRawSms() {
        val raw = RawSms(
            id = "sms-1",
            sender = "AlJazira",
            body = "original body",
            receivedAt = Instant.parse("2026-08-03T10:00:00Z"),
            deviceMessageId = "device-1",
            bodyHash = "hash-1",
        )
        val correction = UserCorrection(
            id = "corr-1",
            targetEventId = "evt-1",
            correctedType = MessageFamily.REFUND,
            correctedAmount = Money.of("10.00", Currency.SAR),
            correctedMerchant = "Store",
            correctedOwnership = null,
            correctedCounterparty = null,
            createdAt = Instant.parse("2026-08-04T10:00:00Z"),
        )

        assertEquals("original body", raw.body)
        assertEquals(MessageFamily.REFUND, correction.correctedType)
        assertEquals("sms-1", raw.id)
    }
}

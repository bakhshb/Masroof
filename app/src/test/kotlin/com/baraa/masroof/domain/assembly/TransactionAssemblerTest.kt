package com.baraa.masroof.domain.assembly

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.ids.FinancialContainerIdFactory
import com.baraa.masroof.domain.matching.TransferMatchCandidate
import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.CardReference
import com.baraa.masroof.domain.model.Confidence
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.MoneyDirection
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.model.ParseStatus
import com.baraa.masroof.domain.model.ParsedEvent
import com.baraa.masroof.domain.model.PurchaseChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.math.BigDecimal
import java.time.Instant

class TransactionAssemblerTest {

    private val receivedAt = Instant.parse("2026-08-01T12:00:00Z")

    @Test
    fun assemblerSource_doesNotFabricateAccountOrCardTypes() {
        val source = File(
            "src/main/kotlin/com/baraa/masroof/domain/assembly/TransactionAssembler.kt",
        ).readText()
        val imports = source.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("import ") }
            .toSet()
        assertFalse(imports.contains("import com.baraa.masroof.domain.model.AccountType"))
        assertFalse(imports.contains("import com.baraa.masroof.domain.model.CardType"))
        assertFalse(imports.contains("import com.baraa.masroof.domain.model.Account"))
        assertFalse(imports.contains("import com.baraa.masroof.domain.model.Card"))
        assertFalse(source.contains("AccountType."))
        assertFalse(source.contains("CardType."))
        assertFalse(Regex("""\bAccount\s*\(""").containsMatchIn(source))
        assertFalse(Regex("""\bCard\s*\(""").containsMatchIn(source))
    }

    @Test
    fun purchase_knownCard_expenseWithCardSourceId() {
        val outcome = TransactionAssembler.assembleSingle(
            event = event(
                family = MessageFamily.PURCHASE,
                amount = money("51.99"),
                card = CardReference(Bank.BANK_ALJAZIRA, "7271"),
                channel = PurchaseChannel.ONLINE,
            ),
            receivedAt = receivedAt,
            sourceOwnership = OwnershipStatus.UNKNOWN,
            destinationOwnership = OwnershipStatus.UNKNOWN,
            cardOwnership = OwnershipStatus.OWNED,
        ) as TransactionAssembler.Outcome.Assembled

        assertEquals(FinancialTransactionType.EXPENSE, outcome.transaction.type)
        assertEquals(
            FinancialContainerIdFactory.cardId(Bank.BANK_ALJAZIRA, "7271"),
            outcome.transaction.sourceContainerId,
        )
        assertNull(outcome.transaction.destinationContainerId)
    }

    @Test
    fun purchase_madaDebitWithAccount_usesAccountSourceId() {
        val outcome = TransactionAssembler.assembleSingle(
            event = event(
                family = MessageFamily.PURCHASE,
                amount = money("120.00"),
                source = AccountReference(Bank.BANK_ALJAZIRA, "3001"),
                card = CardReference(Bank.BANK_ALJAZIRA, "2210"),
                channel = PurchaseChannel.POS,
            ),
            receivedAt = receivedAt,
            sourceOwnership = OwnershipStatus.OWNED,
            destinationOwnership = OwnershipStatus.UNKNOWN,
            cardOwnership = OwnershipStatus.OWNED,
        ) as TransactionAssembler.Outcome.Assembled

        assertEquals(FinancialTransactionType.EXPENSE, outcome.transaction.type)
        assertEquals(
            FinancialContainerIdFactory.accountId(Bank.BANK_ALJAZIRA, "3001"),
            outcome.transaction.sourceContainerId,
        )
        assertNull(outcome.transaction.destinationContainerId)
    }

    @Test
    fun cardPayment_creditCardPayment_withoutInventingCardType() {
        val outcome = TransactionAssembler.assembleSingle(
            event = event(
                family = MessageFamily.CARD_PAYMENT,
                amount = money("200.00"),
                source = AccountReference(Bank.BANK_ALJAZIRA, "3001"),
                card = CardReference(Bank.BANK_ALJAZIRA, "7271"),
            ),
            receivedAt = receivedAt,
            sourceOwnership = OwnershipStatus.OWNED,
            destinationOwnership = OwnershipStatus.UNKNOWN,
            cardOwnership = OwnershipStatus.OWNED,
        ) as TransactionAssembler.Outcome.Assembled

        assertEquals(FinancialTransactionType.CREDIT_CARD_PAYMENT, outcome.transaction.type)
        assertEquals(
            FinancialContainerIdFactory.accountId(Bank.BANK_ALJAZIRA, "3001"),
            outcome.transaction.sourceContainerId,
        )
        assertEquals(
            FinancialContainerIdFactory.cardId(Bank.BANK_ALJAZIRA, "7271"),
            outcome.transaction.destinationContainerId,
        )
    }

    @Test
    fun refundToAccount_usesDestinationContainerId() {
        val outcome = TransactionAssembler.assembleSingle(
            event = event(
                family = MessageFamily.REFUND,
                amount = money("10.00"),
                destination = AccountReference(Bank.BANK_ALJAZIRA, "3001"),
            ),
            receivedAt = receivedAt,
            sourceOwnership = OwnershipStatus.UNKNOWN,
            destinationOwnership = OwnershipStatus.OWNED,
            cardOwnership = OwnershipStatus.UNKNOWN,
        ) as TransactionAssembler.Outcome.Assembled

        assertEquals(FinancialTransactionType.REFUND, outcome.transaction.type)
        assertNull(outcome.transaction.sourceContainerId)
        assertEquals(
            FinancialContainerIdFactory.accountId(Bank.BANK_ALJAZIRA, "3001"),
            outcome.transaction.destinationContainerId,
        )
    }

    @Test
    fun cardOnlyRefund_usesDestinationContainerId() {
        val outcome = TransactionAssembler.assembleSingle(
            event = event(
                family = MessageFamily.REFUND,
                amount = money("10.00"),
                card = CardReference(Bank.BANK_ALJAZIRA, "7271"),
            ),
            receivedAt = receivedAt,
            sourceOwnership = OwnershipStatus.UNKNOWN,
            destinationOwnership = OwnershipStatus.UNKNOWN,
            cardOwnership = OwnershipStatus.OWNED,
        ) as TransactionAssembler.Outcome.Assembled

        assertEquals(FinancialTransactionType.REFUND, outcome.transaction.type)
        assertNull(outcome.transaction.sourceContainerId)
        assertEquals(
            FinancialContainerIdFactory.cardId(Bank.BANK_ALJAZIRA, "7271"),
            outcome.transaction.destinationContainerId,
        )
    }

    @Test
    fun refund_neverPutsReceivingContainerInSource() {
        val withAccount = TransactionAssembler.assembleSingle(
            event = event(
                family = MessageFamily.REFUND,
                amount = money("10.00"),
                destination = AccountReference(Bank.BANK_ALJAZIRA, "3001"),
                card = CardReference(Bank.BANK_ALJAZIRA, "7271"),
            ),
            receivedAt = receivedAt,
            sourceOwnership = OwnershipStatus.UNKNOWN,
            destinationOwnership = OwnershipStatus.OWNED,
            cardOwnership = OwnershipStatus.OWNED,
        ) as TransactionAssembler.Outcome.Assembled

        assertNull(withAccount.transaction.sourceContainerId)
        assertEquals(
            FinancialContainerIdFactory.accountId(Bank.BANK_ALJAZIRA, "3001"),
            withAccount.transaction.destinationContainerId,
        )
        assertTrue(withAccount.transaction.type == FinancialTransactionType.REFUND)
        assertTrue(withAccount.transaction.type != FinancialTransactionType.INCOME)
    }

    @Test
    fun unmatchedOutgoing_ownedSource_unknownDest_externalOut() {
        val outcome = TransactionAssembler.assembleUnmatchedOwnedTransfer(
            candidate = transferCandidate(
                event = event(
                    family = MessageFamily.TRANSFER_OUT,
                    amount = money("500.00"),
                    source = AccountReference(Bank.BANK_ALJAZIRA, "3001"),
                    destination = AccountReference(Bank.UNKNOWN, "0593"),
                ),
                sourceOwnership = OwnershipStatus.OWNED,
                destinationOwnership = OwnershipStatus.UNKNOWN,
            ),
        ) as TransactionAssembler.Outcome.Assembled

        assertEquals(FinancialTransactionType.EXTERNAL_TRANSFER_OUT, outcome.transaction.type)
        assertEquals(
            FinancialContainerIdFactory.accountId(Bank.BANK_ALJAZIRA, "3001"),
            outcome.transaction.sourceContainerId,
        )
        assertNull(outcome.transaction.destinationContainerId)
        assertFalse(outcome.transaction.type == FinancialTransactionType.EXPENSE)
    }

    @Test
    fun unmatchedIncoming_ownedDest_unknownSource_externalIn() {
        val outcome = TransactionAssembler.assembleUnmatchedOwnedTransfer(
            candidate = transferCandidate(
                event = event(
                    family = MessageFamily.TRANSFER_IN,
                    amount = money("200.00"),
                    source = AccountReference(Bank.UNKNOWN, "9999"),
                    destination = AccountReference(Bank.BANK_ALJAZIRA, "3001"),
                ),
                sourceOwnership = OwnershipStatus.UNKNOWN,
                destinationOwnership = OwnershipStatus.OWNED,
            ),
        ) as TransactionAssembler.Outcome.Assembled

        assertEquals(FinancialTransactionType.EXTERNAL_TRANSFER_IN, outcome.transaction.type)
        assertEquals(
            FinancialContainerIdFactory.accountId(Bank.BANK_ALJAZIRA, "3001"),
            outcome.transaction.destinationContainerId,
        )
        assertNull(outcome.transaction.sourceContainerId)
        assertFalse(outcome.transaction.type == FinancialTransactionType.INCOME)
    }

    @Test
    fun unmatchedOutgoing_unownedSource_staysPending() {
        val outcome = TransactionAssembler.assembleUnmatchedOwnedTransfer(
            candidate = transferCandidate(
                event = event(
                    family = MessageFamily.TRANSFER_OUT,
                    amount = money("500.00"),
                    source = AccountReference(Bank.BANK_ALJAZIRA, "3001"),
                    destination = AccountReference(Bank.UNKNOWN, "0593"),
                ),
                sourceOwnership = OwnershipStatus.UNKNOWN,
                destinationOwnership = OwnershipStatus.UNKNOWN,
            ),
        )
        assertTrue(outcome is TransactionAssembler.Outcome.PendingMatch)
    }

    @Test
    fun bankUnknown_referenceDoesNotPersistDurableContainerId() {
        val outcome = TransactionAssembler.assembleSingle(
            event = event(
                family = MessageFamily.FEE,
                amount = money("1.00"),
                source = AccountReference(Bank.UNKNOWN, "6810"),
            ),
            receivedAt = receivedAt,
            sourceOwnership = OwnershipStatus.UNKNOWN,
            destinationOwnership = OwnershipStatus.UNKNOWN,
            cardOwnership = OwnershipStatus.UNKNOWN,
        ) as TransactionAssembler.Outcome.Assembled

        assertNull(outcome.transaction.sourceContainerId)
        assertNull(outcome.transaction.destinationContainerId)
        assertFalse(
            listOfNotNull(
                outcome.transaction.sourceContainerId,
                outcome.transaction.destinationContainerId,
            ).any { it.contains("UNKNOWN") },
        )
    }

    private fun money(v: String) = Money.of(BigDecimal(v), Currency.SAR)

    private fun transferCandidate(
        event: ParsedEvent,
        sourceOwnership: OwnershipStatus,
        destinationOwnership: OwnershipStatus,
    ) = TransferMatchCandidate(
        event = event,
        transactionReference = null,
        occurredAtLocal = null,
        receivedAt = receivedAt,
        sourceOwnership = sourceOwnership,
        destinationOwnership = destinationOwnership,
    )

    private fun event(
        family: MessageFamily,
        amount: Money?,
        source: AccountReference? = null,
        destination: AccountReference? = null,
        card: CardReference? = null,
        channel: PurchaseChannel? = null,
    ) = ParsedEvent(
        id = "pe-1",
        rawSmsId = "sms-1",
        bank = Bank.BANK_ALJAZIRA,
        messageFamily = family,
        direction = MoneyDirection.OUTGOING,
        amount = amount,
        purchaseChannel = channel,
        sourceAccountRef = source,
        destinationAccountRef = destination,
        cardRef = card,
        merchant = null,
        counterparty = null,
        occurredAt = receivedAt,
        bankNetworkType = null,
        confidence = Confidence(1.0),
        parseStatus = ParseStatus.SUCCESS,
    )
}

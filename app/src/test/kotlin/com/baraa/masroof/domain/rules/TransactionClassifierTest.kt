package com.baraa.masroof.domain.rules

import com.baraa.masroof.domain.model.Account
import com.baraa.masroof.domain.model.AccountType
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.BankNetworkType
import com.baraa.masroof.domain.model.Card
import com.baraa.masroof.domain.model.CardType
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.model.PurchaseChannel
import com.baraa.masroof.domain.model.TransferOwnershipType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionClassifierTest {

    private val aljazira = Bank.BANK_ALJAZIRA
    private val d360 = Bank("D360")

    private val owned3001 = Account(
        id = "acct-3001",
        bank = aljazira,
        maskedNumber = "3001",
        displayName = "Main",
        ownership = OwnershipStatus.OWNED,
        type = AccountType.CURRENT,
    )
    private val owned3002 = Account(
        id = "acct-3002",
        bank = aljazira,
        maskedNumber = "3002",
        displayName = "Savings",
        ownership = OwnershipStatus.OWNED,
        type = AccountType.SAVINGS,
    )
    private val ownedD360 = Account(
        id = "acct-d360",
        bank = d360,
        maskedNumber = "1111",
        displayName = "D360",
        ownership = OwnershipStatus.OWNED,
        type = AccountType.CURRENT,
    )
    private val wifeExternal = Account(
        id = "acct-wife",
        bank = aljazira,
        maskedNumber = "8888",
        displayName = null,
        ownership = OwnershipStatus.EXTERNAL,
        type = AccountType.CURRENT,
    )
    private val externalBeneficiary = Account(
        id = "acct-other",
        bank = aljazira,
        maskedNumber = "7777",
        displayName = null,
        ownership = OwnershipStatus.EXTERNAL,
        type = AccountType.CURRENT,
    )
    private val ownedCreditCard = Card(
        id = "card-7271",
        bank = aljazira,
        last4 = "7271",
        displayName = "CC",
        ownership = OwnershipStatus.OWNED,
        type = CardType.CREDIT,
        linkedAccountId = "acct-3001",
    )
    private val ownedDebitCard = Card(
        id = "card-debit",
        bank = aljazira,
        last4 = "1234",
        displayName = "Debit",
        ownership = OwnershipStatus.OWNED,
        type = CardType.DEBIT,
        linkedAccountId = "acct-3001",
    )
    private val unknownAccount = Account(
        id = "acct-unknown",
        bank = aljazira,
        maskedNumber = "0000",
        displayName = null,
        ownership = OwnershipStatus.UNKNOWN,
        type = AccountType.CURRENT,
    )

    @Test
    fun ownedToOwned_sameBank_isSelfTransfer() {
        val result = TransactionClassifier.classify(
            ClassificationContext(
                messageFamily = MessageFamily.TRANSFER_OUT,
                source = owned3001,
                destination = owned3002,
                bankNetworkType = BankNetworkType.INTRA_BANK,
            ),
        )

        val classified = result as ClassificationResult.Classified
        assertEquals(FinancialTransactionType.SELF_TRANSFER, classified.transactionType)
        assertEquals(TransferOwnershipType.SELF_TRANSFER, classified.transferOwnership)
        assertFalse(classified.impact.countsAsExpense)
        assertFalse(classified.impact.countsAsIncome)
        assertEquals(NetWorthEffect.ZERO, classified.impact.netWorthEffect)
    }

    @Test
    fun ownedToOwned_crossBank_isSelfTransfer() {
        val result = TransactionClassifier.classify(
            ClassificationContext(
                messageFamily = MessageFamily.TRANSFER_OUT,
                source = owned3001,
                destination = ownedD360,
                bankNetworkType = BankNetworkType.INTER_BANK,
            ),
        )

        val classified = result as ClassificationResult.Classified
        assertEquals(FinancialTransactionType.SELF_TRANSFER, classified.transactionType)
        assertFalse(classified.impact.countsAsExpense)
        assertFalse(classified.impact.countsAsIncome)
        assertEquals(NetWorthEffect.ZERO, classified.impact.netWorthEffect)
    }

    @Test
    fun externalToOwned_intraBank_isNotSelfTransfer() {
        val result = TransactionClassifier.classify(
            ClassificationContext(
                messageFamily = MessageFamily.TRANSFER_IN,
                source = wifeExternal,
                destination = owned3001,
                bankNetworkType = BankNetworkType.INTRA_BANK,
            ),
        )

        val classified = result as ClassificationResult.Classified
        assertEquals(FinancialTransactionType.EXTERNAL_TRANSFER_IN, classified.transactionType)
        assertEquals(TransferOwnershipType.EXTERNAL_INCOMING, classified.transferOwnership)
        assertFalse(classified.impact.countsAsIncome)
        assertFalse(classified.impact.countsAsExpense)
        assertEquals(NetWorthEffect.UNRESOLVED, classified.impact.netWorthEffect)
    }

    @Test
    fun ownedToExternal_isExternalTransferOut() {
        val result = TransactionClassifier.classify(
            ClassificationContext(
                messageFamily = MessageFamily.TRANSFER_OUT,
                source = owned3001,
                destination = externalBeneficiary,
                bankNetworkType = BankNetworkType.INTRA_BANK,
            ),
        )

        val classified = result as ClassificationResult.Classified
        assertEquals(FinancialTransactionType.EXTERNAL_TRANSFER_OUT, classified.transactionType)
        assertEquals(TransferOwnershipType.EXTERNAL_OUTGOING, classified.transferOwnership)
        assertFalse(classified.impact.countsAsExpense)
        assertFalse(classified.impact.countsAsIncome)
        assertEquals(NetWorthEffect.UNRESOLVED, classified.impact.netWorthEffect)
    }

    @Test
    fun externalTransfers_doNotInferNetWorthFromCashDirection() {
        val incoming = FinancialImpactCalculator.forType(FinancialTransactionType.EXTERNAL_TRANSFER_IN)
        val outgoing = FinancialImpactCalculator.forType(FinancialTransactionType.EXTERNAL_TRANSFER_OUT)

        assertFalse(incoming.countsAsExpense)
        assertFalse(incoming.countsAsIncome)
        assertEquals(NetWorthEffect.UNRESOLVED, incoming.netWorthEffect)

        assertFalse(outgoing.countsAsExpense)
        assertFalse(outgoing.countsAsIncome)
        assertEquals(NetWorthEffect.UNRESOLVED, outgoing.netWorthEffect)
    }

    @Test
    fun billPayment_classifiesToBillPaymentType() {
        val result = TransactionClassifier.classify(
            ClassificationContext(messageFamily = MessageFamily.BILL_PAYMENT),
        )

        assertTrue(result is ClassificationResult.Classified)
        val classified = result as ClassificationResult.Classified
        assertEquals(FinancialTransactionType.BILL_PAYMENT, classified.transactionType)
        assertTrue(classified.impact.countsAsExpense)
        assertFalse(classified.impact.countsAsIncome)
        assertEquals(NetWorthEffect.DECREASE, classified.impact.netWorthEffect)
    }

    @Test
    fun debitOrCurrentPurchase_isExpense() {
        val debitResult = TransactionClassifier.classify(
            ClassificationContext(
                messageFamily = MessageFamily.PURCHASE,
                instrument = ownedDebitCard,
                purchaseChannel = PurchaseChannel.POS,
            ),
        )
        val currentResult = TransactionClassifier.classify(
            ClassificationContext(
                messageFamily = MessageFamily.PURCHASE,
                instrument = owned3001,
                purchaseChannel = PurchaseChannel.ONLINE,
            ),
        )

        val debit = debitResult as ClassificationResult.Classified
        val current = currentResult as ClassificationResult.Classified
        assertEquals(FinancialTransactionType.EXPENSE, debit.transactionType)
        assertEquals(FinancialTransactionType.EXPENSE, current.transactionType)
        assertTrue(debit.impact.countsAsExpense)
        assertTrue(current.impact.countsAsExpense)
        assertFalse(debit.impact.countsAsIncome)
    }

    @Test
    fun creditCardPurchase_isExpense() {
        val result = TransactionClassifier.classify(
            ClassificationContext(
                messageFamily = MessageFamily.PURCHASE,
                instrument = ownedCreditCard,
                purchaseChannel = PurchaseChannel.ONLINE,
            ),
        )

        val classified = result as ClassificationResult.Classified
        assertEquals(FinancialTransactionType.EXPENSE, classified.transactionType)
        assertTrue(classified.impact.countsAsExpense)
        assertEquals(NetWorthEffect.DECREASE, classified.impact.netWorthEffect)
    }

    @Test
    fun creditCardPayment_isNotExpense() {
        val fromFamily = TransactionClassifier.classify(
            ClassificationContext(
                messageFamily = MessageFamily.CARD_PAYMENT,
                source = owned3001,
                destination = ownedCreditCard,
            ),
        )
        val fromTransferShape = TransactionClassifier.classify(
            ClassificationContext(
                messageFamily = MessageFamily.TRANSFER_OUT,
                source = owned3001,
                destination = ownedCreditCard,
                bankNetworkType = BankNetworkType.INTRA_BANK,
            ),
        )

        val a = fromFamily as ClassificationResult.Classified
        val b = fromTransferShape as ClassificationResult.Classified
        assertEquals(FinancialTransactionType.CREDIT_CARD_PAYMENT, a.transactionType)
        assertEquals(FinancialTransactionType.CREDIT_CARD_PAYMENT, b.transactionType)
        assertFalse(a.impact.countsAsExpense)
        assertFalse(a.impact.countsAsIncome)
        assertEquals(NetWorthEffect.ZERO, a.impact.netWorthEffect)
        assertFalse(b.impact.countsAsExpense)
    }

    @Test
    fun refund_isNotOrdinaryIncome() {
        val result = TransactionClassifier.classify(
            ClassificationContext(messageFamily = MessageFamily.REFUND),
        )

        val classified = result as ClassificationResult.Classified
        assertEquals(FinancialTransactionType.REFUND, classified.transactionType)
        assertFalse(classified.impact.countsAsIncome)
        assertFalse(classified.impact.countsAsExpense)
        assertEquals(NetWorthEffect.INCREASE, classified.impact.netWorthEffect)
    }

    @Test
    fun cashWithdrawal_isNotAutomaticallyExpense() {
        val result = TransactionClassifier.classify(
            ClassificationContext(
                messageFamily = MessageFamily.WITHDRAWAL,
                instrument = owned3001,
            ),
        )

        val classified = result as ClassificationResult.Classified
        assertEquals(FinancialTransactionType.CASH_WITHDRAWAL, classified.transactionType)
        assertFalse(classified.impact.countsAsExpense)
        assertFalse(classified.impact.countsAsIncome)
        assertEquals(NetWorthEffect.ZERO, classified.impact.netWorthEffect)
    }

    @Test
    fun fee_countsAsExpense() {
        val result = TransactionClassifier.classify(
            ClassificationContext(messageFamily = MessageFamily.FEE),
        )

        val classified = result as ClassificationResult.Classified
        assertEquals(FinancialTransactionType.FEE, classified.transactionType)
        assertTrue(classified.impact.countsAsExpense)
        assertEquals(NetWorthEffect.DECREASE, classified.impact.netWorthEffect)
    }

    @Test
    fun unknownOwnership_doesNotGuessSelfOrExternalTransfer() {
        val ownedToUnknown = TransactionClassifier.classify(
            ClassificationContext(
                messageFamily = MessageFamily.TRANSFER_OUT,
                source = owned3001,
                destination = unknownAccount,
                bankNetworkType = BankNetworkType.INTRA_BANK,
            ),
        )
        val unknownToOwned = TransactionClassifier.classify(
            ClassificationContext(
                messageFamily = MessageFamily.TRANSFER_IN,
                source = unknownAccount,
                destination = owned3001,
                bankNetworkType = BankNetworkType.INTER_BANK,
            ),
        )
        val unknownToUnknown = TransactionClassifier.classify(
            ClassificationContext(
                messageFamily = MessageFamily.TRANSFER_OUT,
                source = unknownAccount,
                destination = unknownAccount.copy(id = "acct-unknown-2"),
            ),
        )

        listOf(ownedToUnknown, unknownToOwned, unknownToUnknown).forEach { result ->
            assertTrue(result is ClassificationResult.NeedsReview)
            val review = result as ClassificationResult.NeedsReview
            assertEquals(TransferOwnershipType.UNKNOWN, review.transferOwnership)
            assertTrue(review.tentativeType != FinancialTransactionType.SELF_TRANSFER)
            assertTrue(review.tentativeType != FinancialTransactionType.EXTERNAL_TRANSFER_IN)
            assertTrue(review.tentativeType != FinancialTransactionType.EXTERNAL_TRANSFER_OUT)
        }
    }

    @Test
    fun cardPaymentEvidence_ownedAccountToOwnedCard_withoutInventedCardType() {
        val result = TransactionClassifier.classify(
            ClassificationEvidence(
                messageFamily = MessageFamily.CARD_PAYMENT,
                source = ResolvedContainerFacts(
                    kind = ContainerKind.ACCOUNT,
                    ownership = OwnershipStatus.OWNED,
                ),
                destination = ResolvedContainerFacts(
                    kind = ContainerKind.CARD,
                    ownership = OwnershipStatus.OWNED,
                    knownCardType = null,
                ),
            ),
        )

        val classified = result as ClassificationResult.Classified
        assertEquals(FinancialTransactionType.CREDIT_CARD_PAYMENT, classified.transactionType)
        assertFalse(classified.impact.countsAsExpense)
    }

    @Test
    fun transferFamily_requiresKnownCreditTypeForCardPaymentShape() {
        val withoutType = TransactionClassifier.classify(
            ClassificationEvidence(
                messageFamily = MessageFamily.TRANSFER_OUT,
                source = ResolvedContainerFacts(
                    kind = ContainerKind.ACCOUNT,
                    ownership = OwnershipStatus.OWNED,
                ),
                destination = ResolvedContainerFacts(
                    kind = ContainerKind.CARD,
                    ownership = OwnershipStatus.OWNED,
                    knownCardType = null,
                ),
            ),
        ) as ClassificationResult.Classified
        // Without a genuine CardType.CREDIT, do not invent credit-card payment.
        assertEquals(FinancialTransactionType.SELF_TRANSFER, withoutType.transactionType)

        val withCredit = TransactionClassifier.classify(
            ClassificationEvidence(
                messageFamily = MessageFamily.TRANSFER_OUT,
                source = ResolvedContainerFacts(
                    kind = ContainerKind.ACCOUNT,
                    ownership = OwnershipStatus.OWNED,
                ),
                destination = ResolvedContainerFacts(
                    kind = ContainerKind.CARD,
                    ownership = OwnershipStatus.OWNED,
                    knownCardType = CardType.CREDIT,
                ),
            ),
        ) as ClassificationResult.Classified
        assertEquals(FinancialTransactionType.CREDIT_CARD_PAYMENT, withCredit.transactionType)
    }

    @Test
    fun networkIndependence_sameOwnership_sameClassification() {
        val networks = listOf(
            BankNetworkType.INTRA_BANK,
            BankNetworkType.INTER_BANK,
            BankNetworkType.UNKNOWN,
            null,
        )

        networks.forEach { network ->
            val ownedOwned = TransactionClassifier.classify(
                ClassificationContext(
                    messageFamily = MessageFamily.TRANSFER_OUT,
                    source = owned3001,
                    destination = owned3002,
                    bankNetworkType = network,
                ),
            ) as ClassificationResult.Classified
            assertEquals(FinancialTransactionType.SELF_TRANSFER, ownedOwned.transactionType)

            val externalOwned = TransactionClassifier.classify(
                ClassificationContext(
                    messageFamily = MessageFamily.TRANSFER_IN,
                    source = wifeExternal,
                    destination = owned3001,
                    bankNetworkType = network,
                ),
            ) as ClassificationResult.Classified
            assertEquals(FinancialTransactionType.EXTERNAL_TRANSFER_IN, externalOwned.transactionType)
        }
    }
}

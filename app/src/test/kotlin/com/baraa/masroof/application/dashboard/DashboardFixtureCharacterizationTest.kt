package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.ids.FinancialContainerIdFactory
import com.baraa.masroof.domain.ids.TransactionIdFactory
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.CardRegistryEntry
import com.baraa.masroof.domain.model.CardType
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.model.LoanType
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.parsing.fixtures.AlJaziraFixtureParseHarness
import com.baraa.masroof.parsing.model.CardSmsChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * End-to-end characterization: on-disk Bank AlJazira fixtures → parse facts → dashboard helpers.
 */
class DashboardFixtureCharacterizationTest {
    @Test
    fun salaryTransferFixture_detectedAsSalaryIncome() {
        val record = AlJaziraFixtureParseHarness.parseRecord("transfer_in_salary_ar_001")
        assertEquals(true, record.details.salaryIncomeWording)

        val tx = FinancialTransaction(
            id = "tx-salary",
            type = FinancialTransactionType.EXTERNAL_TRANSFER_IN,
            amount = Money.of("3191.68", Currency.SAR),
            occurredAt = Instant.parse("2026-07-27T01:12:00Z"),
            sourceContainerId = null,
            destinationContainerId = FinancialContainerIdFactory.accountId(Bank.BANK_ALJAZIRA, "3001"),
            merchant = null,
            counterparty = null,
            categoryId = null,
            linkedParsedEventIds = listOf(record.event.id),
        )

        assertTrue(SalaryIncomeHeuristics.isSalaryIncome(tx, mapOf(record.event.id to record)))
    }

    @Test
    fun debitPurchaseFixture_infersLinkedAccountFromParseFact() {
        val record = AlJaziraFixtureParseHarness.parseRecord("purchase_pos_ar_debit_001")
        assertEquals(CardSmsChannel.DEBIT, record.details.cardSmsChannel)
        assertEquals("3001", record.details.debitSourceAccountLast4)

        assertEquals(
            "3001",
            DebitLinkedAccountInferrer.inferAccountLast4(
                bank = Bank.BANK_ALJAZIRA,
                cardLast4 = "2210",
                parsedRecords = listOf(record),
            ),
        )
    }

    @Test
    fun financingInstallmentFixture_resolvesLoanRepaymentContainer() {
        val record = AlJaziraFixtureParseHarness.parseRecord("financing_installment_ar_001")
        assertEquals(LoanType.PERSONAL, record.details.loanType)

        val tx = FinancialTransaction(
            id = TransactionIdFactory.fromRawSmsIds(listOf(record.event.rawSmsId)),
            type = FinancialTransactionType.FEE,
            amount = Money.of("3036.11", Currency.SAR),
            occurredAt = Instant.parse("2026-08-27T01:10:00Z"),
            sourceContainerId = FinancialContainerIdFactory.accountId(Bank.BANK_ALJAZIRA, "3001"),
            destinationContainerId = null,
            merchant = null,
            counterparty = record.event.counterparty,
            categoryId = null,
            linkedParsedEventIds = listOf(record.event.id),
        )

        val loanId = FinancialContainerIdFactory.loanId(Bank.BANK_ALJAZIRA, LoanType.PERSONAL)
        assertEquals(loanId, LoanRepaymentAttribution.loanContainerId(tx, mapOf(record.event.id to record)))
        assertTrue(LoanRepaymentAttribution.isLoanRepayment(tx, mapOf(record.event.id to record)))
    }

    @Test
    fun googlePayDebitFixture_classifiesRegistryEntryAsDebit() {
        val record = AlJaziraFixtureParseHarness.parseRecord("purchase_pos_ar_debit_googlepay_001")
        assertEquals(CardSmsChannel.DEBIT, record.details.cardSmsChannel)

        val entry = CardRegistryEntry.forTest(
            bank = Bank.BANK_ALJAZIRA,
            last4 = "8219",
            ownership = OwnershipStatus.OWNED,
            cardType = null,
            firstSeenRawSmsId = record.event.rawSmsId,
            lastSeenRawSmsId = record.event.rawSmsId,
        )

        assertTrue(
            CardRegistryDebitClassifier.isDebitRegistryEntry(
                entry = entry,
                parsedRecords = listOf(record),
            ),
        )
    }

    @Test
    fun creditPurchaseFixture_isNotDebitChannel() {
        val record = AlJaziraFixtureParseHarness.parseRecord("purchase_pos_ar_cc_001")
        assertEquals(CardSmsChannel.CREDIT, record.details.cardSmsChannel)

        val entry = CardRegistryEntry.forTest(
            bank = Bank.BANK_ALJAZIRA,
            last4 = "7271",
            ownership = OwnershipStatus.OWNED,
            cardType = CardType.CREDIT,
            firstSeenRawSmsId = record.event.rawSmsId,
            lastSeenRawSmsId = record.event.rawSmsId,
        )

        assertFalse(
            CardRegistryDebitClassifier.isDebitRegistryEntry(
                entry = entry,
                parsedRecords = listOf(record),
            ),
        )
    }
}

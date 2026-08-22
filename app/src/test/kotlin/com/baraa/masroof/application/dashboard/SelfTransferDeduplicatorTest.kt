package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.FinancialTransactionType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class SelfTransferDeduplicatorTest {
  @Test
  fun duplicateSelfTransfers_keepCanonicalWithMoreLinkedEvents() {
    val account1 = "account:bank_aljazira:3001"
    val account3 = "account:bank_aljazira:3003"
    val duplicate = tx(
      id = "self-a",
      type = FinancialTransactionType.SELF_TRANSFER,
      amount = "4445.67",
      source = account1,
      dest = account3,
      linked = listOf("evt-out"),
    )
    val canonical = tx(
      id = "self-b",
      type = FinancialTransactionType.SELF_TRANSFER,
      amount = "4445.67",
      source = account1,
      dest = account3,
      linked = listOf("evt-out", "evt-in"),
    )

    val filtered = SelfTransferDeduplicator.filter(
      transactions = listOf(duplicate, canonical),
      parsedRecords = emptyList(),
    )

    assertEquals(listOf("self-b"), filtered.map { it.id })
  }

  @Test
  fun account3003_duplicateInternalTransfers_netToZeroCashPosition() {
    val account1 = "account:bank_aljazira:3001"
    val account3 = "account:bank_aljazira:3003"
    val amounts = listOf("4445.67", "0.33", "28093.33")
  val transactions = amounts.flatMap { amount ->
      listOf(
        tx(
          id = "self-$amount-a",
          type = FinancialTransactionType.SELF_TRANSFER,
          amount = amount,
          source = account1,
          dest = account3,
          linked = listOf("evt-$amount-out"),
        ),
        tx(
          id = "self-$amount-b",
          type = FinancialTransactionType.SELF_TRANSFER,
          amount = amount,
          source = account1,
          dest = account3,
          linked = listOf("evt-$amount-in"),
        ),
      )
    } + tx(
      id = "external-out",
      type = FinancialTransactionType.EXTERNAL_TRANSFER_OUT,
      amount = "32539.33",
      source = account3,
      dest = null,
      linked = listOf("evt-external"),
    )

    val filtered = SelfTransferDeduplicator.filter(transactions, parsedRecords = emptyList())
    assertEquals(4, filtered.size)

    val summary = CurrentAccountSummaryCalculator.summarize(
      transactions = filtered,
      parsedRecords = emptyList(),
      ownedAccountContainerIds = setOf(account3),
      ownedAccountLast4s = setOf("3003"),
      scopeMode = AccountFlowScopeMode.SingleAccount,
    )

    assertEquals(Money.of("32539.33", Currency.SAR), summary.inflow.selfTransfersIn)
    assertEquals(Money.zero(Currency.SAR), summary.outflow.selfTransfersOut)
    assertEquals(SignedMoneyAmount.zero(Currency.SAR), summary.accountFlow().accountSummary().remaining)
  }

  private fun tx(
    id: String,
    type: FinancialTransactionType,
    amount: String,
    source: String?,
    dest: String?,
    linked: List<String>,
  ): FinancialTransaction =
    FinancialTransaction(
      id = id,
      type = type,
      amount = Money.of(amount, Currency.SAR),
      occurredAt = Instant.parse("2026-08-02T12:00:00Z"),
      sourceContainerId = source,
      destinationContainerId = dest,
      merchant = null,
      counterparty = null,
      categoryId = null,
      linkedParsedEventIds = linked,
    )
}

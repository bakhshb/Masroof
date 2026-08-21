package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountFlowTest {
    @Test
    fun externalSummary_excludesInternalTransfers() {
        val flow = account3001Flow()

        assertEquals(Money.of("66024.68", Currency.SAR), flow.externalIn)
        assertEquals(Money.of("10685.63", Currency.SAR), flow.externalOut)
        assertEquals(
            SignedMoneyAmount.of(Money.of("55339.05", Currency.SAR)),
            flow.externalSummary().remaining,
        )
    }

    @Test
    fun accountSummary_includesAllInAndAllOut() {
        val flow = account3001Flow()

        assertEquals(Money.of("66024.68", Currency.SAR), flow.totalIn)
        assertEquals(Money.of("86763.63", Currency.SAR), flow.totalOut)
        assertEquals(
            SignedMoneyAmount.difference(flow.totalIn, flow.totalOut),
            flow.accountSummary().remaining,
        )
    }

    @Test
    fun fleetAccountSummary_sumsAllAccountsInAndOut() {
        val fleet = FleetAccountFlow(
            accounts = listOf(
                account3001Flow(),
                AccountFlow.zero(Currency.SAR).copy(
                    internalTransfersIn = Money.of("76078.00", Currency.SAR),
                ),
            ),
        )

        val summary = fleet.accountSummary()!!
        assertEquals(Money.of("142102.68", Currency.SAR), summary.inflow)
        assertEquals(Money.of("86763.63", Currency.SAR), summary.outflow)
        assertEquals(
            SignedMoneyAmount.of(Money.of("55339.05", Currency.SAR)),
            summary.remaining,
        )
    }

    private fun account3001Flow(): AccountFlow =
        AccountFlow.from(
            CurrentAccountSummary.of(
                currency = Currency.SAR,
                salary = Money.of("31731.68", Currency.SAR),
                otherIncome = Money.zero(Currency.SAR),
                externalTransfersIn = Money.of("34293.00", Currency.SAR),
                selfTransfersIn = Money.zero(Currency.SAR),
                creditCardPayments = Money.zero(Currency.SAR),
                billPayments = Money.of("2345.52", Currency.SAR),
                externalTransfersOut = Money.of("5304.00", Currency.SAR),
                cashWithdrawals = Money.zero(Currency.SAR),
                posPurchases = Money.zero(Currency.SAR),
                fees = Money.of("3036.11", Currency.SAR),
                selfTransfersOut = Money.of("76078.00", Currency.SAR),
            ),
        )
}

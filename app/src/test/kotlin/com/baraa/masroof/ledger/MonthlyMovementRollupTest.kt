package com.baraa.masroof.ledger

import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.OpeningBalanceKind
import com.baraa.masroof.transaction.AccountNature
import com.baraa.masroof.transaction.AccountType
import com.baraa.masroof.transaction.Currency
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

class MonthlyMovementRollupTest {
    @Test
    fun monthMovementSumsDailyBucketsNotLastDayOnly() {
        val account = FinancialAccount(
            id = 1L,
            displayName = "Bank",
            institutionName = "Bank",
            accountType = AccountType.BANK_ACCOUNT,
            accountNature = AccountNature.ASSET,
            currency = Currency.SAR,
            openingBalance = BigDecimal("1000"),
            openingBalanceDate = LocalDate.of(2026, 8, 1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
            includeInNetWorth = true,
            includeInLiquidity = true,
            isOwnedByUser = true,
            isActive = true,
            notes = null,
        )
        val day1 = HistoricalFinancialService.calculateDay(
            LocalDate.of(2026, 8, 1),
            listOf(account),
            emptyList(),
        ).copy(
            movement = DailyFinancialMovement(income = BigDecimal("100"), expenses = BigDecimal("10"), netCashMovement = BigDecimal("90")),
        )
        val day2 = HistoricalFinancialService.calculateDay(
            LocalDate.of(2026, 8, 2),
            listOf(account),
            emptyList(),
        ).copy(
            movement = DailyFinancialMovement(income = BigDecimal("50"), expenses = BigDecimal("20"), netCashMovement = BigDecimal("30")),
        )
        val history = MonthlyFinancialHistory(
            YearMonth.of(2026, 8),
            mapOf(day1.selectedDate to day1, day2.selectedDate to day2),
        )
        val month = history.monthMovement()
        assertEquals(0, BigDecimal("150").compareTo(month.income))
        assertEquals(0, BigDecimal("30").compareTo(month.expenses))
    }

    @Test
    fun availableOpeningBalanceConvertsToOutstandingOnBalancePath() {
        val card = FinancialAccount(
            id = 2L,
            displayName = "Visa",
            institutionName = "Bank",
            accountType = AccountType.CREDIT_CARD,
            accountNature = AccountNature.LIABILITY,
            currency = Currency.SAR,
            openingBalance = BigDecimal("8000"),
            openingBalanceDate = LocalDate.of(2026, 8, 1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
            includeInNetWorth = true,
            includeInLiquidity = false,
            isOwnedByUser = true,
            isActive = true,
            notes = null,
            creditLimit = BigDecimal("10000"),
            openingBalanceKind = OpeningBalanceKind.AVAILABLE,
        )
        val balance = AccountBalanceService.balance(card, emptyList(), LocalDate.of(2026, 8, 1))
        assertEquals(0, BigDecimal("2000").compareTo(balance))
    }
}

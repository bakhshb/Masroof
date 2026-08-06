package com.baraa.masroof.ledger

import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.JournalEntryEntity
import com.baraa.masroof.data.db.JournalWithPostings
import com.baraa.masroof.data.db.LedgerPostingEntity
import com.baraa.masroof.transaction.AccountNature
import com.baraa.masroof.transaction.AccountType
import com.baraa.masroof.transaction.Currency
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class HistoricalFinancialServiceTest {
    private val zone = ZoneId.systemDefault(); private val opening = LocalDate.of(2026, 8, 1)
    private fun account(id: Long = 1, nature: AccountNature = AccountNature.ASSET, amount: String = "100") = FinancialAccount(
        id, "حساب", null, AccountType.BANK_ACCOUNT, nature, Currency.SAR, BigDecimal(amount),
        opening.atStartOfDay(zone).toInstant().toEpochMilli(), true, nature == AccountNature.ASSET, true, null, true, null,
    )
    private fun journal(day: LocalDate, side: PostingSide, amount: String = "20", status: JournalPostingStatus = JournalPostingStatus.POSTED) = JournalWithPostings(JournalEntryEntity(1,null,JournalType.INCOME,status,day,LocalTime.NOON,"x",0,0,null,null,JournalGeneratedBy.USER,1), listOf(LedgerPostingEntity(1,1,1,side,BigDecimal(amount),Currency.SAR,null,0)))
    @Test fun beforeOpeningIsNotZeroTracked() { assertEquals(HistoricalTrackingStatus.NOT_STARTED, HistoricalFinancialService.calculateDay(opening.minusDays(1), listOf(account()), emptyList()).accounts.single().trackingStatus) }
    @Test fun openingStartAndEndAreDistinct() { val s = HistoricalFinancialService.calculateDay(opening, listOf(account()), listOf(journal(opening, PostingSide.DEBIT))); assertEquals(BigDecimal("100"), s.startOfDayAssets); assertEquals(BigDecimal("120"), s.endOfDayAssets) }
    @Test fun liabilityCreditIncreasesOwedAndUnpostedExcluded() { val s = HistoricalFinancialService.calculateDay(opening, listOf(account(nature=AccountNature.LIABILITY)), listOf(journal(opening, PostingSide.CREDIT), journal(opening, PostingSide.CREDIT, status=JournalPostingStatus.DRAFT))); assertEquals(BigDecimal("120"), s.endOfDayLiabilities); assertEquals(1, s.postedJournalCount) }
}

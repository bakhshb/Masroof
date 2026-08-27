package com.baraa.masroof.domain.loan

import com.baraa.masroof.bank.aljazira.AlJaziraParsingPipeline
import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.assembly.TransactionAssembler
import com.baraa.masroof.domain.ids.FinancialContainerIdFactory
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.model.LoanType
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.model.ParseStatus
import com.baraa.masroof.parsing.model.ParseResult
import com.baraa.masroof.parsing.model.SmsParseInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class FinancingInstallmentAssemblyTest {
    private val pipeline = AlJaziraParsingPipeline()

    private val body = """
        خصم: قسط تمويل
        من: 3001
        القسط: SAR 3,036.11
        المبلغ المتبقي: SAR 33,397.25
        لـ: تمويل شخصي
        في: 2026-08-27 01:10
    """.trimIndent()

    @Test
    fun parsesFinancingInstallmentSms() {
        val result = pipeline.parse(
            SmsParseInput(
                rawSmsId = "sms-loan",
                sender = "AlJazira",
                body = body,
                receivedAt = Instant.parse("2026-08-27T01:10:00Z"),
            ),
        ) as ParseResult.Success

        assertEquals(MessageFamily.FINANCING_INSTALLMENT, result.event.messageFamily)
        assertEquals(ParseStatus.SUCCESS, result.event.parseStatus)
        assertEquals(Money.of("3036.11", Currency.SAR), result.event.amount)
        assertEquals("3001", result.event.sourceAccountRef?.maskedNumber)
        assertEquals("تمويل شخصي", result.event.counterparty)
        assertEquals(LoanType.PERSONAL, LoanTypeResolver.fromLabel(result.event.counterparty))
    }

    @Test
    fun assemblesOwnedAccountToOwnedLoanRepayment() {
        val parsed = pipeline.parse(
            SmsParseInput(
                rawSmsId = "sms-loan",
                sender = "AlJazira",
                body = body,
                receivedAt = Instant.parse("2026-08-27T01:10:00Z"),
            ),
        ) as ParseResult.Success

        val outcome = TransactionAssembler.assembleSingle(
            event = parsed.event,
            receivedAt = Instant.parse("2026-08-27T01:10:00Z"),
            sourceOwnership = OwnershipStatus.OWNED,
            destinationOwnership = OwnershipStatus.OWNED,
            cardOwnership = OwnershipStatus.UNKNOWN,
            loanOwnership = OwnershipStatus.OWNED,
        ) as TransactionAssembler.Outcome.Assembled

        assertEquals(FinancialTransactionType.LOAN_REPAYMENT, outcome.transaction.type)
        assertEquals(
            FinancialContainerIdFactory.accountId(Bank.BANK_ALJAZIRA, "3001"),
            outcome.transaction.sourceContainerId,
        )
        assertEquals(
            FinancialContainerIdFactory.loanId(Bank.BANK_ALJAZIRA, LoanType.PERSONAL),
            outcome.transaction.destinationContainerId,
        )
        assertFalse(outcome.transaction.type == FinancialTransactionType.FEE)
    }

    @Test
    fun needsReviewWhenLoanOwnershipUnknown() {
        val parsed = pipeline.parse(
            SmsParseInput(
                rawSmsId = "sms-loan",
                sender = "AlJazira",
                body = body,
                receivedAt = Instant.parse("2026-08-27T01:10:00Z"),
            ),
        ) as ParseResult.Success

        val outcome = TransactionAssembler.assembleSingle(
            event = parsed.event,
            receivedAt = Instant.parse("2026-08-27T01:10:00Z"),
            sourceOwnership = OwnershipStatus.OWNED,
            destinationOwnership = OwnershipStatus.UNKNOWN,
            cardOwnership = OwnershipStatus.UNKNOWN,
            loanOwnership = OwnershipStatus.UNKNOWN,
        )
        assertTrue(outcome is TransactionAssembler.Outcome.NeedsReview)
    }
}

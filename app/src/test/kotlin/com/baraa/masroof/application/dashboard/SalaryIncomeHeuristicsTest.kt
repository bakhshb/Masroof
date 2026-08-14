package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.model.RawSms
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SalaryIncomeHeuristicsTest {
    @Test
    fun incomeType_isAlwaysSalary() {
        val tx = transaction(FinancialTransactionType.INCOME)
        assertTrue(
            SalaryIncomeHeuristics.isSalaryIncome(tx, emptyMap(), emptyMap()),
        )
    }

    @Test
    fun externalTransferWithSalarySms_isSalary() {
        val tx = transaction(
            type = FinancialTransactionType.EXTERNAL_TRANSFER_IN,
            linked = listOf("evt-1"),
        )
        val records = mapOf(
            "evt-1" to parsedRecord("evt-1", "sms-1"),
        )
        val raw = mapOf(
            "sms-1" to rawSms("sms-1", "حوالة واردة راتب\nمبلغ: SAR 3,191.68"),
        )
        assertTrue(SalaryIncomeHeuristics.isSalaryIncome(tx, records, raw))
    }

    @Test
    fun externalTransferWithoutSalaryWording_isNotSalary() {
        val tx = transaction(
            type = FinancialTransactionType.EXTERNAL_TRANSFER_IN,
            linked = listOf("evt-1"),
        )
        val records = mapOf(
            "evt-1" to parsedRecord("evt-1", "sms-1"),
        )
        val raw = mapOf(
            "sms-1" to rawSms("sms-1", "حوالة واردة\nمبلغ: SAR 200.00"),
        )
        assertFalse(SalaryIncomeHeuristics.isSalaryIncome(tx, records, raw))
    }

    private fun transaction(
        type: FinancialTransactionType,
        linked: List<String> = emptyList(),
    ): FinancialTransaction =
        FinancialTransaction(
            id = "tx-1",
            type = type,
            amount = Money.of("100.00", Currency.SAR),
            occurredAt = java.time.Instant.parse("2026-08-10T12:00:00Z"),
            sourceContainerId = null,
            destinationContainerId = "account:bank_aljazira:3001",
            merchant = null,
            counterparty = null,
            categoryId = null,
            linkedParsedEventIds = linked,
        )

    private fun parsedRecord(eventId: String, rawSmsId: String) =
        com.baraa.masroof.parsing.repository.ParsedEventRecord(
            event = com.baraa.masroof.domain.model.ParsedEvent(
                id = eventId,
                rawSmsId = rawSmsId,
                bank = com.baraa.masroof.domain.model.Bank.BANK_ALJAZIRA,
                messageFamily = com.baraa.masroof.domain.model.MessageFamily.TRANSFER_IN,
                direction = com.baraa.masroof.domain.model.MoneyDirection.INCOMING,
                amount = Money.of("100.00", Currency.SAR),
                purchaseChannel = null,
                sourceAccountRef = null,
                destinationAccountRef = null,
                cardRef = null,
                merchant = null,
                counterparty = null,
                occurredAt = java.time.Instant.parse("2026-08-10T12:00:00Z"),
                bankNetworkType = null,
                confidence = com.baraa.masroof.domain.model.Confidence(1.0),
                parseStatus = com.baraa.masroof.domain.model.ParseStatus.SUCCESS,
            ),
            details = com.baraa.masroof.parsing.model.ParsedEventDetails(),
        )

    private fun rawSms(id: String, body: String) = RawSms(
        id = id,
        sender = "AlJazira",
        body = body,
        receivedAt = java.time.Instant.parse("2026-08-10T12:00:00Z"),
        deviceMessageId = id,
        bodyHash = id,
    )
}

package com.baraa.masroof.application.dashboard

import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.parsing.repository.ParsedEventRecord

/**
 * Detects salary-like inflows from transaction type and persisted parse facts.
 *
 * Salary is not a separate [FinancialTransactionType]; it may appear as [INCOME]
 * or as an inbound transfer whose SMS mentions salary at parse time.
 */
object SalaryIncomeHeuristics {
    fun isSalaryIncome(
        transaction: FinancialTransaction,
        parsedRecordsById: Map<String, ParsedEventRecord>,
    ): Boolean {
        if (transaction.type == FinancialTransactionType.INCOME) return true
        if (transaction.type != FinancialTransactionType.EXTERNAL_TRANSFER_IN) return false

        val textParts = buildList {
            transaction.counterparty?.let { add(it) }
            transaction.merchant?.let { add(it) }
            for (eventId in transaction.linkedParsedEventIds) {
                val record = parsedRecordsById[eventId] ?: continue
                if (record.details.salaryIncomeWording == true) return true
                record.event.counterparty?.let { add(it) }
                record.event.merchant?.let { add(it) }
            }
        }
        return textParts.any { part -> containsSalaryKeyword(part) }
    }

    private fun containsSalaryKeyword(text: String): Boolean {
        val normalized = text.lowercase()
        return salaryKeywords.any { keyword -> normalized.contains(keyword) }
    }

    private val salaryKeywords = listOf(
        "راتب",
        "رواتب",
        "salary",
        "payroll",
        "wage",
    )
}

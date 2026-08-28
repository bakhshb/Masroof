package com.baraa.masroof.application.dashboard

import com.baraa.masroof.domain.ids.FinancialContainerIdFactory
import com.baraa.masroof.domain.loan.LoanTypeResolver
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.model.LoanType
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.parsing.repository.ParsedEventRecord

/**
 * Resolves which loan container a transaction repays, including legacy [FinancialTransactionType.FEE]
 * rows whose linked SMS is [MessageFamily.FINANCING_INSTALLMENT].
 */
object LoanRepaymentAttribution {
    fun loanContainerId(
        tx: FinancialTransaction,
        parsedRecordsById: Map<String, ParsedEventRecord>,
    ): String? {
        if (tx.type == FinancialTransactionType.LOAN_REPAYMENT) {
            tx.destinationContainerId?.let { return it }
        }
        financingInstallmentRecords(tx, parsedRecordsById).forEach { record ->
            val loanType = LoanTypeResolver.fromLabel(record.event.counterparty) ?: return@forEach
            return FinancialContainerIdFactory.loanId(record.event.bank, loanType)
        }
        return null
    }

    fun matchesLoan(
        tx: FinancialTransaction,
        parsedRecordsById: Map<String, ParsedEventRecord>,
        bank: Bank,
        loanType: LoanType,
    ): Boolean {
        val expected = FinancialContainerIdFactory.loanId(bank, loanType) ?: return false
        return loanContainerId(tx, parsedRecordsById) == expected
    }

    fun buildInvolvementIndex(
        transactions: List<FinancialTransaction>,
        parsedRecords: List<ParsedEventRecord>,
    ): Map<String, Set<String>> {
        if (transactions.isEmpty()) return emptyMap()
        val parsedRecordsById = parsedRecords.associateBy { it.event.id }
        return transactions.mapNotNull { tx ->
            loanContainerId(tx, parsedRecordsById)?.let { loanId -> tx.id to setOf(loanId) }
        }.toMap()
    }

    private fun financingInstallmentRecords(
        tx: FinancialTransaction,
        parsedRecordsById: Map<String, ParsedEventRecord>,
    ): List<ParsedEventRecord> =
        tx.linkedParsedEventIds.mapNotNull { parsedRecordsById[it] }
            .filter { it.event.messageFamily == MessageFamily.FINANCING_INSTALLMENT }
}

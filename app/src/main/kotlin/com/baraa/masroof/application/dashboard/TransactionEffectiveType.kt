package com.baraa.masroof.application.dashboard

import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.parsing.repository.ParsedEventRecord

/**
 * Presentation and aggregation semantics derived from linked SMS, not only stored type.
 */
object TransactionEffectiveType {
    fun resolve(
        tx: FinancialTransaction,
        parsedRecordsById: Map<String, ParsedEventRecord>,
    ): FinancialTransactionType =
        if (LoanRepaymentAttribution.isLoanRepayment(tx, parsedRecordsById)) {
            FinancialTransactionType.LOAN_REPAYMENT
        } else {
            tx.type
        }

    fun resolve(
        tx: FinancialTransaction,
        parsedRecords: List<ParsedEventRecord>,
    ): FinancialTransactionType = resolve(tx, parsedRecords.associateBy { it.event.id })
}

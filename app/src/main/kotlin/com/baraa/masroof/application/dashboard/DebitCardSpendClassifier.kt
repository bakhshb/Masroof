package com.baraa.masroof.application.dashboard

import com.baraa.masroof.domain.ids.FinancialContainerIdFactory
import com.baraa.masroof.domain.model.CardRegistryEntry
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.FinancialTransactionType

/**
 * Shared rules for Mada salary-period spending: POS purchases, cash withdrawals, and refunds.
 */
object DebitCardSpendClassifier {
    enum class Effect {
        Expense,
        Refund,
    }

    fun scopeFor(
        entry: CardRegistryEntry,
        ownedAccountContainerIds: Set<String>,
        ownedAccountLast4s: Set<String>,
    ): CurrentAccountTransactionScope {
        val linkedContainerId = entry.linkedAccount?.let { account ->
            FinancialContainerIdFactory.accountId(account)
        }
        val linkedLast4s = entry.linkedAccountMaskedNumber?.let { masked ->
            CurrentAccountTransactionScope.ownedAccountLast4sFromMaskedNumbers(listOf(masked))
        }.orEmpty()
        return when {
            linkedContainerId != null ->
                CurrentAccountTransactionScope(
                    ownedContainerIds = setOf(linkedContainerId),
                    ownedAccountLast4s = linkedLast4s,
                    mode = AccountFlowScopeMode.SingleAccount,
                )
            else ->
                CurrentAccountTransactionScope(
                    ownedContainerIds = ownedAccountContainerIds,
                    ownedAccountLast4s = ownedAccountLast4s,
                    mode = AccountFlowScopeMode.Fleet,
                )
        }
    }

    fun effectFor(
        tx: FinancialTransaction,
        bankId: String,
        last4: String,
        scope: CurrentAccountTransactionScope,
        context: AccountFlowClassificationContext,
        cardInvolvement: Map<String, Set<String>>,
    ): Effect? {
        if (!CardTransactionInvolvementResolver.matchesCard(tx.id, bankId, last4, cardInvolvement)) {
            return null
        }
        if (tx.type == FinancialTransactionType.REFUND) {
            return Effect.Refund
        }
        val isDebitSpend = AccountFlowClassifier.classify(tx, scope, context).any { assignment ->
            assignment is FlowAssignment.Expense &&
                (
                    assignment.category == FlowExpenseCategory.POS_PURCHASE ||
                        assignment.category == FlowExpenseCategory.CASH_WITHDRAWAL
                    )
        }
        return if (isDebitSpend) Effect.Expense else null
    }
}

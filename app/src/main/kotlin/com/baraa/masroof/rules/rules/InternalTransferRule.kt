package com.baraa.masroof.rules.rules

import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.ledger.AccountIdentifierCompatibility
import com.baraa.masroof.ledger.FinancialInstitutionResolver
import com.baraa.masroof.rules.AccountMatching
import com.baraa.masroof.rules.RuleContext
import com.baraa.masroof.rules.RuleInput
import com.baraa.masroof.rules.RulePriority
import com.baraa.masroof.rules.RuleResult
import com.baraa.masroof.rules.TransactionRule
import com.baraa.masroof.transaction.CategorySource
import com.baraa.masroof.transaction.FinancialTreatment
import com.baraa.masroof.transaction.IdentifierRole
import com.baraa.masroof.transaction.ParsedIdentifierEvidence
import com.baraa.masroof.transaction.TransactionType

/**
 * Classifies a transfer as INTERNAL_TRANSFER when the SMS can be
 * confidently matched to two of the user's owned accounts (one as source,
 * one as destination). Typed identifier evidence is matched by type+value
 * against [RuleContext.accountIdentifiers]. Sender links use
 * [RuleContext.accountsBySenderKey]. Ambiguous matches return null.
 */
class InternalTransferRule : TransactionRule {
    override val name: String = "InternalTransferRule"
    override val priority: RulePriority = RulePriority.INTERNAL_TRANSFER

    override fun evaluate(input: RuleInput, context: RuleContext): RuleResult? {
        if (input.type !in TRANSFER_TYPES) return null

        val owned = context.ownedAccounts.filter { it.isOwnedByUser && it.isActive }
        if (owned.size < 2) return null

        val source = matchSource(owned, input, context) ?: return null
        val destination = matchDestination(owned, source, input, context) ?: return null

        return RuleResult(
            financialTreatment = FinancialTreatment.INTERNAL_TRANSFER,
            categoryId = null,
            confidence = 90,
            reason = "transfer between owned accounts: ${source.displayName} -> ${destination.displayName}",
            source = CategorySource.RULE,
            excludeFromSpending = true,
        )
    }

    private fun matchSource(
        owned: List<FinancialAccount>,
        input: RuleInput,
        context: RuleContext,
    ): FinancialAccount? {
        val sourceEvidence = input.parsed.identifierEvidence.firstOrNull {
            it.role == IdentifierRole.SOURCE
        } ?: input.parsed.identifierEvidence.firstOrNull {
            it.role == IdentifierRole.UNSPECIFIED
        }
        if (sourceEvidence != null) {
            findByEvidence(owned, sourceEvidence, context)?.let { return it }
        }
        matchBySenderLink(owned, input.sender, context)?.let { return it }
        return AccountMatching.matchByName(input.body, input.parsed.merchant, owned)
    }

    private fun matchDestination(
        owned: List<FinancialAccount>,
        source: FinancialAccount,
        input: RuleInput,
        context: RuleContext,
    ): FinancialAccount? {
        val remaining = owned.filter { it.id != source.id }
        val evidence = input.parsed.identifierEvidence.firstOrNull {
            it.role == IdentifierRole.DESTINATION
        } ?: input.parsed.identifierEvidence.firstOrNull {
            it.role == IdentifierRole.UNSPECIFIED &&
                findByEvidence(remaining, it, context) != null
        }
        if (evidence != null) {
            findByEvidence(remaining, evidence, context)?.let { return it }
            return null
        }
        return AccountMatching.matchByName(input.body, input.parsed.merchant, remaining)
    }

    private fun matchBySenderLink(
        accounts: List<FinancialAccount>,
        sender: String?,
        context: RuleContext,
    ): FinancialAccount? {
        val key = FinancialInstitutionResolver.senderKey(sender) ?: return null
        val ids = context.accountsBySenderKey[key].orEmpty()
        if (ids.isEmpty()) return null
        return accounts.filter { it.id in ids }.singleOrNull()
    }

    private fun findByEvidence(
        accounts: List<FinancialAccount>,
        evidence: ParsedIdentifierEvidence,
        context: RuleContext,
    ): FinancialAccount? {
        if (evidence.lastFour.length != 4) return null
        val accountIds = context.accountIdentifiers
            .asSequence()
            .filter {
                it.identifierType == evidence.type &&
                    it.normalizedValue == evidence.lastFour
            }
            .map { it.accountId }
            .toSet()
        val matches = accounts.filter { account ->
            account.id in accountIds &&
                AccountIdentifierCompatibility.isCompatibleTyped(account.accountType, evidence.type)
        }
        return matches.singleOrNull()
    }

    private companion object {
        val TRANSFER_TYPES = setOf(
            TransactionType.TRANSFER_OUT,
            TransactionType.TRANSFER_IN,
            TransactionType.INTERNAL_TRANSFER,
        )
    }
}

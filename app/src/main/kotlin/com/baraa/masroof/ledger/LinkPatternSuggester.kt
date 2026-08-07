package com.baraa.masroof.ledger

import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.data.repository.AccountIdentifierRepository
import com.baraa.masroof.data.repository.TransactionSmsBodyRepository
import com.baraa.masroof.transaction.FinancialTreatment

/**
 * Deterministic review suggestion from SMS patterns + learned link rules.
 * Never posts journals and never calls an LLM.
 */
data class LinkPatternSuggestion(
    val treatment: FinancialTreatment,
    val sourceAccountId: Long?,
    val destinationAccountId: Long?,
    val confidence: Int,
    val reasonAr: String,
)

class LinkPatternSuggester(
    private val identifierRepository: AccountIdentifierRepository,
    private val rules: AccountLinkRuleRepository,
    private val smsBodyRepository: TransactionSmsBodyRepository? = null,
) {
    suspend fun suggest(
        transaction: TransactionEntity,
        accounts: List<FinancialAccount>,
        smsBody: String? = null,
    ): LinkPatternSuggestion? {
        val owned = accounts.filter { it.isActive && it.isOwnedByUser && it.systemAccountKey == null }
        if (owned.isEmpty()) return null

        val body = smsBody
            ?: smsBodyRepository?.getBody(transaction.id)
        val audit = LocalTreatmentAuditor.auditTransaction(transaction, smsBody = body)
        val treatment = when {
            audit.treatment != FinancialTreatment.PENDING_REVIEW &&
                audit.treatment != FinancialTreatment.IGNORED -> audit.treatment
            transaction.financialTreatment != FinancialTreatment.PENDING_REVIEW &&
                transaction.financialTreatment != FinancialTreatment.IGNORED ->
                transaction.financialTreatment
            else -> FinancialTreatment.EXPENSE
        }
        val working = transaction.copy(financialTreatment = treatment)

        val match = AccountMatcher.match(working, owned, identifierRepository)
        val matchedAccount = match.account?.takeIf { !match.needsReview }
        val learnedSource = rules.find(working, owned, AccountLinkRuleRepository.DIRECTION_SOURCE)
        val learnedDest = rules.find(working, owned, AccountLinkRuleRepository.DIRECTION_DESTINATION)

        var sourceId: Long?
        var destId: Long?
        var reason: String
        var confidence: Int

        when {
            treatment.requiresTwoAccounts -> {
                sourceId = learnedSource?.id
                    ?: match.account?.takeIf { !match.needsReview }?.id
                    ?: institutionUnique(owned, working)?.id
                destId = learnedDest?.id
                    ?: match.destinationAccountCandidate?.id
                if (sourceId == null && destId == null) return null
                reason = when {
                    learnedSource != null || learnedDest != null -> "من تأكيد سابق لنفس المرسل/النوع"
                    match.account != null -> "من المعرف في الرسالة — أكمل الحساب الآخر"
                    else -> "من نمط الرسالة — أكمل الحسابين"
                }
                confidence = when {
                    sourceId != null && destId != null && sourceId != destId ->
                        if (learnedSource != null || learnedDest != null) 85 else 60
                    else -> 55
                }
            }
            treatment == FinancialTreatment.INCOME || treatment == FinancialTreatment.REFUND -> {
                destId = matchedAccount?.id
                    ?: learnedDest?.id
                    ?: learnedSource?.id
                    ?: institutionUnique(owned, working)?.id
                    ?: owned.singleOrNull()?.id
                if (destId == null) return null
                sourceId = null
                reason = reasonFor(matchedAccount != null, learnedDest != null || learnedSource != null, audit.reasonAr)
                confidence = confFor(matchedAccount != null, learnedDest != null || learnedSource != null, audit.confidence)
            }
            else -> {
                sourceId = matchedAccount?.id
                    ?: learnedSource?.id
                    ?: learnedDest?.id
                    ?: institutionUnique(owned, working)?.id
                    ?: owned.singleOrNull()?.id
                if (sourceId == null) return null
                destId = null
                reason = reasonFor(matchedAccount != null, learnedSource != null || learnedDest != null, audit.reasonAr)
                confidence = confFor(matchedAccount != null, learnedSource != null || learnedDest != null, audit.confidence)
            }
        }

        return LinkPatternSuggestion(
            treatment = treatment,
            sourceAccountId = sourceId,
            destinationAccountId = destId,
            confidence = confidence.coerceIn(40, 95),
            reasonAr = reason,
        )
    }

    suspend fun suggestAll(
        transactions: List<TransactionEntity>,
        accounts: List<FinancialAccount>,
    ): Map<Long, LinkPatternSuggestion> {
        val out = LinkedHashMap<Long, LinkPatternSuggestion>()
        for (tx in transactions) {
            suggest(tx, accounts)?.let { out[tx.id] = it }
        }
        return out
    }

    private fun institutionUnique(
        owned: List<FinancialAccount>,
        tx: TransactionEntity,
    ): FinancialAccount? {
        val sender = tx.originalSender?.lowercase().orEmpty()
        if (sender.isBlank()) return null
        val hits = owned.filter { acc ->
            val inst = acc.institutionName?.lowercase().orEmpty()
            val name = acc.displayName.lowercase()
            (inst.isNotBlank() && (sender.contains(inst) || inst.contains(sender.take(4)))) ||
                (name.isNotBlank() && (sender.contains(name) || name.contains(sender.take(4))))
        }
        return hits.singleOrNull()
    }

    private fun reasonFor(matched: Boolean, learned: Boolean, auditReason: String): String = when {
        matched && learned -> "من المعرف في الرسالة + تأكيد سابق"
        matched -> "من آخر 4 أرقام في الرسالة"
        learned -> "من تأكيد سابق لنفس المرسل/النوع"
        else -> "من نمط الرسالة ($auditReason)"
    }

    private fun confFor(matched: Boolean, learned: Boolean, auditConfidence: Int): Int = when {
        matched -> 90
        learned -> 85
        else -> maxOf(55, auditConfidence.coerceAtMost(75))
    }
}

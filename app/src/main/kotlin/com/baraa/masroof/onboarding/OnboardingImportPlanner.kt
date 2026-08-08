package com.baraa.masroof.onboarding

import com.baraa.masroof.data.db.DateSource
import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.data.repository.AccountIdentifierRepository
import com.baraa.masroof.data.repository.MessagePattern
import com.baraa.masroof.ledger.AccountMatcher
import com.baraa.masroof.sms.MessageTemplateEngine
import com.baraa.masroof.sms.SenderNormalizer
import com.baraa.masroof.sms.SmsMessage
import com.baraa.masroof.sms.TemplateResolutionResult
import com.baraa.masroof.sms.TemplateResolutionService

data class PatternMatchCounts(
    val matched: Int,
    val unmatched: Int,
    val total: Int,
)

data class OnboardingImportPreview(
    val totalMessages: Int,
    val matchedPatterns: Int,
    val unknown: Int,
    val willImport: Int,
)

data class OnboardingLinkBucket(
    val accountId: Long,
    val accountName: String,
    val lastFourHint: String?,
    val matchedCount: Int,
)

data class OnboardingLinkPreview(
    val byAccount: List<OnboardingLinkBucket>,
    val needsReview: Int,
)

/**
 * Pure planning helpers for pattern-first onboarding import/link previews.
 * Does not write to Room.
 */
object OnboardingImportPlanner {

    fun countForTemplate(
        templateText: String,
        messages: List<SmsMessage>,
    ): PatternMatchCounts {
        var matched = 0
        for (sms in messages) {
            val body = sms.body ?: continue
            if (MessageTemplateEngine.matches(templateText, body)) matched++
        }
        val total = messages.count { !it.body.isNullOrBlank() }
        return PatternMatchCounts(matched = matched, unmatched = (total - matched).coerceAtLeast(0), total = total)
    }

    fun messageMatchesAnyPattern(
        body: String,
        patterns: List<MessagePattern>,
        sender: String? = null,
        smsTimestamp: Long? = null,
    ): Boolean {
        return TemplateResolutionService.resolve(sender, body, smsTimestamp, patterns) is
            TemplateResolutionResult.Matched
    }

    fun countForPatterns(
        patterns: List<MessagePattern>,
        messages: List<SmsMessage>,
    ): PatternMatchCounts {
        var matched = 0
        for (sms in messages) {
            val body = sms.body ?: continue
            if (messageMatchesAnyPattern(body, patterns, sms.sender, sms.timestamp)) matched++
        }
        val total = messages.count { !it.body.isNullOrBlank() }
        return PatternMatchCounts(matched = matched, unmatched = (total - matched).coerceAtLeast(0), total = total)
    }

    fun importPreview(
        messages: List<SmsMessage>,
        patterns: List<MessagePattern>,
    ): OnboardingImportPreview {
        val counts = countForPatterns(patterns, messages)
        return OnboardingImportPreview(
            totalMessages = counts.total,
            matchedPatterns = counts.matched,
            unknown = counts.unmatched,
            willImport = counts.matched,
        )
    }

    suspend fun linkPreview(
        messages: List<SmsMessage>,
        patterns: List<MessagePattern>,
        accounts: List<FinancialAccount>,
        identifierRepository: AccountIdentifierRepository,
        accountMatcher: AccountMatcher = AccountMatcher,
    ): OnboardingLinkPreview {
        val perAccount = mutableMapOf<Long, Int>()
        var needsReview = 0
        val now = System.currentTimeMillis()
        for (sms in messages) {
            val body = sms.body ?: continue
            val outcome = TemplateResolutionService.resolve(
                sms.sender,
                body,
                sms.timestamp.takeIf { it > 0L },
                patterns,
            )
            val parsed = (outcome as? TemplateResolutionResult.Matched)?.parsed ?: continue
            if (parsed.amount == null) continue
            val stub = TransactionEntity(
                uniqueFingerprint = "onboarding-preview-${sms.id}-$now",
                smsTimestamp = sms.timestamp,
                originalSender = sms.sender,
                transactionType = parsed.transactionType,
                amount = parsed.amount,
                currency = parsed.currency,
                merchantOrBeneficiary = parsed.merchant,
                accountOrCardLastFourDigits = parsed.accountOrCardLastFourDigits,
                transactionDate = parsed.transactionDate,
                transactionTime = parsed.transactionTime,
                status = parsed.status,
                confidence = parsed.confidence,
                parsingNotes = emptyList(),
                dateSource = DateSource.FROM_SMS_METADATA,
                createdAt = now,
                updatedAt = now,
            )
            val match = accountMatcher.match(stub, accounts, identifierRepository, parsed.identifierEvidence)
            when {
                match.account != null && !match.needsReview ->
                    perAccount[match.account.id] = (perAccount[match.account.id] ?: 0) + 1
                else -> needsReview++
            }
        }
        val snapshots = identifierRepository.getActiveSnapshots()
        val buckets = accounts.map { acct ->
            val last4 = snapshots.firstOrNull { it.accountId == acct.id }?.normalizedValue
            OnboardingLinkBucket(
                accountId = acct.id,
                accountName = acct.displayName,
                lastFourHint = last4,
                matchedCount = perAccount[acct.id] ?: 0,
            )
        }
        return OnboardingLinkPreview(byAccount = buckets, needsReview = needsReview)
    }

    fun filterMessagesForSender(messages: List<SmsMessage>, normalizedSenderKey: String): List<SmsMessage> =
        messages.filter { SenderNormalizer.normalize(it.sender) == normalizedSenderKey }
}

package com.baraa.masroof.sms

import com.baraa.masroof.data.db.MessagePatternDefinitionEntity
import com.baraa.masroof.data.db.MessagePatternStatus
import com.baraa.masroof.data.repository.MessagePattern
import com.baraa.masroof.transaction.TransactionTypeTaxonomy

enum class PatternRuntimeEligibilityResult {
    ELIGIBLE,
    NOT_APPROVED,
    INACTIVE,
    DEPRECATED,
    STALE_NORMALIZATION,
    MISSING_TEMPLATE,
    INVALID_TRANSACTION_TYPE,
}

/** Single source of truth for whether a pattern may parse an SMS at runtime. */
object PatternRuntimeEligibility {
    fun evaluate(
        definition: MessagePatternDefinitionEntity,
        allowUnapprovedInactive: Boolean = false,
    ): PatternRuntimeEligibilityResult = when {
        definition.deprecatedAt != null ||
            definition.status == MessagePatternStatus.DEPRECATED ->
            PatternRuntimeEligibilityResult.DEPRECATED
        !allowUnapprovedInactive && definition.status != MessagePatternStatus.APPROVED ->
            PatternRuntimeEligibilityResult.NOT_APPROVED
        !allowUnapprovedInactive && !definition.isActive ->
            PatternRuntimeEligibilityResult.INACTIVE
        definition.normalizationVersion != NORMALIZATION_VERSION ->
            PatternRuntimeEligibilityResult.STALE_NORMALIZATION
        definition.templateText.isNullOrBlank() ->
            PatternRuntimeEligibilityResult.MISSING_TEMPLATE
        TransactionTypeTaxonomy.parse(definition.transactionType) == null ->
            PatternRuntimeEligibilityResult.INVALID_TRANSACTION_TYPE
        else -> PatternRuntimeEligibilityResult.ELIGIBLE
    }

    fun evaluate(pattern: MessagePattern): PatternRuntimeEligibilityResult =
        evaluate(pattern.definition)

    fun isEligible(definition: MessagePatternDefinitionEntity): Boolean =
        evaluate(definition) == PatternRuntimeEligibilityResult.ELIGIBLE

    fun isEligible(pattern: MessagePattern): Boolean = isEligible(pattern.definition)

    /**
     * Session-only "use once" may bypass approval/active state, but never
     * normalization, deprecation, template, or transaction-type safety.
     */
    fun isEligibleForUseOnce(definition: MessagePatternDefinitionEntity): Boolean =
        evaluate(definition, allowUnapprovedInactive = true) ==
            PatternRuntimeEligibilityResult.ELIGIBLE
}

enum class PatternFamilyRuntimeState {
    APPROVED_CURRENT,
    APPROVED_STALE,
    NOT_APPROVED,
}

fun patternFamilyRuntimeState(patterns: List<MessagePattern>): PatternFamilyRuntimeState {
    val approved = patterns.filter { it.definition.status == MessagePatternStatus.APPROVED }
    return when {
        approved.any(PatternRuntimeEligibility::isEligible) ->
            PatternFamilyRuntimeState.APPROVED_CURRENT
        approved.isNotEmpty() ->
            PatternFamilyRuntimeState.APPROVED_STALE
        else -> PatternFamilyRuntimeState.NOT_APPROVED
    }
}

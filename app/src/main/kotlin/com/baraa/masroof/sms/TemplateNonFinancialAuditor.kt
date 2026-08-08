package com.baraa.masroof.sms

import com.baraa.masroof.data.db.MessagePatternStatus
import com.baraa.masroof.data.db.PatternCanonicalField
import com.baraa.masroof.data.repository.MessagePattern
import com.baraa.masroof.transaction.TransactionType
import com.baraa.masroof.transaction.TransactionTypeTaxonomy

/**
 * Detects approved templates that look non-financial (settings, OTP, ads,
 * limit changes) so they can be safely reclassified without deleting SMS.
 */
object TemplateNonFinancialAuditor {

    data class Finding(
        val patternId: Long,
        val reasonAr: String,
        val suggestedType: TransactionType = TransactionType.NON_FINANCIAL,
        val suggestedStatus: MessagePatternStatus = MessagePatternStatus.UNKNOWN,
    )

    fun audit(pattern: MessagePattern): Finding? {
        val definition = pattern.definition
        val template = definition.templateText.orEmpty()
        val name = definition.userFriendlyName
        val type = TransactionTypeTaxonomy.parse(definition.transactionType)
        val bodyLike = listOf(template, name).joinToString("\n")

        if (MessageTypeCueCatalog.isNonFinancialCue(bodyLike)) {
            return Finding(
                patternId = definition.id,
                reasonAr = "القالب يبدو إعدادات/OTP/حد — ليس عملية مالية",
            )
        }

        val hasAmount = template.contains("{AMOUNT}", ignoreCase = true) ||
            template.contains("{TRANSACTION_AMOUNT}", ignoreCase = true) ||
            pattern.fields.any { it.canonicalField == PatternCanonicalField.TRANSACTION_AMOUNT }
        if (type != null &&
            TransactionTypeTaxonomy.isFinancial(type) &&
            TransactionTypeTaxonomy.requiresAmount(type) &&
            template.isNotBlank() &&
            !hasAmount
        ) {
            return Finding(
                patternId = definition.id,
                reasonAr = "نمط مالي معتمد بلا مبلغ عملية — يُراجع كغير مالي أو يُصحَّح",
            )
        }

        // Misclassified online purchase that is actually a limit-change notice.
        if (type == TransactionType.ONLINE_PURCHASE || type == TransactionType.PURCHASE) {
            val folded = MessageTypeCueCatalog.foldArabic(bodyLike)
            if (listOf("تغيير الحد", "الحد اليومي", "credit limit", "daily limit").any { it in folded }) {
                return Finding(
                    patternId = definition.id,
                    reasonAr = "مصنّف كشراء لكنه تغيير حد — يُعاد تصنيفه لغير مالي",
                )
            }
        }
        return null
    }

    fun auditAll(patterns: List<MessagePattern>): List<Finding> =
        patterns.mapNotNull { audit(it) }
}

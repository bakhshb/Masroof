package com.baraa.masroof.sms

import com.baraa.masroof.data.db.MessagePatternStatus
import com.baraa.masroof.data.db.PatternCanonicalField
import com.baraa.masroof.data.db.PatternFieldRole
import com.baraa.masroof.data.db.PatternValueType
import com.baraa.masroof.transaction.MoneyFlowDirection
import com.baraa.masroof.transaction.TransactionType
import com.baraa.masroof.transaction.TransactionTypeTaxonomy

/** Editable snapshot for the template editor dialog. */
data class TemplateEditDraft(
    val patternId: Long,
    val senderProfileId: Long,
    val displayName: String,
    val transactionType: TransactionType,
    val direction: MoneyFlowDirection,
    val templateText: String,
    val status: MessagePatternStatus,
    val active: Boolean,
    val fields: List<TemplateFieldDraft>,
)

data class TemplateFieldDraft(
    val placeholderToken: String,
    val canonicalField: PatternCanonicalField,
    val sourceLabel: String,
    val role: PatternFieldRole,
    val valueType: PatternValueType,
    val required: Boolean,
)

sealed class TemplateEditValidation {
    data object Ok : TemplateEditValidation()
    data class Error(val messageAr: String) : TemplateEditValidation()
}

object TemplateEditValidator {
    private val tokenPattern = Regex("""[A-Z][A-Z0-9_]*""")
    private val placeholderPattern = Regex("""\{([^{}]+)}""")
    private val suspiciousDigitRun = Regex("""(?:[0-9٠-٩][\s-]?){6,}""")

    fun validate(draft: TemplateEditDraft): TemplateEditValidation {
        if (draft.senderProfileId <= 0L) {
            return error("اختر مرسلًا صالحًا قبل الحفظ")
        }
        if (draft.displayName.isBlank()) {
            return error("اسم العرض مطلوب ولا يمكن أن يكون فارغًا")
        }
        if (draft.templateText.isBlank()) {
            return error("نص القالب مطلوب ولا يمكن أن يكون فارغًا")
        }
        val staticText = placeholderPattern.replace(draft.templateText, " ")
        if (suspiciousDigitRun.containsMatchIn(staticText)) {
            return error("يبدو أن نص القالب يحتوي رقم حساب/بطاقة/هاتف شخصيًا؛ استبدله بحقل نائب")
        }

        val rawTokens = placeholderPattern.findAll(draft.templateText)
            .map { it.groupValues[1].trim() }
            .toList()
        if (draft.templateText.count { it == '{' } != rawTokens.size ||
            draft.templateText.count { it == '}' } != rawTokens.size
        ) {
            return error("أقواس الحقول في نص القالب غير متوازنة")
        }
        val invalidTemplateToken = rawTokens.firstOrNull { !tokenPattern.matches(it) }
        if (invalidTemplateToken != null) {
            return error("الحقل {$invalidTemplateToken} غير صالح؛ استخدم أحرفًا إنجليزية كبيرة وأرقامًا وشرطة سفلية")
        }

        val definitionTokens = draft.fields.map { it.placeholderToken.trim() }
        val invalidDefinitionToken = definitionTokens.firstOrNull { !tokenPattern.matches(it) }
        if (invalidDefinitionToken != null) {
            return error("رمز الحقل '$invalidDefinitionToken' غير صالح؛ يجب أن يكون بصيغة UPPER_SNAKE_CASE")
        }
        val duplicate = definitionTokens.groupingBy { it }.eachCount().entries.firstOrNull { it.value > 1 }
        if (duplicate != null) {
            return error("رمز الحقل ${duplicate.key} مكرر؛ يجب تعريف كل رمز مرة واحدة")
        }
        val templateTokens = rawTokens.toSet()
        val definedTokens = definitionTokens.toSet()
        val missing = templateTokens - definedTokens
        if (missing.isNotEmpty()) {
            return error("أضف تعريفًا للحقول التالية: ${missing.joinToString { "{$it}" }}")
        }
        val unused = definedTokens - templateTokens
        if (unused.isNotEmpty()) {
            return error("احذف تعريفات الحقول غير الموجودة في القالب: ${unused.joinToString()}")
        }

        draft.fields.forEach { field ->
            if (field.sourceLabel.isBlank()) {
                return error("أدخل تسمية مصدر للحقل ${field.placeholderToken}")
            }
            val expected = expectedValueType(field.canonicalField)
            if (field.valueType != expected) {
                return error(
                    "الحقل ${field.placeholderToken} من النوع ${field.canonicalField.name} " +
                        "ويجب أن تكون قيمة نوعه ${expected.name}",
                )
            }
            identifierRoleError(field)?.let { return error(it) }
        }

        val derivedDirection = derivedDirection(draft.transactionType)
        if (draft.transactionType != TransactionType.OTHER_FINANCIAL &&
            draft.direction != derivedDirection
        ) {
            return error(
                "اتجاه الأموال لا يطابق نوع العملية؛ الاتجاه المطلوب هو " +
                    TransactionTypeTaxonomy.directionLabelAr(derivedDirection),
            )
        }
        if (draft.active && draft.status != MessagePatternStatus.APPROVED) {
            return error("لا يمكن تفعيل قالب غير معتمد؛ اعتمده أولًا أو أوقف التفعيل")
        }
        if (draft.status == MessagePatternStatus.APPROVED &&
            TransactionTypeTaxonomy.requiresAmount(draft.transactionType) &&
            !hasAmountSemantics(draft)
        ) {
            return error("لا يمكن اعتماد قالب مالي بدون تعريف حقل مبلغ العملية")
        }
        return TemplateEditValidation.Ok
    }

    fun hasAmountSemantics(draft: TemplateEditDraft): Boolean {
        return draft.fields.any {
            it.canonicalField == PatternCanonicalField.TRANSACTION_AMOUNT &&
                it.valueType == PatternValueType.MONEY &&
                "{${it.placeholderToken}}" in draft.templateText
        }
    }

    fun derivedDirection(type: TransactionType): MoneyFlowDirection =
        TransactionTypeTaxonomy.directionOf(type)

    private fun expectedValueType(field: PatternCanonicalField): PatternValueType = when (field) {
        PatternCanonicalField.TRANSACTION_AMOUNT,
        PatternCanonicalField.AVAILABLE_BALANCE,
        PatternCanonicalField.CARD_AMOUNT_DUE,
        -> PatternValueType.MONEY
        PatternCanonicalField.CURRENCY -> PatternValueType.CURRENCY_CODE
        PatternCanonicalField.TRANSACTION_DATE -> PatternValueType.DATE
        PatternCanonicalField.TRANSACTION_TIME -> PatternValueType.TIME
        PatternCanonicalField.ACCOUNT_LAST4,
        PatternCanonicalField.SOURCE_ACCOUNT_LAST4,
        PatternCanonicalField.DESTINATION_ACCOUNT_LAST4,
        PatternCanonicalField.CREDIT_CARD_LAST4,
        PatternCanonicalField.DEBIT_CARD_LAST4,
        PatternCanonicalField.IBAN_LAST4,
        PatternCanonicalField.SOURCE_IBAN_LAST4,
        PatternCanonicalField.DESTINATION_IBAN_LAST4,
        PatternCanonicalField.WALLET_LAST4,
        -> PatternValueType.LAST4
        PatternCanonicalField.TRANSACTION_REFERENCE -> PatternValueType.REFERENCE
        else -> PatternValueType.TEXT
    }

    private fun identifierRoleError(field: TemplateFieldDraft): String? {
        val expected = when (field.canonicalField) {
            PatternCanonicalField.SOURCE_ACCOUNT_LAST4,
            PatternCanonicalField.SOURCE_IBAN_LAST4,
            -> PatternFieldRole.SOURCE
            PatternCanonicalField.DESTINATION_ACCOUNT_LAST4,
            PatternCanonicalField.DESTINATION_IBAN_LAST4,
            -> PatternFieldRole.DESTINATION
            PatternCanonicalField.ACCOUNT_LAST4,
            PatternCanonicalField.CREDIT_CARD_LAST4,
            PatternCanonicalField.DEBIT_CARD_LAST4,
            PatternCanonicalField.IBAN_LAST4,
            PatternCanonicalField.WALLET_LAST4,
            -> null // Generic identifiers may be SOURCE, DESTINATION, or context.
            else -> null
        }
        return if (expected != null && field.role != expected) {
            "دور المعرّف ${field.placeholderToken} يجب أن يكون ${expected.name}"
        } else {
            null
        }
    }

    private fun error(message: String) = TemplateEditValidation.Error(message)
}

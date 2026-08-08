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
    data class Error(
        val messageAr: String,
        val digitRun: DigitRunFinding? = null,
    ) : TemplateEditValidation()
}

/** Debug-safe location of a suspicious digit run in the template text. */
data class DigitRunFinding(
    /** 1-based line number within the template text. */
    val lineNumber: Int,
    /** The offending digit run as it appears in the template (digits + spaces/hyphens). */
    val rawMatch: String,
    /** The label text of the line (after the first colon, or the whole line if label-only). */
    val lineLabel: String,
    /** Suggested placeholder token inferred from the label, e.g. {DATE} / {AMOUNT} / {ACCOUNT_LAST4}. */
    val suggestedPlaceholder: String,
)

object TemplateEditValidator {
    private val tokenPattern = Regex("""[A-Z][A-Z0-9_]*""")
    // Escaped closing brace for Android ICU regex portability (see PatternStructure.PLACEHOLDER).
    private val placeholderPattern = Regex("""\{([^{}]+)\}""")
    private val suspiciousDigitRun = Regex("""(?:[0-9٠-٩][\s-]?){6,}""")

    /**
     * Locate the first 6+ digit run in the static text of [templateText]
     * (placeholders stripped). Returns line number, the raw run, the line label
     * and a suggested placeholder inferred from the label, or null if clean.
     */
    fun findSuspiciousDigitRun(templateText: String): DigitRunFinding? {
        if (templateText.isBlank()) return null
        val staticText = placeholderPattern.replace(templateText, " ")
        val match = suspiciousDigitRun.find(staticText) ?: return null
        val lineStart = staticText.lastIndexOf('\n', match.range.first - 1) + 1
        val lineEnd = staticText.indexOf('\n', match.range.last + 1)
            .takeIf { it >= 0 } ?: staticText.length
        val staticLine = staticText.substring(lineStart, lineEnd).trim()
        val lineNumber = staticText.substring(0, lineStart).count { it == '\n' } + 1
        val rawMatch = staticText.substring(match.range.first, match.range.last + 1)
        val label = staticLine.substringBefore(':').trim().ifBlank { staticLine }
        return DigitRunFinding(
            lineNumber = lineNumber,
            rawMatch = rawMatch,
            lineLabel = label,
            suggestedPlaceholder = suggestPlaceholderFor(label),
        )
    }

    /**
     * Infer a sensible placeholder token from a label. Conservative mapping
     * covering the labels the corpus / manual drafts actually produce.
     */
    fun suggestPlaceholderFor(label: String): String {
        val shape = labelShape(label)
        return when {
            shape.hasAccount -> "{ACCOUNT_LAST4}"
            shape.hasDateTime -> "{DATE}"
            shape.hasAmount -> "{AMOUNT}"
            shape.hasReference -> "{TRANSACTION_ID}"
            shape.hasMerchant -> "{MERCHANT}"
            else -> "{VALUE}"
        }
    }

    private data class LabelShape(
        val hasAccount: Boolean,
        val hasDateTime: Boolean,
        val hasAmount: Boolean,
        val hasReference: Boolean,
        val hasMerchant: Boolean,
    )

    private fun labelShape(label: String): LabelShape {
        val folded = MessageTypeCueCatalog.foldArabic(label)
        val hasAmount = com.baraa.masroof.transaction.MonetaryFieldClassifier
            .isTransactionAmount(label)
        val hasDateTime = folded == "في" || folded == "on" ||
            folded.contains("تاريخ") || folded.contains("date")
        val hasAccount = folded == "من" || folded == "حساب" || folded == "الحساب" ||
            folded == "رقم الحساب" || folded.contains("حساب") || folded.contains("account") ||
            folded == "الي" || folded == "إلى" || folded == "الى" ||
            folded.contains("بطاقه") || folded.contains("بطاقة") || folded.contains("card")
        val hasReference = folded == "ref" || folded.contains("مرجع") ||
            folded.contains("reference") || folded.contains("رقم العمليه") ||
            folded.contains("رقم المعامله")
        val hasMerchant = folded == "لدى" || folded == "at" ||
            folded == "التاجر" || folded.contains("تاجر") || folded.contains("merchant")
        return LabelShape(hasAccount, hasDateTime, hasAmount, hasReference, hasMerchant)
    }

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
        val digitRun = findSuspiciousDigitRun(draft.templateText)
        if (digitRun != null) {
            return error(
                "يبدو أن نص القالب يحتوي رقم حساب/بطاقة/هاتف شخصيًا في السطر " +
                    "${digitRun.lineNumber} (${digitRun.rawMatch})؛ استبدله بحقل نائب",
                digitRun = digitRun,
            )
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
            -> null
            else -> null
        }
        return if (expected != null && field.role != expected) {
            "دور المعرّف ${field.placeholderToken} يجب أن يكون ${expected.name}"
        } else {
            null
        }
    }

    private fun error(message: String, digitRun: DigitRunFinding? = null) = TemplateEditValidation.Error(message, digitRun)
}

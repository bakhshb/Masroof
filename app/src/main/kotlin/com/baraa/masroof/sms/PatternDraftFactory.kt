package com.baraa.masroof.sms

import com.baraa.masroof.accounts.AccountSmsAnalyzer
import com.baraa.masroof.data.db.MessagePatternStatus
import com.baraa.masroof.data.db.PatternCanonicalField
import com.baraa.masroof.data.db.PatternFieldRole
import com.baraa.masroof.data.db.PatternValueType
import com.baraa.masroof.transaction.LineBasedFieldParser
import com.baraa.masroof.transaction.MoneyFlowDirection
import com.baraa.masroof.transaction.TransactionType
import com.baraa.masroof.transaction.TransactionTypeTaxonomy

/**
 * In-memory, UNPERSISTED template draft built deterministically from a
 * single SMS. Carries everything the [com.baraa.masroof.ui.senders.TemplateEditorScreen]
 * needs to present a confirmation screen before a single Room row is written.
 *
 * `templateEditDraft.patternId == 0L` marks this as a fresh (not-yet-saved)
 * draft. The repository persists it exactly once on user confirmation via
 * [com.baraa.masroof.data.repository.MessagePatternRepository.createPatternFromDraft].
 */
data class PatternDraft(
    val templateEditDraft: TemplateEditDraft,
    val sanitizedExample: String,
    val senderProfileId: Long,
    /** Underlying SMS body hash (debug only; never raw SMS). */
    val bodyHash: String,
)

sealed interface PatternDraftResult {
    /** Structure is valid and the transaction type is confidently known. */
    data class Ready(val draft: PatternDraft) : PatternDraftResult
    /** Structure is valid but the transaction type is uncertain; the editor
     *  must let the user choose a type before approval. */
    data class NeedsTypeSelection(val draft: PatternDraft) : PatternDraftResult
    /** OTP / non-financial / bank-service / empty — never a transaction template. */
    data class NonFinancial(val reason: String) : PatternDraftResult
    /** A CORE stage threw; the user should pick a different message. */
    data class Failed(val stage: String, val detail: String = "") : PatternDraftResult
}

/**
 * Compact, privacy-safe summary of one SMS for the manual financial picker.
 * Never carries raw SMS. Used only to render a short row in the picker.
 */
data class FinancialMessageSummary(
    val sms: SmsMessage,
    val typeLabel: String,
    val merchantOrBeneficiary: String?,
    val amount: String?,
    val currency: String?,
    val date: String?,
    val maskedLast4: String?,
    /** Short sanitized preview when extraction is incomplete. */
    val fallbackPreview: String?,
    val isUnclassifiedFinancial: Boolean,
)

/**
 * Single deterministic core that turns one SMS into an editable template draft.
 *
 * Reuses the SAME canonical components as [PatternDiscoveryService]:
 *  - [MessageTypeCueCatalog] (type / direction / channel)
 *  - [MessageTemplateEngine] (structural template + placeholders)
 *  - [CanonicalMessageNormalizer] (label normalization for field de-dup)
 *  - [CanonicalSmsFieldExtractor] (strict field extraction for the summary)
 *  - [PatternDiscoveryService.suggestFields] (label -> canonical field map)
 *
 * It does NOT introduce a new parser or normalizer, and it does NOT depend on
 * the batch discovery lifecycle (no [PatternDiscoveryService.discoverSafely]
 * call). Automatic and manual creation share the same structural building
 * blocks without being coupled to the same control flow.
 */
object PatternDraftFactory {

    fun fromSms(
        sms: SmsMessage,
        senderProfileId: Long,
    ): PatternDraftResult {
        val body = sms.body.orEmpty()
        if (body.isBlank()) return PatternDraftResult.NonFinancial("رسالة فارغة")
        if (SmsStructureNormalizer.looksLikeOtpOrMarketing(body)) {
            return PatternDraftResult.NonFinancial("رسالة تحقق أو تسويق")
        }
        val cue = try {
            MessageTypeCueCatalog.detect(body)
        } catch (failure: Throwable) {
            if (failure is VirtualMachineError) throw failure
            return PatternDraftResult.Failed("TYPE_CUE", debugDetail(failure))
        }
        if (cue.transactionType == TransactionType.NON_FINANCIAL ||
            MessageTypeCueCatalog.isNonFinancialCue(body)
        ) {
            return PatternDraftResult.NonFinancial(
                cue.displayNameAr.ifBlank { "رسالة غير مالية" },
            )
        }
        val built = try {
            MessageTemplateEngine.buildFromSms(body)
        } catch (failure: Throwable) {
            if (failure is VirtualMachineError) throw failure
            return PatternDraftResult.Failed("TEMPLATE_BUILD", debugDetail(failure))
        }
        val templateText = built.templateText
        if (templateText.isBlank()) {
            return PatternDraftResult.Failed("TEMPLATE_BUILD")
        }

        val fields = try {
            buildFieldDrafts(body)
        } catch (failure: Throwable) {
            if (failure is VirtualMachineError) throw failure
            return PatternDraftResult.Failed("FIELD_EXTRACT", debugDetail(failure))
        }

        val resolvedType = built.transactionType ?: cue.transactionType
        val direction = TransactionTypeTaxonomy.parseDirection(
            built.direction ?: cue.direction,
            resolvedType,
        )
        val displayName = built.displayName.ifBlank {
            cue.displayNameAr.ifBlank { "نمط رسالة" }
        }
        val draft = TemplateEditDraft(
            patternId = 0L,
            senderProfileId = senderProfileId,
            displayName = displayName,
            transactionType = resolvedType ?: TransactionType.OTHER_FINANCIAL,
            direction = direction,
            templateText = templateText,
            status = MessagePatternStatus.UNKNOWN,
            active = false,
            fields = fields,
        )
        val sanitized = AccountSmsAnalyzer.safeSanitizedPreview(
            body, maxChars = 240, preserveNewlines = true,
        ) ?: "(تعذرت المعاينة)"
        val pattern = PatternDraft(
            templateEditDraft = draft,
            sanitizedExample = sanitized,
            senderProfileId = senderProfileId,
            bodyHash = safeHash(body),
        )
        return when {
            resolvedType == null || resolvedType == TransactionType.OTHER_FINANCIAL ->
                PatternDraftResult.NeedsTypeSelection(pattern)
            else -> PatternDraftResult.Ready(pattern)
        }
    }

    /**
     * Build a compact, privacy-safe summary for the financial picker.
     * Excludes OTP / non-financial / bank-service messages (returns null).
     */
    fun summarize(sms: SmsMessage): FinancialMessageSummary? {
        val body = sms.body.orEmpty()
        if (body.isBlank()) return null
        if (SmsStructureNormalizer.looksLikeOtpOrMarketing(body)) return null
        val cue = MessageTypeCueCatalog.detect(body)
        if (cue.transactionType == TransactionType.NON_FINANCIAL ||
            MessageTypeCueCatalog.isNonFinancialCue(body)
        ) {
            return null
        }
        val fields = CanonicalSmsFieldExtractor.extract(body)
        val merchant = fields.values[PatternCanonicalField.MERCHANT]
            ?: fields.values[PatternCanonicalField.BENEFICIARY]
        val amount = fields.amount?.toPlainString()
        val currency = fields.currency?.name
        val date = fields.date?.toString()
        val last4 = listOf(
            PatternCanonicalField.CREDIT_CARD_LAST4,
            PatternCanonicalField.DEBIT_CARD_LAST4,
            PatternCanonicalField.ACCOUNT_LAST4,
            PatternCanonicalField.IBAN_LAST4,
            PatternCanonicalField.WALLET_LAST4,
        ).firstNotNullOfOrNull { fields.values[it] }
        val maskedLast4 = last4?.let { "••••$it" }
        val typeLabel = cue.displayNameAr.ifBlank {
            cue.transactionType?.let { TransactionTypeTaxonomy.labelAr(it) }
                ?: "رسالة مالية غير مصنفة"
        }
        val hasAnyExtraction = merchant != null || amount != null || maskedLast4 != null
        val fallbackPreview = if (hasAnyExtraction) null else {
            AccountSmsAnalyzer.safeSanitizedPreview(
                body, maxChars = 80, preserveNewlines = false,
            ) ?: ""
        }
        return FinancialMessageSummary(
            sms = sms,
            typeLabel = typeLabel,
            merchantOrBeneficiary = merchant?.trim()?.takeIf { it.isNotBlank() },
            amount = amount,
            currency = currency,
            date = date,
            maskedLast4 = maskedLast4,
            fallbackPreview = fallbackPreview,
            isUnclassifiedFinancial = cue.transactionType == null ||
                cue.transactionType == TransactionType.OTHER_FINANCIAL,
        )
    }

    private fun buildFieldDrafts(body: String): List<TemplateFieldDraft> {
        val lines = LineBasedFieldParser.splitLines(body)
        val suggested = PatternDiscoveryService.suggestFields(lines)
        return suggested.map { field ->
            val token = TemplateResolutionService.defaultPlaceholder(field.canonicalField)
            TemplateFieldDraft(
                placeholderToken = token,
                canonicalField = field.canonicalField,
                sourceLabel = field.sourceLabel,
                role = field.role,
                valueType = field.valueType,
                required = field.required,
            )
        }
    }

    /**
     * DEBUG-only, SMS-free throwable detail for the manual draft [PatternDraftResult.Failed].
     * For NoClassDefFoundError this surfaces the missing class FQN so an Android
     * packaging/class-loading failure can be diagnosed without touching parsing.
     * Returns "" in release.
     */
    private fun debugDetail(throwable: Throwable): String {
        if (!com.baraa.masroof.BuildConfig.DEBUG) return ""
        val msg = throwable.message?.trim().orEmpty().take(200)
        val cause = throwable.cause
        val causePart = if (cause != null) {
            " cause=${cause.javaClass.simpleName.ifBlank { cause.javaClass.name }}${(cause.message?.trim() ?: "").takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""}"
        } else ""
        val frame = throwable.stackTrace.firstOrNull()?.let { f ->
            val where = f.fileName?.let { n -> "($n${if (f.lineNumber > 0) ":${f.lineNumber}" else ""})" } ?: ""
            " at=${f.className.substringAfterLast('.')}.${f.methodName}$where"
        } ?: ""
        return listOfNotNull(msg.ifBlank { null }?.let { "msg=$it" }, causePart.takeIf { it.isNotBlank() }, frame.takeIf { it.isNotBlank() }).joinToString(" ")
    }

    private fun safeHash(value: String): String = runCatching {
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(12)
    }.getOrElse {
        value.hashCode().toUInt().toString(16).padStart(8, '0').take(12)
    }
}
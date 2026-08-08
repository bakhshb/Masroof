package com.baraa.masroof.sms

import com.baraa.masroof.data.db.AccountIdentifierType
import com.baraa.masroof.data.db.MessagePatternDefinitionEntity
import com.baraa.masroof.data.db.MessagePatternStatus
import com.baraa.masroof.data.db.PatternCanonicalField
import com.baraa.masroof.data.db.PatternFieldDefinitionEntity
import com.baraa.masroof.data.db.PatternFieldRole
import com.baraa.masroof.data.repository.MessagePattern
import com.baraa.masroof.transaction.Currency
import com.baraa.masroof.transaction.IdentifierRole
import com.baraa.masroof.transaction.ParsedIdentifierEvidence
import com.baraa.masroof.transaction.ParsedTransaction
import com.baraa.masroof.transaction.TransactionStatus
import com.baraa.masroof.transaction.TransactionType
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

sealed class TemplateResolutionResult {
    data class Matched(
        val pattern: MessagePattern,
        val parsed: ParsedTransaction,
        val extractedValues: Map<String, String>,
        val score: Int,
    ) : TemplateResolutionResult()

    data class Unmatched(val reason: Reason = Reason.NO_APPROVED_MATCH) : TemplateResolutionResult() {
        enum class Reason {
            EMPTY_BODY,
            NO_APPROVED_TEMPLATES,
            NO_APPROVED_MATCH,
            INVALID_TEMPLATE,
            LOOKUP_FAILED,
        }
    }

    data class Ambiguous(val candidates: List<MessagePattern>) : TemplateResolutionResult()
}

data class ApprovedTemplateMatchAttempt(
    val templateId: Long,
    val displayName: String,
    val transactionType: String?,
    val canonicalKey: String,
    val canonicalSignature: String,
    val historicalMessageCount: Int,
    val active: Boolean,
    val approved: Boolean,
    val requiredPlaceholders: List<String>,
    val optionalPlaceholders: List<String>,
    val eligible: Boolean,
    val eligibilityFailure: String? = null,
    val match: TemplateMatcher.MatchResult? = null,
)

data class ApprovedTemplateMatchDiagnostics(
    val smsStructuralSignature: String,
    val attempts: List<ApprovedTemplateMatchAttempt>,
) {
    val primaryFailure: String
        get() {
            if (attempts.none { it.eligible }) {
                return attempts.firstNotNullOfOrNull { it.eligibilityFailure }
                    ?: "NO_APPROVED_TEMPLATES"
            }
            return attempts.asSequence()
                .filter { it.eligible }
                .mapNotNull { it.match }
                .maxByOrNull { it.score }
                ?.failureReason
                ?.name
                ?: "NO_APPROVED_MATCH"
        }
}

/**
 * Canonical production and preview resolver. Only effective, active APPROVED
 * typed templates participate; legacy signatures and deprecated rows never do.
 */
object TemplateResolutionService {
    /**
     * Explain deterministic matching without logging the raw SMS body.
     * The structural signature redacts dynamic values.
     */
    fun diagnose(
        body: String?,
        patterns: List<MessagePattern>,
        allowOncePatternIds: Set<Long> = emptySet(),
    ): ApprovedTemplateMatchDiagnostics {
        val attempts = patterns.map { pattern ->
            val definition = pattern.definition
            val approved = definition.status == MessagePatternStatus.APPROVED
            val useOnce = definition.id in allowOncePatternIds
            val versionOk = definition.normalizationVersion == NORMALIZATION_VERSION
            val failure = when {
                !approved && !useOnce -> "NOT_APPROVED"
                !definition.isActive && !useOnce -> "INACTIVE"
                definition.deprecatedAt != null -> "DEPRECATED"
                !versionOk -> "STALE_NORMALIZATION"
                definition.templateText.isNullOrBlank() -> "MISSING_TEMPLATE_TEXT"
                else -> null
            }
            ApprovedTemplateMatchAttempt(
                templateId = definition.id,
                displayName = definition.userFriendlyName,
                transactionType = definition.transactionType,
                canonicalKey = definition.canonicalKey,
                canonicalSignature = definition.normalizedSignature,
                historicalMessageCount = definition.exampleCount,
                active = definition.isActive,
                approved = definition.status == MessagePatternStatus.APPROVED,
                requiredPlaceholders = pattern.fields
                    .filter { it.required }
                    .map { it.placeholderToken.ifBlank { defaultPlaceholder(it.canonicalField) } },
                optionalPlaceholders = pattern.fields
                    .filterNot { it.required }
                    .map { it.placeholderToken.ifBlank { defaultPlaceholder(it.canonicalField) } },
                eligible = failure == null,
                eligibilityFailure = failure,
                match = if (failure == null) {
                    TemplateMatcher.match(definition.templateText, body, pattern.anchors)
                } else {
                    null
                },
            )
        }
        return ApprovedTemplateMatchDiagnostics(
            smsStructuralSignature = SmsStructureNormalizer.signatureFromBody(body.orEmpty()),
            attempts = attempts,
        )
    }

    fun resolve(
        sender: String?,
        body: String?,
        smsTimestampMillis: Long?,
        patterns: List<MessagePattern>,
        /** Session-only candidate pattern IDs (Use Once) — never persisted as APPROVED. */
        allowOncePatternIds: Set<Long> = emptySet(),
    ): TemplateResolutionResult {
        if (body.isNullOrBlank()) {
            return TemplateResolutionResult.Unmatched(TemplateResolutionResult.Unmatched.Reason.EMPTY_BODY)
        }
        val effective = patterns.filter { pattern ->
            val definition = pattern.definition
            val versionOk = definition.normalizationVersion == NORMALIZATION_VERSION
            val approved = versionOk &&
                definition.status == MessagePatternStatus.APPROVED &&
                definition.isActive &&
                definition.deprecatedAt == null
            val useOnce = definition.id in allowOncePatternIds &&
                definition.deprecatedAt == null &&
                !definition.templateText.isNullOrBlank()
            approved || useOnce
        }
        if (effective.isEmpty()) {
            return TemplateResolutionResult.Unmatched(
                TemplateResolutionResult.Unmatched.Reason.NO_APPROVED_TEMPLATES,
            )
        }

        // Step 1: deterministic canonical signature lookup. Signature is the
        // primary identity: a pattern whose stored signature equals the
        // runtime body signature is the strongest possible match and is
        // accepted regardless of whether its template uses the same regex
        // surface as the body. Template-only rows are excluded here —
        // they fall through to template matching below.
        val runtimeSignature = SmsStructureNormalizer.signatureFromBody(body)
        val signatureHits: List<Pair<MessagePattern, TemplateMatcher.MatchResult>> =
            effective.mapNotNull { pattern ->
                val signature = pattern.definition.normalizedSignature
                    .substringBefore("#revision:")
                if (signature.isBlank() ||
                    pattern.definition.templateText.isNullOrBlank()
                ) {
                    null
                } else if (signature == runtimeSignature) {
                    val matchResult = TemplateMatcher.match(
                        pattern.definition.templateText,
                        body,
                        pattern.anchors,
                    )
                    if (matchResult.matched) pattern to matchResult
                    else {
                        // Signature equal but template cannot extract: use the
                        // signature-only form so field extraction still succeeds
                        // via the pattern's stored fields.
                        pattern to syntheticMatchResult(matchResult, pattern)
                    }
                } else {
                    null
                }
            }
        if (signatureHits.size > 1) {
            // Two distinct templates with the same canonical signature are a
            // real conflict. Surface as AMBIGUOUS so the user resolves it,
            // not as a hidden highest-score guess.
            return TemplateResolutionResult.Ambiguous(signatureHits.map { it.first })
        }
        if (signatureHits.size == 1) {
            val pair = signatureHits.single()
            val parsed = extract(sender, body, smsTimestampMillis, pair.first, pair.second.values)
            return TemplateResolutionResult.Matched(
                pair.first,
                parsed,
                pair.second.values,
                pair.second.score,
            )
        }

        // Step 2: legacy signature-only patterns (no templateText). These
        // rows exist from migrations 17→21 and match purely by signature.
        val legacySignatureHit: MessagePattern? = effective.firstOrNull { pattern ->
            val signature = pattern.definition.normalizedSignature
                .substringBefore("#revision:")
            signature == runtimeSignature &&
                pattern.definition.templateText.isNullOrBlank()
        }
        if (legacySignatureHit != null) {
            val parsed = extract(
                sender,
                body,
                smsTimestampMillis,
                legacySignatureHit,
                emptyMap(),
            )
            return TemplateResolutionResult.Matched(
                legacySignatureHit,
                parsed,
                emptyMap(),
                score = 100,
            )
        }

        // Step 3: template-only matching (anchors + regex). Used when no
        // exact signature match was found. Required anchors must still hold.
        val hits = effective.mapNotNull { pattern ->
            val template = pattern.definition.templateText?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            TemplateMatcher.match(template, body, pattern.anchors).takeIf { it.matched }?.let { pattern to it }
        }
        if (hits.isEmpty()) {
            return TemplateResolutionResult.Unmatched(TemplateResolutionResult.Unmatched.Reason.NO_APPROVED_MATCH)
        }
        if (hits.any { TransactionType.values().none { type -> type.name == it.first.definition.transactionType } }) {
            return TemplateResolutionResult.Unmatched(TemplateResolutionResult.Unmatched.Reason.INVALID_TEMPLATE)
        }

        // Same signature wins among the template-matched candidates.
        val exactHits = hits.filter {
            it.first.definition.normalizedSignature.substringBefore("#revision:") == runtimeSignature
        }
        val candidates = if (exactHits.isNotEmpty()) exactHits else hits
        val ranked = candidates.sortedWith(
            compareByDescending<Pair<MessagePattern, TemplateMatcher.MatchResult>> { it.second.score }
                .thenByDescending { it.first.definition.version },
        )
        if (ranked.size > 1) {
            val margin = ranked[0].second.score - ranked[1].second.score
            // Matching variants are not interchangeable. A close contender is
            // an explicit review case, never a hidden highest-score guess.
            if (margin < 3) return TemplateResolutionResult.Ambiguous(ranked.map { it.first })
        }
        val best = ranked.first()
        val parsed = extract(sender, body, smsTimestampMillis, best.first, best.second.values)
        return TemplateResolutionResult.Matched(best.first, parsed, best.second.values, best.second.score)
    }

    private fun syntheticMatchResult(
        original: TemplateMatcher.MatchResult,
        pattern: MessagePattern,
    ): TemplateMatcher.MatchResult {
        // Build a synthetic captured-values map from the pattern's stored
        // fields so extract() still has placeholder values to work with.
        val captured = pattern.fields.associate { field ->
            val token = field.placeholderToken.ifBlank { defaultPlaceholder(field.canonicalField) }
            token to ""
        }
        return TemplateMatcher.MatchResult(
            matched = true,
            values = captured,
            score = original.score + 5,
        )
    }

    private fun extract(
        sender: String?,
        body: String,
        smsTimestampMillis: Long?,
        pattern: MessagePattern,
        values: Map<String, String>,
    ): ParsedTransaction {
        val type = pattern.definition.transactionType
            ?.let { runCatching { TransactionType.valueOf(it) }.getOrNull() }
            ?: TransactionType.OTHER_FINANCIAL
        var amount: BigDecimal? = null
        var currency: Currency? = null
        var merchant: String? = null
        var date: LocalDate? = null
        var time: LocalTime? = null
        val evidence = mutableListOf<ParsedIdentifierEvidence>()
        val notes = mutableListOf("template_driven:${pattern.definition.id}")
        val missing = mutableListOf<String>()

        pattern.fields.forEach { field ->
            val token = field.placeholderToken.trim().ifBlank { defaultPlaceholder(field.canonicalField) }
            val value = values[token]?.trim()
            if (value.isNullOrEmpty()) {
                if (field.required) missing += field.canonicalField.name.lowercase(Locale.ROOT)
                return@forEach
            }
            when (field.canonicalField) {
                PatternCanonicalField.TRANSACTION_AMOUNT -> amount = parseMoney(value)
                PatternCanonicalField.CURRENCY -> currency = parseCurrency(value)
                PatternCanonicalField.MERCHANT,
                PatternCanonicalField.BENEFICIARY,
                -> if (merchant == null) merchant = value.take(80)
                PatternCanonicalField.TRANSACTION_DATE -> date = parseDate(value)
                PatternCanonicalField.TRANSACTION_TIME -> time = parseTime(value)
                PatternCanonicalField.ACCOUNT_LAST4,
                PatternCanonicalField.SOURCE_ACCOUNT_LAST4,
                PatternCanonicalField.DESTINATION_ACCOUNT_LAST4,
                PatternCanonicalField.CREDIT_CARD_LAST4,
                PatternCanonicalField.DEBIT_CARD_LAST4,
                PatternCanonicalField.IBAN_LAST4,
                PatternCanonicalField.SOURCE_IBAN_LAST4,
                PatternCanonicalField.DESTINATION_IBAN_LAST4,
                PatternCanonicalField.WALLET_LAST4,
                -> identifierEvidence(field, value)?.let(evidence::add)
                PatternCanonicalField.AVAILABLE_BALANCE,
                PatternCanonicalField.CARD_AMOUNT_DUE,
                -> notes += "context_field:${field.canonicalField}"
                else -> Unit
            }
        }

        // Fallback: even without explicit field definitions, the captured
        // values map already carries typed amounts/dates/identifiers via
        // the placeholder token names. Use them so the round-trip test and
        // any signature-only pattern still produces an extracted transaction.
        if (amount == null) amount = parseMoney(values["AMOUNT"].orEmpty())
            ?: parseMoney(values["TRANSACTION_AMOUNT"].orEmpty())
        if (currency == null) currency = parseCurrency(values["CURRENCY"].orEmpty())
            ?: parseCurrency(body)
        if (merchant == null) merchant = values["MERCHANT"]?.takeIf { it.isNotBlank() }
            ?: values["BENEFICIARY"]?.takeIf { it.isNotBlank() }
        if (date == null) date = parseDate(values["DATE"].orEmpty())
        if (time == null) time = parseTime(values["TIME"].orEmpty())
        listOf(
            "CREDIT_CARD_LAST4" to IdentifierRole.UNSPECIFIED,
            "DEBIT_CARD_LAST4" to IdentifierRole.UNSPECIFIED,
            "ACCOUNT_LAST4" to IdentifierRole.UNSPECIFIED,
            "SOURCE_ACCOUNT_LAST4" to IdentifierRole.SOURCE,
            "DESTINATION_ACCOUNT_LAST4" to IdentifierRole.DESTINATION,
            "WALLET_LAST4" to IdentifierRole.UNSPECIFIED,
        ).forEach { (token, role) ->
            if (evidence.none { it.lastFour == values[token] && it.role == role }) {
                val raw = values[token]?.trim().orEmpty()
                if (raw.isNotEmpty()) {
                    val last4 = Regex("""(?<!\d)(\d{4})(?!\d)""").find(raw)?.groupValues?.get(1)
                    if (last4 != null) {
                        val type = when (token) {
                            "CREDIT_CARD_LAST4" -> AccountIdentifierType.CREDIT_CARD_LAST4
                            "DEBIT_CARD_LAST4" -> AccountIdentifierType.DEBIT_CARD_LAST4
                            "WALLET_LAST4" -> AccountIdentifierType.WALLET_LAST4
                            else -> AccountIdentifierType.ACCOUNT_LAST4
                        }
                        evidence += ParsedIdentifierEvidence(type, last4, role, 90, "template:$token")
                    }
                }
            }
        }

        if (amount == null && type != TransactionType.NON_FINANCIAL) {
            notes += "template_missing_amount"
            if ("amount" !in missing) missing += "amount"
        }
        if (currency == null && amount != null) {
            currency = parseCurrency(body) ?: Currency.SAR
        }
        val metadataDateTime = smsTimestampMillis?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
        }
        return ParsedTransaction(
            originalSender = sender,
            originalMessage = body,
            transactionType = type,
            amount = amount,
            currency = currency ?: Currency.UNKNOWN,
            merchant = merchant,
            accountOrCardLastFourDigits = evidence.firstOrNull {
                it.role != IdentifierRole.DESTINATION
            }?.lastFour,
            transactionDate = date ?: metadataDateTime?.toLocalDate(),
            transactionTime = time ?: metadataDateTime?.toLocalTime(),
            status = if (amount != null || type == TransactionType.NON_FINANCIAL) {
                TransactionStatus.COMPLETED
            } else {
                TransactionStatus.NEEDS_REVIEW
            },
            confidence = if (missing.isEmpty()) 95 else 55,
            parsingNotes = notes,
            parserName = "Template:${pattern.definition.id}",
            parserVersion = pattern.definition.version.toString(),
            matchedRules = listOf("approved_template:${pattern.definition.id}"),
            missingFields = missing.distinct(),
            identifierEvidence = evidence,
        )
    }

    private fun identifierEvidence(
        field: PatternFieldDefinitionEntity,
        raw: String,
    ): ParsedIdentifierEvidence? {
        val last4 = Regex("""(?<!\d)(\d{4})(?!\d)""").find(raw)?.groupValues?.get(1) ?: return null
        val type = when (field.canonicalField) {
            PatternCanonicalField.CREDIT_CARD_LAST4 -> AccountIdentifierType.CREDIT_CARD_LAST4
            PatternCanonicalField.DEBIT_CARD_LAST4 -> AccountIdentifierType.DEBIT_CARD_LAST4
            PatternCanonicalField.IBAN_LAST4,
            PatternCanonicalField.SOURCE_IBAN_LAST4,
            PatternCanonicalField.DESTINATION_IBAN_LAST4,
            -> AccountIdentifierType.IBAN_LAST4
            PatternCanonicalField.WALLET_LAST4 -> AccountIdentifierType.WALLET_LAST4
            else -> AccountIdentifierType.ACCOUNT_LAST4
        }
        val role = when (field.role) {
            PatternFieldRole.SOURCE -> IdentifierRole.SOURCE
            PatternFieldRole.DESTINATION -> IdentifierRole.DESTINATION
            else -> when (field.canonicalField) {
                PatternCanonicalField.SOURCE_ACCOUNT_LAST4,
                PatternCanonicalField.SOURCE_IBAN_LAST4,
                -> IdentifierRole.SOURCE
                PatternCanonicalField.DESTINATION_ACCOUNT_LAST4,
                PatternCanonicalField.DESTINATION_IBAN_LAST4,
                -> IdentifierRole.DESTINATION
                else -> IdentifierRole.UNSPECIFIED
            }
        }
        return ParsedIdentifierEvidence(type, last4, role, 95, "template:${field.placeholderToken}")
    }

    fun defaultPlaceholder(field: PatternCanonicalField): String = when (field) {
        PatternCanonicalField.TRANSACTION_AMOUNT -> "AMOUNT"
        PatternCanonicalField.CURRENCY -> "CURRENCY"
        PatternCanonicalField.MERCHANT -> "MERCHANT"
        PatternCanonicalField.BENEFICIARY -> "BENEFICIARY"
        PatternCanonicalField.TRANSACTION_DATE -> "DATE"
        PatternCanonicalField.TRANSACTION_TIME -> "TIME"
        PatternCanonicalField.AVAILABLE_BALANCE -> "AVAILABLE_BALANCE"
        PatternCanonicalField.CARD_AMOUNT_DUE -> "TOTAL_DUE"
        PatternCanonicalField.TRANSACTION_REFERENCE -> "TRANSACTION_ID"
        else -> field.name
    }

    private fun parseMoney(raw: String): BigDecimal? =
        Regex("""[-+]?\d{1,3}(?:,\d{3})+(?:\.\d+)?|[-+]?\d+(?:\.\d+)?""")
            .find(raw)?.value?.replace(",", "")?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }

    private fun parseCurrency(raw: String): Currency? {
        val value = raw.uppercase(Locale.ROOT)
        return when {
            "SAR" in value || Regex("""\bSR\b""").containsMatchIn(value) || "ريال" in raw -> Currency.SAR
            "USD" in value -> Currency.USD
            "EUR" in value -> Currency.EUR
            else -> null
        }
    }

    private fun parseDate(raw: String): LocalDate? = runCatching {
        val parts = raw.trim().replace('.', '-').replace('/', '-').replace('\u060c', '-')
            .split('-').map(String::toInt)
        val (a, b, c) = parts
        if (a > 31) LocalDate.of(a, b, c) else LocalDate.of(if (c < 100) c + 2000 else c, b, a)
    }.getOrNull()

    private fun parseTime(raw: String): LocalTime? = runCatching {
        val parts = raw.trim().split(':').map(String::toInt)
        if (parts.size == 2) LocalTime.of(parts[0], parts[1])
        else LocalTime.of(parts[0], parts[1], parts[2])
    }.getOrNull()
}

package com.baraa.masroof.sms

import com.baraa.masroof.accounts.AccountSmsAnalyzer
import com.baraa.masroof.data.db.PatternCanonicalField
import com.baraa.masroof.data.db.PatternExtractionStrategy
import com.baraa.masroof.data.db.PatternFieldRole
import com.baraa.masroof.data.db.PatternValueType
import com.baraa.masroof.transaction.LineBasedFieldParser

data class DiscoveredMessagePattern(
    val signature: String,
    val friendlyNameHint: String,
    val messageCount: Int,
    val latestTimestamp: Long,
    val sanitizedSamples: List<String>,
    val suggestedFields: List<SuggestedPatternField>,
    val looksLikeOtpOrMarketing: Boolean,
)

data class SuggestedPatternField(
    val canonicalField: PatternCanonicalField,
    val sourceLabel: String,
    val valueType: PatternValueType,
    val role: PatternFieldRole = PatternFieldRole.PRIMARY,
    val required: Boolean = false,
    val extractionStrategy: PatternExtractionStrategy = PatternExtractionStrategy.LABELED_LINE,
)

/**
 * Clusters inbox SMS for one sender by value-tokenized structural signature.
 */
object PatternDiscoveryService {

    fun discover(messages: List<SmsMessage>): List<DiscoveredMessagePattern> {
        data class Acc(
            val signature: String,
            var count: Int = 0,
            var latest: Long = 0L,
            val samples: MutableList<String> = mutableListOf(),
            val labelHits: MutableMap<String, Int> = linkedMapOf(),
            var otpHits: Int = 0,
            var nameHint: String = "نمط رسالة",
        )
        val buckets = linkedMapOf<String, Acc>()
        for (sms in messages) {
            val body = sms.body.orEmpty()
            if (body.isBlank()) continue
            val signature = SmsStructureNormalizer.signatureFromBody(body)
            val acc = buckets.getOrPut(signature) {
                Acc(signature = signature, nameHint = SmsStructureNormalizer.friendlyNameHint(body))
            }
            acc.count++
            if (sms.timestamp >= acc.latest) {
                acc.latest = sms.timestamp
                if (acc.samples.size < 3) {
                    acc.samples += AccountSmsAnalyzer.sanitizedPreview(body)
                } else if (acc.samples.size == 3) {
                    // keep first samples
                }
            } else if (acc.samples.size < 3) {
                acc.samples += AccountSmsAnalyzer.sanitizedPreview(body)
            }
            if (SmsStructureNormalizer.looksLikeOtpOrMarketing(body)) acc.otpHits++
            for (line in LineBasedFieldParser.splitLines(body)) {
                val label = line.label.trim()
                if (label.isNotEmpty()) {
                    acc.labelHits[label] = (acc.labelHits[label] ?: 0) + 1
                }
            }
        }
        return buckets.values
            .map { acc ->
                DiscoveredMessagePattern(
                    signature = acc.signature,
                    friendlyNameHint = acc.nameHint,
                    messageCount = acc.count,
                    latestTimestamp = acc.latest,
                    sanitizedSamples = acc.samples.distinct().take(3),
                    suggestedFields = suggestFields(acc.labelHits.keys),
                    looksLikeOtpOrMarketing = acc.otpHits * 2 >= acc.count && acc.count > 0,
                )
            }
            .sortedWith(
                compareByDescending<DiscoveredMessagePattern> { it.messageCount }
                    .thenByDescending { it.latestTimestamp },
            )
    }

    fun suggestFields(labels: Collection<String>): List<SuggestedPatternField> {
        val out = linkedMapOf<String, SuggestedPatternField>()
        for (label in labels) {
            val mapped = mapLabel(label) ?: continue
            out.putIfAbsent(label.trim().lowercase(), mapped.copy(sourceLabel = label.trim()))
        }
        return out.values.toList()
    }

    private fun mapLabel(label: String): SuggestedPatternField? {
        val l = label.trim()
        val lower = l.lowercase(LocaleOrRoot())
        fun field(
            c: PatternCanonicalField,
            vt: PatternValueType,
            role: PatternFieldRole = PatternFieldRole.PRIMARY,
            required: Boolean = false,
        ) = SuggestedPatternField(c, l, vt, role, required)

        return when {
            lower.contains("رصيد") || lower.contains("balance") || lower.contains("حد ائتمان") ->
                field(PatternCanonicalField.AVAILABLE_BALANCE, PatternValueType.MONEY)
            lower.contains("مستحق") || lower.contains("amount due") ->
                field(PatternCanonicalField.CARD_AMOUNT_DUE, PatternValueType.MONEY)
            "بمبلغ" in lower || "مبلغ" in lower || "قيمة" in lower ||
                lower.contains("amount") || lower.contains("debited") ->
                field(PatternCanonicalField.TRANSACTION_AMOUNT, PatternValueType.MONEY, required = true)
            lower.contains("بطاقة ائتمان") || lower.contains("ائتمانية") || lower.contains("credit card") ->
                field(PatternCanonicalField.CREDIT_CARD_LAST4, PatternValueType.LAST4)
            lower.contains("مدى") || lower.contains("debit") || lower.contains("بطاقة خصم") ->
                field(PatternCanonicalField.DEBIT_CARD_LAST4, PatternValueType.LAST4)
            lower.contains("آيبان") || lower.contains("iban") ->
                field(PatternCanonicalField.IBAN_LAST4, PatternValueType.LAST4)
            lower.contains("إلى حساب") || lower.contains("حساب المستفيد") ||
                lower.contains("destination") ->
                field(PatternCanonicalField.DESTINATION_ACCOUNT_LAST4, PatternValueType.LAST4, PatternFieldRole.DESTINATION)
            lower.contains("من حساب") || lower.contains("خصمت من") || lower.contains("source") ->
                field(PatternCanonicalField.SOURCE_ACCOUNT_LAST4, PatternValueType.LAST4, PatternFieldRole.SOURCE)
            lower.contains("حساب") || lower.contains("account") || lower.contains("بطاقة") ||
                lower.contains("card") ->
                field(PatternCanonicalField.ACCOUNT_LAST4, PatternValueType.LAST4)
            lower.contains("لدى") || lower.contains("merchant") || lower.contains("تاجر") ->
                field(PatternCanonicalField.MERCHANT, PatternValueType.TEXT)
            lower.contains("مرجع") || lower.contains("reference") || lower.contains("ref") ->
                field(PatternCanonicalField.TRANSACTION_REFERENCE, PatternValueType.REFERENCE)
            lower.contains("وقت") || lower.contains("time") || lower == "في" ->
                field(PatternCanonicalField.TRANSACTION_TIME, PatternValueType.TIME)
            lower.contains("تاريخ") || lower.contains("date") ->
                field(PatternCanonicalField.TRANSACTION_DATE, PatternValueType.DATE)
            lower.contains("عملة") || lower.contains("currency") ->
                field(PatternCanonicalField.CURRENCY, PatternValueType.CURRENCY_CODE)
            else -> null
        }
    }

    private fun LocaleOrRoot(): java.util.Locale = java.util.Locale.ROOT
}

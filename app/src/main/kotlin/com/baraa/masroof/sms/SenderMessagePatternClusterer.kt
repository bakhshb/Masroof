package com.baraa.masroof.sms

import com.baraa.masroof.accounts.AccountSmsAnalyzer

/**
 * One discovered SMS style for a sender (transfer, Google Pay, debit, …).
 * Features are structural only — no raw body is retained beyond an on-device sample.
 */
data class DiscoveredSmsPattern(
    val senderDisplay: String,
    val senderKey: String,
    val structureKey: String,
    val messageCount: Int,
    val latestTimestamp: Long,
    val sanitizedSample: String,
    val features: LearnedSmsFeatures,
    val looksLikeOtp: Boolean,
)

/**
 * Clusters inbox SMS by (normalized sender + structureKey) for pattern-first teach.
 */
object SenderMessagePatternClusterer {

    fun cluster(messages: List<SmsMessage>): List<DiscoveredSmsPattern> {
        data class Acc(
            val senderDisplay: String,
            val senderKey: String,
            val structureKey: String,
            var count: Int = 0,
            var latestTimestamp: Long = 0L,
            var sampleBody: String? = null,
            val amountLabels: LinkedHashSet<String> = linkedSetOf(),
            val typeCues: LinkedHashSet<String> = linkedSetOf(),
            val lineLabels: LinkedHashSet<String> = linkedSetOf(),
            var otpHits: Int = 0,
        )

        val buckets = linkedMapOf<Pair<String, String>, Acc>()
        for (sms in messages) {
            val display = sms.sender?.trim().orEmpty()
            if (display.isBlank()) continue
            val senderKey = SenderNormalizer.normalize(display) ?: continue
            val body = sms.body.orEmpty()
            val structureKey = SenderMessagePatternLearner.structureKeyFromBody(body)
            val features = SenderMessagePatternLearner.learnInclude(listOf(body))
            val key = senderKey to structureKey
            val acc = buckets.getOrPut(key) {
                Acc(
                    senderDisplay = display,
                    senderKey = senderKey,
                    structureKey = structureKey,
                )
            }
            acc.count++
            if (sms.timestamp >= acc.latestTimestamp) {
                acc.latestTimestamp = sms.timestamp
                if (body.isNotBlank()) acc.sampleBody = body
            } else if (acc.sampleBody == null && body.isNotBlank()) {
                acc.sampleBody = body
            }
            acc.amountLabels += features.amountLabels
            acc.typeCues += features.typeCues
            acc.lineLabels += features.lineLabels
            if (BankSmsFilter.isOtpOrAuthenticationMessage(body)) acc.otpHits++
        }

        return buckets.values
            .map { acc ->
                DiscoveredSmsPattern(
                    senderDisplay = acc.senderDisplay,
                    senderKey = acc.senderKey,
                    structureKey = acc.structureKey,
                    messageCount = acc.count,
                    latestTimestamp = acc.latestTimestamp,
                    sanitizedSample = AccountSmsAnalyzer.sanitizedPreview(acc.sampleBody),
                    features = LearnedSmsFeatures(
                        amountLabels = acc.amountLabels,
                        typeCues = acc.typeCues,
                        lineLabels = acc.lineLabels,
                    ),
                    looksLikeOtp = acc.otpHits > 0 && acc.otpHits * 2 >= acc.count,
                )
            }
            .sortedWith(
                compareByDescending<DiscoveredSmsPattern> { it.messageCount }
                    .thenByDescending { it.latestTimestamp }
                    .thenBy { it.senderDisplay },
            )
    }
}

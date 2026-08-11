package com.baraa.masroof.bank.aljazira

import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.Confidence
import com.baraa.masroof.parsing.detector.BankDetector
import com.baraa.masroof.parsing.model.BankDetectionResult
import java.util.Locale

/**
 * Conservative Bank AlJazira detection via normalized sender match.
 *
 * False negative is preferred over false positive. No substring matching on sender.
 * Common device variants (spacing, hyphens, promotional -AD suffix) are normalized
 * before the exact allowlist check.
 */
class AlJaziraBankDetector : BankDetector {
    override fun detect(sender: String, body: String): BankDetectionResult {
        val normalizedSender = normalizeSender(sender)
        if (normalizedSender in EXACT_SENDERS) {
            return BankDetectionResult.Detected(
                bank = Bank.BANK_ALJAZIRA,
                confidence = Confidence(
                    score = 1.0,
                    reasons = listOf("exact_sender:$normalizedSender"),
                ),
                evidence = listOf("sender:$sender"),
            )
        }
        return BankDetectionResult.Unknown(
            reasons = listOf("sender_not_recognized_as_bank_aljazira"),
        )
    }

    internal companion object {
        /** Fixture-proven exact sender forms only (after normalization). */
        val EXACT_SENDERS = setOf(
            "aljazira",
        )

        internal fun normalizeSender(sender: String): String {
            var normalized = sender
                .trim()
                .lowercase(Locale.ROOT)
                .replace(Regex("[\\s\\-_]+"), "")
            if (normalized.endsWith("ad") && normalized.length > 2) {
                val withoutPromoSuffix = normalized.removeSuffix("ad")
                if (withoutPromoSuffix in EXACT_SENDERS) {
                    normalized = withoutPromoSuffix
                }
            }
            return normalized
        }
    }
}

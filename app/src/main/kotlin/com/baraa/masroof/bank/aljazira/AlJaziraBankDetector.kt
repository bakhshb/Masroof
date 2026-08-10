package com.baraa.masroof.bank.aljazira

import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.Confidence
import com.baraa.masroof.parsing.detector.BankDetector
import com.baraa.masroof.parsing.model.BankDetectionResult
import java.util.Locale

/**
 * Deterministic Bank AlJazira detection from fixture-supported sender evidence.
 *
 * Does not treat arbitrary banking-looking SMS as AlJazira.
 */
class AlJaziraBankDetector : BankDetector {
    override fun detect(sender: String, body: String): BankDetectionResult {
        val normalizedSender = sender.trim().lowercase(Locale.ROOT)
        for (form in SENDER_FORMS) {
            if (normalizedSender == form || normalizedSender.contains(form)) {
                return BankDetectionResult.Detected(
                    bank = Bank.BANK_ALJAZIRA,
                    confidence = Confidence(
                        score = if (normalizedSender == form) 1.0 else 0.9,
                        reasons = listOf("sender_match:$form"),
                    ),
                    evidence = listOf("sender:$sender"),
                )
            }
        }
        return BankDetectionResult.Unknown(
            reasons = listOf("sender_not_recognized_as_bank_aljazira"),
        )
    }

    companion object {
        /** Fixture / known AlJazira sender forms only. */
        private val SENDER_FORMS = listOf(
            "aljazira",
            "al-jazira",
            "bank aljazira",
            "bank al-jazira",
            "jazira",
        )
    }
}

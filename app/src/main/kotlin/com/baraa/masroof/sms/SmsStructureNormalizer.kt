package com.baraa.masroof.sms

import com.baraa.masroof.transaction.LineBasedFieldParser

/**
 * Public facade for the runtime structural signature.
 *
 * Delegates to [CanonicalMessageNormalizer] — the single source of truth
 * shared by training and import. Produces a deterministic signature for any
 * SMS body, independent of merchant / amount / date / time / last-four /
 * reference values.
 */
object SmsStructureNormalizer {

    /** Canonical signature of a raw SMS body. */
    fun signatureFromBody(body: String?): String =
        StructuralSignatureGenerator.text(CanonicalMessageNormalizer.normalizeBody(body))

    /** Canonical signature of a stored template. */
    fun signatureFromTemplate(templateText: String?): String =
        StructuralSignatureGenerator.text(CanonicalMessageNormalizer.normalizeTemplate(templateText))

    /** Stable hash for `(senderProfileId, hash)` uniqueness. */
    fun signatureHash(body: String?): String =
        StructuralSignatureGenerator.hash(CanonicalMessageNormalizer.normalizeBody(body))

    /** Friendly display name from the canonical structure. */
    fun friendlyNameHint(body: String?): String =
        SemanticPatternCanonicalizer.displayName(body)

    /** Detect OTP / marketing content; classification never participates in identity. */
    fun looksLikeOtpOrMarketing(body: String?): Boolean {
        if (BankSmsFilter.isOtpOrAuthenticationMessage(body)) return true
        val n = MessageTypeCueCatalog.foldArabic(
            BankSmsFilter.normalizeForKeywordSearch(body.orEmpty()),
        )
        return listOf("عرض", "خصم حصري", "اشترك", "promotion", "unsubscribe", "اعلان")
            .any { it in n }
    }
}
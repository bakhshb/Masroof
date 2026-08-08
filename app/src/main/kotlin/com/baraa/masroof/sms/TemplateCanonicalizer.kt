package com.baraa.masroof.sms

/**
 * Template-side facade for the canonical variant identity.
 *
 * Delegates to [CanonicalMessageNormalizer] so a template built during
 * training and a body scanned during import produce the same signature.
 */
object TemplateCanonicalizer {

    /**
     * Canonical key for a stored template. Includes the normalization version
     * so the same logical structure under different versions never collides.
     *
     * `transactionTypeName` is retained for source compatibility but never
     * participates in identity.
     */
    fun canonicalKey(
        templateText: String?,
        signature: String,
        transactionTypeName: String? = null,
    ): String = signature.takeIf { it.isNotBlank() }
        ?: SmsStructureNormalizer.signatureFromTemplate(templateText)

    fun canonicalKeyFromBody(body: String?): String =
        SmsStructureNormalizer.signatureFromBody(body)
}
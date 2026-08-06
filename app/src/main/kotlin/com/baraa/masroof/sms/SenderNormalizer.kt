package com.baraa.masroof.sms

import java.text.Normalizer
import java.util.Locale

/** Canonical, exact-key normalization for SMS sender addresses.
 * It intentionally does not apply fuzzy matching or bank-name inference. */
object SenderNormalizer {
    fun normalize(sender: String?): String? {
        if (sender.isNullOrBlank()) return null
        val key = Normalizer.normalize(sender, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .map { when (it) {
                '٠' -> '0'; '١' -> '1'; '٢' -> '2'; '٣' -> '3'; '٤' -> '4'
                '٥' -> '5'; '٦' -> '6'; '٧' -> '7'; '٨' -> '8'; '٩' -> '9'
                else -> it
            } }
            .filter { it.isLetterOrDigit() }
            .joinToString("")
        return key.takeIf { it.isNotBlank() }?.take(64)
    }
}

package com.baraa.masroof.presentation.common

/** User-facing last4 label; never shows internal placeholders like "unknown". */
fun formatCardLast4(last4: String?): String {
    val trimmed = last4?.trim().orEmpty()
    if (trimmed.isEmpty()) return "····"
    if (trimmed.equals("unknown", ignoreCase = true)) return "····"
    return trimmed
}

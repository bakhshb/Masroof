package com.baraa.masroof.ui.accounts

/**
 * Presentation helpers for account identifier chips on account lists.
 */
object AccountIdentifierLabels {
    /**
     * Joins active last-four values for one account as `•••• 1111 · •••• 2222`.
     */
    fun formatLastFours(normalizedValues: List<String>): String? {
        val distinct = normalizedValues
            .map { it.filter(Char::isDigit).takeLast(4) }
            .filter { it.length == 4 }
            .distinct()
        if (distinct.isEmpty()) return null
        return distinct.joinToString(" · ") { "•••• $it" }
    }
}

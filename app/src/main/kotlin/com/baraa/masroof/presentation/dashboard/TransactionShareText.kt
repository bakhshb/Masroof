package com.baraa.masroof.presentation.dashboard

/**
 * Plain-text share payload for one transaction or a filtered period list.
 * Localization happens before this formatter; it only joins lines.
 */
object TransactionShareText {
    fun field(label: String, value: String): String = "$label: $value"

    fun listRow(date: String, title: String, amount: String, type: String): String =
        listOf(date, title, amount, type).joinToString(" • ")

    fun document(title: String, vararg sections: String?): String {
        val lines = buildList {
            add(title.trim())
            sections.mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }.forEach(::add)
        }
        return lines.joinToString("\n")
    }

    fun listDocument(
        title: String,
        metaLines: List<String>,
        rows: List<String>,
    ): String {
        val parts = buildList {
            add(title.trim())
            metaLines.map { it.trim() }.filter { it.isNotEmpty() }.forEach(::add)
            if (rows.isNotEmpty()) {
                add("")
                addAll(rows)
            }
        }
        return parts.joinToString("\n")
    }
}

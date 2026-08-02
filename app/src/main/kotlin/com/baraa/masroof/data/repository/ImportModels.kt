package com.baraa.masroof.data.repository

import com.baraa.masroof.data.db.TransactionEntity

/** Counts produced by [TransactionImportService.preview] before any DB writes. */
data class ImportPreview(
    val messagesScanned: Int,
    val parsedSuccessfully: Int,
    val unparseable: Int,
    val newTransactions: Int,
    val duplicatesSkipped: Int,
) {
    /** Convenience flag for the UI to decide whether to enable the Save button. */
    val hasAnythingToImport: Boolean get() = newTransactions > 0
}

/** Counts produced by [TransactionImportService.commit] after a real insert pass. */
data class ImportSummary(
    val messagesScanned: Int,
    val parsedSuccessfully: Int,
    val unparseable: Int,
    val inserted: Int,
    val duplicatesSkipped: Int,
) {
    companion object {
        fun fromPreviewAndInsert(preview: ImportPreview, inserted: Int, duplicatesFromInsert: Int) =
            ImportSummary(
                messagesScanned = preview.messagesScanned,
                parsedSuccessfully = preview.parsedSuccessfully,
                unparseable = preview.unparseable,
                inserted = inserted,
                duplicatesSkipped = preview.duplicatesSkipped + duplicatesFromInsert,
            )
    }
}

/**
 * A [TransactionEntity] that has been built from a single SMS and is ready to
 * insert. Carries no extra state — the entity IS the payload.
 */
typealias PreparedTransaction = TransactionEntity

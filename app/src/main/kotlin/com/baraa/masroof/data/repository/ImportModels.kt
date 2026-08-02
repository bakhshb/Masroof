package com.baraa.masroof.data.repository

import com.baraa.masroof.data.db.TransactionEntity

/** Per-item duplicate status surfaced in the import preview. */
enum class ImportItemStatus {
    /** A genuinely new transaction the user has not imported before. */
    NEW,

    /** The exact same SMS was already imported (fingerprint collision). */
    EXACT_DUPLICATE,

    /**
     * A different SMS message for what looks like the same underlying
     * transaction (same sender / amount / currency / type / merchant /
     * card / day / time-bucket), arriving within the configured duplicate
     * window. The user should review and decide.
     */
    POSSIBLE_DUPLICATE,
}

/** Per-item user decision shown in the duplicate review dialog. */
enum class DuplicateDecision {
    /** Skip this item — do not insert. */
    SKIP,

    /** Insert anyway — treat as a new transaction. */
    INSERT_ANYWAY,
}

/** One parsed message being considered for import, with its decision. */
data class ImportPreviewItem(
    val smsIndex: Int,
    val sender: String?,
    val bodyExcerpt: String?, // truncated for display; never full body
    val amount: java.math.BigDecimal?,
    val currency: com.baraa.masroof.transaction.Currency,
    val transactionType: com.baraa.masroof.transaction.TransactionType,
    val merchant: String?,
    val smsTimestamp: Long,
    val preparedEntity: TransactionEntity?,
    val status: ImportItemStatus,
    /** For POSSIBLE_DUPLICATE items, the existing transaction it collides with. */
    val collidingWith: TransactionEntity? = null,
    val decision: DuplicateDecision = when (status) {
        // Exact duplicates are auto-skipped; possible duplicates default to
        // skip (user must opt in to insert anyway); new items default to
        // insert.
        ImportItemStatus.EXACT_DUPLICATE -> DuplicateDecision.SKIP
        ImportItemStatus.POSSIBLE_DUPLICATE -> DuplicateDecision.SKIP
        ImportItemStatus.NEW -> DuplicateDecision.INSERT_ANYWAY
    },
)

/** Aggregate counts produced by [TransactionImportService.preview]. */
data class ImportPreview(
    val messagesScanned: Int,
    val parsedSuccessfully: Int,
    val unparseable: Int,
    val newTransactions: Int,
    val exactDuplicates: Int,
    val possibleDuplicates: Int,
) {
    val hasAnythingToImport: Boolean get() = newTransactions > 0
    val hasPossibleDuplicates: Boolean get() = possibleDuplicates > 0
}

/** Counts produced by [TransactionImportService.commit] after a real insert pass. */
data class ImportSummary(
    val messagesScanned: Int,
    val parsedSuccessfully: Int,
    val unparseable: Int,
    val inserted: Int,
    val exactDuplicatesSkipped: Int,
    val possibleDuplicatesSkipped: Int,
    val possibleDuplicatesInserted: Int,
) {
    companion object {
        fun fromCounts(
            messagesScanned: Int,
            parsedSuccessfully: Int,
            unparseable: Int,
            inserted: Int,
            exactDuplicatesSkipped: Int,
            possibleDuplicatesSkipped: Int,
            possibleDuplicatesInserted: Int,
        ) = ImportSummary(
            messagesScanned = messagesScanned,
            parsedSuccessfully = parsedSuccessfully,
            unparseable = unparseable,
            inserted = inserted,
            exactDuplicatesSkipped = exactDuplicatesSkipped,
            possibleDuplicatesSkipped = possibleDuplicatesSkipped,
            possibleDuplicatesInserted = possibleDuplicatesInserted,
        )
    }
}

package com.baraa.masroof.transaction

import java.math.BigDecimal
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

/**
 * Deterministic SHA-256 fingerprint for a parsed bank transaction.
 *
 * The fingerprint is used to deduplicate imports: the same physical SMS must
 * produce the same fingerprint, but a materially different transaction must
 * produce a different one. The hash is over **stable, normalized** values so
 * that minor formatting differences (case, whitespace, leading/trailing
 * spaces) do not cause a re-import to create a duplicate row.
 *
 * The input components are:
 *  - normalized sender (trim + lowercase + collapse whitespace)
 *  - SMS received timestamp (epoch millis)
 *  - amount as [BigDecimal.toPlainString]
 *  - currency enum name
 *  - transaction type enum name
 *  - normalized merchant or beneficiary (trim + lowercase + collapse whitespace)
 *  - last four digits when present, "" otherwise
 *
 * The hash is returned as a lowercase hex string. Two callers computing the
 * fingerprint for the same logical transaction always get identical strings.
 *
 * This object is **pure JVM** — no Android imports, no logging, no I/O — so
 * the contract can be unit-tested on the JVM.
 */
object TransactionFingerprint {

    fun compute(
        sender: String?,
        smsTimestamp: Long,
        amount: BigDecimal?,
        currency: Currency,
        type: TransactionType,
        merchant: String?,
        lastFour: String?,
    ): String {
        val input = buildString {
            append(normalize(sender))
            append('|')
            append(smsTimestamp)
            append('|')
            append(amount?.toPlainString().orEmpty())
            append('|')
            append(currency.name)
            append('|')
            append(type.name)
            append('|')
            append(normalize(merchant))
            append('|')
            append(lastFour.orEmpty())
        }
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return buildString(bytes.size * 2) {
            for (b in bytes) {
                append(HEX_CHARS[(b.toInt() ushr 4) and 0x0F])
                append(HEX_CHARS[b.toInt() and 0x0F])
            }
        }
    }

    /**
     * Normalize a free-text field for fingerprinting: trim, lowercase using the
     * root locale, and collapse internal whitespace runs. This keeps the hash
     * stable across minor formatting differences without dropping meaningful
     * characters.
     */
    fun normalize(s: String?): String {
        if (s.isNullOrBlank()) return ""
        return s.trim().lowercase(Locale.ROOT).replace(WHITESPACE_RUN, " ")
    }

    /**
     * Generate a coarse similarity key used for near-duplicate detection
     * across separate SMS messages for the same purchase (e.g. a push
     * notification and a later digest). Deliberately **excludes** the exact
     * SMS-received timestamp so a re-sent SMS still collides; the importing
     * service then narrows with [com.baraa.masroof.data.repository.TransactionImportService.DUPLICATE_WINDOW_MILLIS].
     *
     * The transaction time is rounded down to the nearest [timeWindowMinutes]
     * (default 10) so two messages sent 5 minutes apart for the same
     * purchase still share a key, while legitimately-repeated purchases
     * (e.g. the same $5 coffee tomorrow morning) keep distinct keys.
     */
    fun generateSimilarityKey(
        sender: String?,
        amount: BigDecimal?,
        currency: Currency,
        type: TransactionType,
        merchant: String?,
        lastFour: String?,
        date: LocalDate?,
        time: LocalTime?,
        timeWindowMinutes: Int = DEFAULT_SIMILARITY_WINDOW_MIN,
    ): String {
        val timeRounded = if (time != null) roundTimeToWindow(time, timeWindowMinutes) else null
        val input = buildString {
            append(normalize(sender))
            append('|')
            append(amount?.toPlainString().orEmpty())
            append('|')
            append(currency.name)
            append('|')
            append(type.name)
            append('|')
            append(normalize(merchant))
            append('|')
            append(lastFour.orEmpty())
            append('|')
            append(date?.toString().orEmpty())
            append('|')
            append(timeRounded?.toString().orEmpty())
        }
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return buildString(bytes.size * 2) {
            for (b in bytes) {
                append(HEX_CHARS[(b.toInt() ushr 4) and 0x0F])
                append(HEX_CHARS[b.toInt() and 0x0F])
            }
        }
    }

    private fun roundTimeToWindow(time: LocalTime, windowMinutes: Int): LocalTime {
        if (windowMinutes <= 0) return time
        val totalMinutes = time.hour * 60 + time.minute
        val rounded = (totalMinutes / windowMinutes) * windowMinutes
        return LocalTime.of(rounded / 60, rounded % 60)
    }

    private val WHITESPACE_RUN = Regex("\\s+")
    private val HEX_CHARS = "0123456789abcdef"

    const val DEFAULT_SIMILARITY_WINDOW_MIN: Int = 10
}

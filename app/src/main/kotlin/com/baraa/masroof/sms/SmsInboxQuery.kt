package com.baraa.masroof.sms

import java.time.ZoneId

/**
 * Exact ContentResolver arguments used to read the device SMS inbox.
 *
 * Date bounds are always epoch **milliseconds** (Android SMS `date` column units).
 * Template / sender / OTP filters must never appear in [selection].
 *
 * URI / column names are plain strings so JVM unit tests can assert the query
 * without relying on Android framework stubs.
 */
data class SmsInboxQuery(
    val uriString: String = INBOX_URI,
    val projection: Array<String> = DEFAULT_PROJECTION,
    val selection: String,
    val selectionArgs: Array<String>,
    val sortOrder: String,
    val startMillis: Long,
    val endMillisExclusive: Long,
    val zoneId: String,
    val selectedStartDate: String,
    val selectedEndExclusive: String,
    val limit: Int,
) {
    fun toLogMap(): Map<String, Any?> = mapOf(
        "uri" to uriString,
        "selection" to selection,
        "selectionArgs" to selectionArgs.toList().toString(),
        "sortOrder" to sortOrder,
        "startMillis" to startMillis,
        "endMillisExclusive" to endMillisExclusive,
        "zoneId" to zoneId,
        "selectedStartDate" to selectedStartDate,
        "selectedEndExclusive" to selectedEndExclusive,
        "limit" to limit,
        "dateUnit" to "milliseconds",
    )

    companion object {
        const val INBOX_URI: String = "content://sms/inbox"
        const val COL_ID: String = "_id"
        const val COL_ADDRESS: String = "address"
        const val COL_BODY: String = "body"
        const val COL_DATE: String = "date"

        val DEFAULT_PROJECTION: Array<String> = arrayOf(COL_ID, COL_ADDRESS, COL_BODY, COL_DATE)

        /**
         * Build the provider query for [range].
         *
         * Selection is date-only: `date >= start AND date < endExclusive`.
         * Never filters by sender, approved templates, or OTP.
         */
        fun forRange(
            range: SmsImportRange,
            limit: Int = SmsRepository.DEFAULT_LIMIT,
            zone: ZoneId = ZoneId.systemDefault(),
        ): SmsInboxQuery {
            val startMillis = range.startMillis(zone)
            val endMillis = range.endMillis(zone)
            return SmsInboxQuery(
                selection = "$COL_DATE >= ? AND $COL_DATE < ?",
                selectionArgs = arrayOf(startMillis.toString(), endMillis.toString()),
                sortOrder = "$COL_DATE DESC",
                startMillis = startMillis,
                endMillisExclusive = endMillis,
                zoneId = zone.id,
                selectedStartDate = range.start.toString(),
                selectedEndExclusive = range.endExclusive.toString(),
                limit = limit,
            )
        }

        /** True when [epoch] looks like seconds-since-epoch for dates after ~2001. */
        fun looksLikeEpochSeconds(epoch: Long): Boolean =
            epoch in 1_000_000_000L..99_999_999_999L
    }
}

/**
 * Result of reading the Android SMS provider — never conflates permission
 * failure with an empty inbox.
 */
sealed class SmsInboxLoadResult {
    abstract val query: SmsInboxQuery
    /** Rows returned by the provider cursor before app-side classification. */
    abstract val rawRowCount: Int

    data class Success(
        val messages: List<SmsMessage>,
        override val query: SmsInboxQuery,
        override val rawRowCount: Int,
    ) : SmsInboxLoadResult()

    data class PermissionDenied(
        override val query: SmsInboxQuery,
        override val rawRowCount: Int = 0,
        val messageAr: String = "لا توجد صلاحية لقراءة الرسائل",
    ) : SmsInboxLoadResult()

    data class Failed(
        override val query: SmsInboxQuery,
        override val rawRowCount: Int = 0,
        val errorMessage: String,
    ) : SmsInboxLoadResult()

    val messagesOrEmpty: List<SmsMessage>
        get() = (this as? Success)?.messages.orEmpty()

    val permissionMissing: Boolean get() = this is PermissionDenied
}

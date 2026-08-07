package com.baraa.masroof.sms

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads SMS rows from the system SMS provider, classifies them with
 * [BankSmsFilter], and parses any likely financial message into a
 * structured transaction via [BankParserRegistry].
 *
 * The repository is read-only: it never writes, deletes, or modifies messages.
 * All I/O runs on [Dispatchers.IO] so callers may invoke it from the main thread.
 */
class SmsRepository(private val context: Context) {

    private val tag = "SmsRepository"

    /**
     * Load inbox messages inside the given [range], capped at [limit].
     * Each row is annotated with the bank-filter result AND a parsed
     * transaction (when applicable). UI layers can decide whether to show
     * or hide non-financial messages, and whether to render the structured
     * card or the raw body.
     *
     * Messages outside the selected range are never returned.
     *
     * @return list of messages, empty if the provider is unavailable or no rows match
     */
    suspend fun loadInbox(
        range: SmsImportRange,
        limit: Int = DEFAULT_LIMIT,
    ): List<SmsMessage> = withContext(Dispatchers.IO) {
        val startMillis = range.startMillis()
        // endExclusive for the SQL query is end-of-last-day; if [endExclusive]
        // lands at start of next day, that already represents end-of-day.
        val endMillis = range.endMillis()
        val resolver: ContentResolver = context.contentResolver
        val uri: Uri = Telephony.Sms.Inbox.CONTENT_URI

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
        )
        // Cap must be high enough for full-month bank SMS; 100 silently dropped
        // older messages and left balances incomplete.
        val sortOrder = "${Telephony.Sms.DATE} DESC LIMIT $limit"
        val selection = "${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms.DATE} < ?"
        val args = arrayOf(startMillis.toString(), endMillis.toString())

        val result = ArrayList<SmsMessage>()
        var cursor: Cursor? = null
        try {
            cursor = resolver.query(uri, projection, selection, args, sortOrder)
            if (cursor == null) {
                Log.w(tag, "ContentResolver returned null cursor")
                return@withContext emptyList()
            }
            val idIdx = cursor.getColumnIndex(Telephony.Sms._ID)
            val addrIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
            val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)

            while (cursor.moveToNext()) {
                val id = if (idIdx >= 0 && !cursor.isNull(idIdx)) cursor.getLong(idIdx) else 0L
                val sender = if (addrIdx >= 0 && !cursor.isNull(addrIdx)) cursor.getString(addrIdx) else null
                val body = if (bodyIdx >= 0 && !cursor.isNull(bodyIdx)) cursor.getString(bodyIdx) else null
                val date = if (dateIdx >= 0 && !cursor.isNull(dateIdx)) cursor.getLong(dateIdx) else 0L
                if (date < startMillis || date >= endMillis) continue
                val match = BankSmsFilter.classifyMessage(sender, body)
                // Parsing is intentionally deferred to SmsImportOrchestrator,
                // after the registered-sender authorization gate.
                result.add(SmsMessage(id = id, sender = sender, body = body, timestamp = date, matchReason = match.reason))
            }
            if (result.size >= limit) {
                Log.w(tag, "SMS inbox hit limit=$limit in selected range — older messages may be missing")
            } else {
                Log.d(tag, "Loaded ${result.size} SMS rows in selected range")
            }
        } catch (security: SecurityException) {
            // Permission revoked between check and query — return empty so UI can recover.
            Log.e(tag, "READ_SMS permission missing while reading inbox", security)
            return@withContext emptyList()
        } catch (t: Throwable) {
            Log.e(tag, "Failed to read SMS inbox", t)
            return@withContext emptyList()
        } finally {
            runCatching { cursor?.close() }
        }
        result
    }

    /**
     * Recover a stored transaction's SMS body from the device inbox
     * (sender + timestamp). Used for link assist on imports that predate
     * the local SMS-body table. Never logs the body.
     */
    suspend fun findBodyBySenderAndTimestamp(
        sender: String?,
        timestampMillis: Long,
        windowMillis: Long = BODY_LOOKUP_WINDOW_MILLIS,
    ): String? = withContext(Dispatchers.IO) {
        if (timestampMillis <= 0L) return@withContext null
        val wantSender = BankSmsFilter.normalizeSender(sender.orEmpty())
        if (wantSender.isBlank()) return@withContext null
        val resolver = context.contentResolver
        val uri = Telephony.Sms.Inbox.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
        )
        val selection = "${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms.DATE} <= ?"
        val args = arrayOf(
            (timestampMillis - windowMillis).toString(),
            (timestampMillis + windowMillis).toString(),
        )
        var cursor: Cursor? = null
        try {
            cursor = resolver.query(uri, projection, selection, args, "${Telephony.Sms.DATE} DESC")
                ?: return@withContext null
            val addrIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
            val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)
            var bestBody: String? = null
            var bestDelta = Long.MAX_VALUE
            while (cursor.moveToNext()) {
                val rowSender = if (addrIdx >= 0 && !cursor.isNull(addrIdx)) cursor.getString(addrIdx) else null
                if (BankSmsFilter.normalizeSender(rowSender.orEmpty()) != wantSender) continue
                val date = if (dateIdx >= 0 && !cursor.isNull(dateIdx)) cursor.getLong(dateIdx) else continue
                val body = if (bodyIdx >= 0 && !cursor.isNull(bodyIdx)) cursor.getString(bodyIdx) else null
                if (body.isNullOrBlank()) continue
                val delta = kotlin.math.abs(date - timestampMillis)
                if (delta < bestDelta) {
                    bestDelta = delta
                    bestBody = body
                }
            }
            bestBody
        } catch (_: SecurityException) {
            null
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { cursor?.close() }
        }
    }

    companion object {
        /** High enough for a busy month of bank SMS; raising this fixes incomplete balances. */
        const val DEFAULT_LIMIT: Int = 5000
        const val BODY_LOOKUP_WINDOW_MILLIS: Long = 3L * 60L * 1000L
    }
}

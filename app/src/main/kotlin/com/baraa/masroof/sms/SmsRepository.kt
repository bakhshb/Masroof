package com.baraa.masroof.sms

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads SMS rows from the system SMS provider.
 *
 * The repository is read-only: it never writes, deletes, or modifies messages.
 * All I/O runs on [Dispatchers.IO] so callers may invoke it from the main thread.
 *
 * **Important:** the provider query is date-range only. Sender, OTP, template,
 * and account filters belong in [com.baraa.masroof.data.repository.SmsImportOrchestrator].
 */
class SmsRepository(private val context: Context) {

    private val tag = "SmsRepository"

    /**
     * Load inbox messages inside the given [range], capped at [limit].
     *
     * Prefer [loadInboxResult] when the UI must distinguish permission denial
     * from a genuinely empty date window.
     */
    suspend fun loadInbox(
        range: SmsImportRange,
        limit: Int = DEFAULT_LIMIT,
    ): List<SmsMessage> = loadInboxResult(range, limit).messagesOrEmpty

    /**
     * Query the SMS provider and return a typed result with query diagnostics.
     */
    suspend fun loadInboxResult(
        range: SmsImportRange,
        limit: Int = DEFAULT_LIMIT,
    ): SmsInboxLoadResult = withContext(Dispatchers.IO) {
        val query = SmsInboxQuery.forRange(range, limit = limit)
        val resolver: ContentResolver = context.contentResolver
        val uri = android.net.Uri.parse(query.uriString)


        var cursor: Cursor? = null
        try {
            cursor = resolver.query(
                uri,
                query.projection,
                query.selection,
                query.selectionArgs,
                query.sortOrder,
            )
            if (cursor == null) {
                Log.w(tag, "ContentResolver returned null cursor for ${query.uriString}")
                return@withContext SmsInboxLoadResult.Failed(
                    query = query,
                    errorMessage = "مزود الرسائل أعاد نتيجة فارغة",
                )
            }
            val idIdx = cursor.getColumnIndex(SmsInboxQuery.COL_ID)
            val addrIdx = cursor.getColumnIndex(SmsInboxQuery.COL_ADDRESS)
            val bodyIdx = cursor.getColumnIndex(SmsInboxQuery.COL_BODY)
            val dateIdx = cursor.getColumnIndex(SmsInboxQuery.COL_DATE)

            val result = ArrayList<SmsMessage>(cursor.count.coerceAtLeast(0).coerceAtMost(limit))
            var rawRows = 0
            while (cursor.moveToNext()) {
                rawRows++
                if (result.size >= limit) continue
                val id = if (idIdx >= 0 && !cursor.isNull(idIdx)) cursor.getLong(idIdx) else 0L
                val sender = if (addrIdx >= 0 && !cursor.isNull(addrIdx)) cursor.getString(addrIdx) else null
                val body = if (bodyIdx >= 0 && !cursor.isNull(bodyIdx)) cursor.getString(bodyIdx) else null
                val date = if (dateIdx >= 0 && !cursor.isNull(dateIdx)) cursor.getLong(dateIdx) else 0L
                // Defense in depth: provider selection already applies the window.
                if (date < query.startMillis || date >= query.endMillisExclusive) continue
                val match = BankSmsFilter.classifyMessage(sender, body)
                result.add(
                    SmsMessage(
                        id = id,
                        sender = sender,
                        body = body,
                        timestamp = date,
                        matchReason = match.reason,
                    ),
                )
            }
            if (result.size >= limit) {
                Log.w(tag, "SMS inbox hit limit=$limit in selected range — older messages may be missing")
            } else {
                Log.d(tag, "Loaded ${result.size} SMS rows in selected range (rawCursor=$rawRows)")
            }
            SmsInboxLoadResult.Success(
                messages = result,
                query = query,
                rawRowCount = rawRows,
            )
        } catch (security: SecurityException) {
            Log.e(tag, "READ_SMS permission missing while reading inbox", security)
            SmsInboxLoadResult.PermissionDenied(query = query)
        } catch (t: Throwable) {
            Log.e(tag, "Failed to read SMS inbox", t)
            SmsInboxLoadResult.Failed(
                query = query,
                errorMessage = t.message ?: t.javaClass.simpleName,
            )
        } finally {
            runCatching { cursor?.close() }
        }
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

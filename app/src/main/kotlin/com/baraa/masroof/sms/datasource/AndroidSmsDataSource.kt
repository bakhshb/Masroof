package com.baraa.masroof.sms.datasource

import android.content.ContentResolver
import android.net.Uri
import android.provider.Telephony
import com.baraa.masroof.sms.model.ProviderSmsRecord
import java.time.Instant

/**
 * Reads the device SMS inbox via [Telephony.Sms.Inbox].
 *
 * Projection and column indexes are explicit. Sort order is DATE ASC
 * (oldest → newest) for stable later discovery/matching.
 */
class AndroidSmsDataSource(
    private val contentResolver: ContentResolver,
) : SmsDataSource {

    override fun queryInbox(receivedAfter: Instant?): Sequence<ProviderSmsRecord> = sequence {
        val selection: String?
        val selectionArgs: Array<String>?
        if (receivedAfter != null) {
            selection = "${Telephony.Sms.DATE} >= ?"
            selectionArgs = arrayOf(receivedAfter.toEpochMilli().toString())
        } else {
            selection = null
            selectionArgs = null
        }

        val cursor = try {
            contentResolver.query(
                INBOX_URI,
                PROJECTION,
                selection,
                selectionArgs,
                SORT_ORDER,
            )
        } catch (se: SecurityException) {
            throw SmsPermissionException(cause = se)
        } catch (t: Throwable) {
            throw SmsProviderException("SMS provider query failed", t)
        }

        if (cursor == null) {
            throw SmsProviderException("SMS provider returned null cursor")
        }

        cursor.use { c ->
            val idIdx = c.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addressIdx = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIdx = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIdx = c.getColumnIndexOrThrow(Telephony.Sms.DATE)

            while (c.moveToNext()) {
                val id = c.getString(idIdx)
                val address = c.getString(addressIdx)
                val body = c.getString(bodyIdx)
                val dateMillis = c.getLong(dateIdx)
                if (id.isNullOrBlank() || address.isNullOrBlank() || body == null) {
                    // Skip malformed row; scanner counts separately when mapping fails.
                    continue
                }
                yield(
                    ProviderSmsRecord(
                        providerMessageId = id,
                        sender = address,
                        body = body,
                        receivedAt = Instant.ofEpochMilli(dateMillis),
                    ),
                )
            }
        }
    }

    companion object {
        private val INBOX_URI: Uri = Telephony.Sms.Inbox.CONTENT_URI
        private val PROJECTION = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
        )
        /** Oldest → newest. */
        private const val SORT_ORDER = "${Telephony.Sms.DATE} ASC"
    }
}

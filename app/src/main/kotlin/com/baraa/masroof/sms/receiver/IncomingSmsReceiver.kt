package com.baraa.masroof.sms.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.baraa.masroof.MasroofApplication
import com.baraa.masroof.sms.ingestion.SmsIngestionService
import com.baraa.masroof.sms.mapper.AndroidSmsMapper
import com.baraa.masroof.sms.model.ProviderSmsRecord
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Receives [Telephony.Sms.Intents.SMS_RECEIVED_ACTION] and hands off to the
 * shared [SmsIngestionService].
 *
 * Multipart PDUs are combined into one RawSms body (correct part order).
 * Work runs off the main broadcast path via [goAsync] + application scope.
 *
 * Does not log SMS bodies, OTPs, or financial fields.
 */
class IncomingSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val app = context.applicationContext as? MasroofApplication
        if (app == null) {
            // Unexpected process wiring — do not invent a separate database graph.
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) {
            return
        }

        val sender = messages.firstOrNull()?.displayOriginatingAddress?.takeIf { it.isNotBlank() }
            ?: return
        val body = messages.joinToString(separator = "") { it.displayMessageBody.orEmpty() }
        if (body.isEmpty()) {
            return
        }
        // Prefer the earliest originating timestamp among parts when available.
        val receivedAtMillis = messages
            .mapNotNull { msg -> msg.timestampMillis.takeIf { it > 0L } }
            .minOrNull()
            ?: System.currentTimeMillis()

        val rawSms = try {
            AndroidSmsMapper.toRawSms(
                ProviderSmsRecord(
                    providerMessageId = null,
                    sender = sender,
                    body = body,
                    receivedAt = Instant.ofEpochMilli(receivedAtMillis),
                ),
            )
        } catch (_: IllegalArgumentException) {
            return
        }

        val pendingResult = goAsync()
        val ingestion: SmsIngestionService = app.container.smsIngestionService
        app.container.applicationScope.launch {
            try {
                ingestion.ingest(rawSms)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

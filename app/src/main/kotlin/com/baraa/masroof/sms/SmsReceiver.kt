package com.baraa.masroof.sms

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import com.baraa.masroof.MasroofApplication
import com.baraa.masroof.notifications.BankTransactionNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives `SMS_RECEIVED` broadcasts and forwards the messages to the
 * canonical [com.baraa.masroof.data.repository.SmsImportOrchestrator].
 *
 * Flow:
 *  1. Verify RECEIVE_SMS permission.
 *  2. Combine multipart SMS via [Telephony.Sms.Intents.getMessagesFromIntent].
 *  3. Drop OTP-only messages and unknown senders.
 *  4. Build a single [SmsMessage] per sender and call
 *     [com.baraa.masroof.data.repository.SmsImportOrchestrator.processIncoming].
 *  5. After the orchestrator returns, post notifications + update
 *     diagnostics counters in [com.baraa.masroof.diagnostics.DeveloperPreferences].
 *
 * The receiver uses [goAsync] so the suspend orchestrator work can run
 * without blocking the main thread or ANR-ing the system.
 *
 * **DO NOT** reimplement parsing here. The orchestrator is the only
 * canonical pipeline that does parsing, dedup, journal creation, and
 * balance refresh.
 */
class SmsReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val app = context.applicationContext as? MasroofApplication ?: return
        if (!app.developerPreferences.automaticSmsImportEnabled) return
        if (context.checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            recordDiagnostic(app, sender = null, result = "PERMISSION_DENIED")
            return
        }
        val pending = goAsync()
        scope.launch {
            try {
                val combined = combineMultipart(intent)
                val filtered = combined.filter { isFinancial(it) }
                if (filtered.isEmpty()) {
                    recordDiagnostic(app, sender = null, result = "IGNORED_NON_FINANCIAL")
                    return@launch
                }
                val trackingStartDate = app.financialSetupRepository.run {
                    val s = load()
                    java.time.Instant.ofEpochMilli(s.trackingStartDate)
                        .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                }
                val result = app.importOrchestrator.processIncoming(filtered, trackingStartDate)
                updateCounters(app, result)
                postNotifications(app, result)
                recordDiagnostic(app, sender = filtered.first().sender, result = summaryFor(result))
            } catch (t: Throwable) {
                recordDiagnostic(app, sender = null, result = "ERROR:${t.javaClass.simpleName}")
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * Combines multipart SMS into a single [SmsMessage] per sender.
     * Pure function so it can be unit-tested without an Android Context.
     */
    internal fun combineMultipart(intent: Intent): List<SmsMessage> {
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return emptyList()
        val bySender = linkedMapOf<String, SmsMessage>()
        for (m in messages) {
            val sender = m.displayOriginatingAddress ?: m.serviceCenterAddress ?: ""
            val existing = bySender[sender]
            bySender[sender] = if (existing == null) {
                SmsMessage(
                    id = 0L,
                    sender = sender,
                    body = m.displayMessageBody,
                    timestamp = if (m.timestampMillis > 0L) m.timestampMillis else System.currentTimeMillis(),
                )
            } else {
                existing.copy(
                    body = (existing.body ?: "") + (m.displayMessageBody ?: ""),
                    timestamp = kotlin.math.min(existing.timestamp, m.timestampMillis),
                )
            }
        }
        return bySender.values.toList()
    }

    /**
     * Filters out OTP-only and unknown senders. Keeps only messages
     * that look like bank notifications.
     */
    private fun isFinancial(message: SmsMessage): Boolean {
        val body = message.body ?: return false
        // OTP / auth challenges are rejected inside BankSmsFilter.classifyMessage.
        return BankSmsFilter.classifyMessage(message.sender, body).isMatch
    }

    private fun updateCounters(app: MasroofApplication, result: com.baraa.masroof.data.repository.SmsImportResult) {
        val p = app.developerPreferences
        p.autoImportedCount += result.importedTransactions
        p.autoNeedsReviewCount += result.needsReviewTransactions
        p.autoDuplicateCount += result.duplicateTransactions
    }

    private fun postNotifications(app: MasroofApplication, result: com.baraa.masroof.data.repository.SmsImportResult) {
        if (!app.developerPreferences.transactionNotificationsEnabled) return
        val notifier = BankTransactionNotifier(app)
        for (acct in result.affectedAccounts) {
            notifier.notifyBalanceRefreshed(acct)
        }
        if (result.linkedTransactions > 0) {
            notifier.notifyDebitOrCreditSummary(result.linkedTransactions, result.affectedAccounts)
        }
        if (result.needsReviewTransactions > 0) {
            notifier.notifyNeedsReview(result.needsReviewTransactions)
        }
        app.developerPreferences.lastNotificationResult = "ok"
    }

    private fun recordDiagnostic(app: MasroofApplication, sender: String?, result: String) {
        val p = app.developerPreferences
        p.lastReceiverTriggerAt = System.currentTimeMillis()
        p.lastReceiverSender = sender?.let { maskSender(it) }
        p.lastReceiverResult = result
    }

    private fun maskSender(sender: String): String =
        if (sender.length <= 4) "••••"
        else sender.substring(0, 2) + "••••" + sender.substring(sender.length - 2)

    private fun summaryFor(result: com.baraa.masroof.data.repository.SmsImportResult): String {
        return "imported=${result.importedTransactions} linked=${result.linkedTransactions} posted=${result.postedTransactions} review=${result.needsReviewTransactions} dup=${result.duplicateTransactions}"
    }
}
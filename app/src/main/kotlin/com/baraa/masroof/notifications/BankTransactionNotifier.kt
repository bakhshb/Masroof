package com.baraa.masroof.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.baraa.masroof.MainActivity
import com.baraa.masroof.data.repository.SmsImportResult
import java.text.NumberFormat
import java.util.Locale

/**
 * Posts masked transaction notifications on Android 13+ when the user
 * has enabled [com.baraa.masroof.diagnostics.DeveloperPreferences.transactionNotificationsEnabled].
 *
 * Sensitive data is **never** shown in full:
 *  - No OTP codes
 *  - No full card numbers (only last 4 digits)
 *  - No full account numbers
 *  - No full SMS body
 */
class BankTransactionNotifier(private val context: Context) {

    init {
        ensureChannel()
    }

    fun notifyDebitOrCreditSummary(linked: Int, accounts: List<SmsImportResult.AffectedAccountSummary>) {
        val totalIn = accounts.fold(java.math.BigDecimal.ZERO) { acc, a -> acc.add(a.moneyIn) }
        val totalOut = accounts.fold(java.math.BigDecimal.ZERO) { acc, a -> acc.add(a.moneyOut) }
        val sample = accounts.firstOrNull()
        // Prefer outflow wording when more money left the accounts than entered.
        val title = if (totalOut.compareTo(totalIn) > 0) {
            "تم تسجيل خصم / مصروف"
        } else {
            "تم تسجيل مبلغ وارد"
        }
        val body = buildString {
            when {
                totalOut.compareTo(totalIn) > 0 && totalOut.signum() > 0 ->
                    append("خصم ${formatAmount(totalOut)} ر.س")
                totalIn.signum() > 0 ->
                    append("إضافة ${formatAmount(totalIn)} ر.س")
                else -> append("تم تحديث الحساب")
            }
            sample?.let { append(" • ${it.accountName}") }
            sample?.let { append(" • الرصيد المحسوب ${formatAmount(it.calculatedBalance)} ر.س") }
        }
        post(NOTIF_ID_TX, title, body)
    }

    fun notifyBalanceRefreshed(acct: SmsImportResult.AffectedAccountSummary) {
        val body = "تم تحديث ${acct.accountName}. الرصيد المحسوب ${formatAmount(acct.calculatedBalance)} ر.س"
        post(NOTIF_ID_BALANCE, "تحديث حساب", body)
    }

    fun notifyNeedsReview(count: Int) {
        if (count <= 0) return
        val body = "تعذّر تحديد الحساب أو نوع العملية لـ $count عملية. اضغط لمراجعتها."
        post(NOTIF_ID_REVIEW, "عملية جديدة تحتاج مراجعة", body)
    }

    private fun post(id: Int, title: String, body: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pending = PendingIntent.getActivity(context, id, intent, pendingIntentFlags)
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.baraa.masroof.R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { nm.notify(id, notif) }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME_AR, NotificationManager.IMPORTANCE_DEFAULT)
            channel.description = CHANNEL_DESC
            nm.createNotificationChannel(channel)
        }
    }

    private fun formatAmount(value: java.math.BigDecimal): String =
        NumberFormat.getNumberInstance(Locale.US).apply {
            maximumFractionDigits = 2
            minimumFractionDigits = if (value.stripTrailingZeros().scale() > 0) 2 else 0
        }.format(value.abs())

    companion object {
        const val CHANNEL_ID = "bank_transactions"
        const val CHANNEL_NAME_AR = "العمليات البنكية"
        const val CHANNEL_DESC = "إشعارات تسجيل العمليات البنكية الجديدة"
        const val NOTIF_ID_TX = 1001
        const val NOTIF_ID_BALANCE = 1002
        const val NOTIF_ID_REVIEW = 1003
    }
}

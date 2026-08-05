package com.baraa.masroof.diagnostics

import android.content.Context
import android.content.SharedPreferences

/**
 * SharedPreferences-backed implementation of [DeveloperPreferences].
 * Defaults: [showDevDetails] = false, [testDataMode] = false.
 *
 * Test mode is **never** persisted as `true` across process restarts —
 * the user must enable it explicitly each session.
 */
class SharedPreferencesDeveloperPreferences(context: Context) : DeveloperPreferences {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override var showDevDetails: Boolean
        get() = prefs.getBoolean(KEY_SHOW_DEV, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_DEV, value).apply()

    override var testDataMode: Boolean
        get() = prefs.getBoolean(KEY_TEST_DATA, false)
        set(value) = prefs.edit().putBoolean(KEY_TEST_DATA, value).apply()

    override var automaticSmsImportEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SMS_IMPORT, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SMS_IMPORT, value).apply()

    override var transactionNotificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_TX_NOTIFS, false)
        set(value) = prefs.edit().putBoolean(KEY_TX_NOTIFS, value).apply()

    override var needsReviewNotificationsOnly: Boolean
        get() = prefs.getBoolean(KEY_REVIEW_NOTIFS, false)
        set(value) = prefs.edit().putBoolean(KEY_REVIEW_NOTIFS, value).apply()

    override var balanceInNotifications: Boolean
        get() = prefs.getBoolean(KEY_BALANCE_IN_NOTIF, true)
        set(value) = prefs.edit().putBoolean(KEY_BALANCE_IN_NOTIF, value).apply()

    override var lastReceiverTriggerAt: Long
        get() = prefs.getLong(KEY_LAST_RX_TRIGGER, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_RX_TRIGGER, value).apply()

    override var lastReceiverSender: String?
        get() = prefs.getString(KEY_LAST_RX_SENDER, null)
        set(value) = prefs.edit().putString(KEY_LAST_RX_SENDER, value).apply()

    override var lastReceiverResult: String?
        get() = prefs.getString(KEY_LAST_RX_RESULT, null)
        set(value) = prefs.edit().putString(KEY_LAST_RX_RESULT, value).apply()

    override var lastNotificationResult: String?
        get() = prefs.getString(KEY_LAST_NOTIF_RESULT, null)
        set(value) = prefs.edit().putString(KEY_LAST_NOTIF_RESULT, value).apply()

    override var autoImportedCount: Int
        get() = prefs.getInt(KEY_AUTO_IMPORTED, 0)
        set(value) = prefs.edit().putInt(KEY_AUTO_IMPORTED, value).apply()

    override var autoNeedsReviewCount: Int
        get() = prefs.getInt(KEY_AUTO_NEEDS_REVIEW, 0)
        set(value) = prefs.edit().putInt(KEY_AUTO_NEEDS_REVIEW, value).apply()

    override var autoDuplicateCount: Int
        get() = prefs.getInt(KEY_AUTO_DUPLICATE, 0)
        set(value) = prefs.edit().putInt(KEY_AUTO_DUPLICATE, value).apply()

    companion object {
        const val PREFS_NAME: String = "masroof_dev_prefs"
        const val KEY_SHOW_DEV: String = "show_dev_details"
        const val KEY_TEST_DATA: String = "test_data_mode"
        const val KEY_AUTO_SMS_IMPORT: String = "auto_sms_import_enabled"
        const val KEY_TX_NOTIFS: String = "transaction_notifications_enabled"
        const val KEY_REVIEW_NOTIFS: String = "needs_review_notifications_only"
        const val KEY_BALANCE_IN_NOTIF: String = "balance_in_notifications"
        const val KEY_LAST_RX_TRIGGER: String = "last_receiver_trigger_at"
        const val KEY_LAST_RX_SENDER: String = "last_receiver_sender"
        const val KEY_LAST_RX_RESULT: String = "last_receiver_result"
        const val KEY_LAST_NOTIF_RESULT: String = "last_notification_result"
        const val KEY_AUTO_IMPORTED: String = "auto_imported_count"
        const val KEY_AUTO_NEEDS_REVIEW: String = "auto_needs_review_count"
        const val KEY_AUTO_DUPLICATE: String = "auto_duplicate_count"
    }
}
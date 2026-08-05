package com.baraa.masroof.diagnostics

/**
 * Persistent developer / testing preferences. Backed by
 * SharedPreferences in production; tests can drive this through a fake
 * implementation.
 *
 * These preferences control the **automatic** portions of the app —
 * automatic incoming-SMS processing, notifications, and the receiver
 * diagnostics counters used in [com.baraa.masroof.ui.diagnostics.DiagnosticsScreen].
 */
interface DeveloperPreferences {
    var showDevDetails: Boolean
    var testDataMode: Boolean

    /** Section J — automatic import of new incoming bank SMS. */
    var automaticSmsImportEnabled: Boolean

    /** Section K — show notifications for new transactions. */
    var transactionNotificationsEnabled: Boolean
    var needsReviewNotificationsOnly: Boolean
    var balanceInNotifications: Boolean

    /** Section L — receiver diagnostics. */
    var lastReceiverTriggerAt: Long
    var lastReceiverSender: String?
    var lastReceiverResult: String?
    var lastNotificationResult: String?
    var autoImportedCount: Int
    var autoNeedsReviewCount: Int
    var autoDuplicateCount: Int
}
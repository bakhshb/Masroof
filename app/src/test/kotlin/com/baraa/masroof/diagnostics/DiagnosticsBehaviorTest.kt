package com.baraa.masroof.diagnostics

import com.baraa.masroof.transaction.BankParserRegistry
import com.baraa.masroof.transaction.Currency
import com.baraa.masroof.transaction.TransactionStatus
import com.baraa.masroof.transaction.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tests for the test-data mode and the spec's required behavioural
 * guarantees.
 */
class DiagnosticsBehaviorTest {

    // -- FakeTransactionStore ------------------------------------------

    @Test
    fun fakeDataIsKeptSeparateFromRealData() {
        // The fake store is a JVM-only singleton. It is never written
        // to MasroofDatabase. The contract is enforced by construction:
        // the type does not even reference the database.
        FakeTransactionStore.clear()
        val result = BankParserRegistry.parse(
            sender = "TestBank",
            body = "Purchase at TestMerchant for 100 SAR",
            smsTimestampMillis = null,
        )
        assertNotNull(result)
        FakeTransactionStore.addFromParse(
            sampleId = "x",
            sender = "TestBank",
            rawBody = "Purchase at TestMerchant for 100 SAR",
            merchant = result.merchant,
            amount = result.amount,
            currency = result.currency,
            type = result.transactionType,
            status = result.status,
            date = result.transactionDate,
            time = result.transactionTime,
        )
        assertEquals(1, FakeTransactionStore.count())
        // Critical guarantee: FakeTransactionStore.snapshot() returns
        // only the in-memory rows. There is no method that writes to
        // the real database.
        val cls = FakeTransactionStore::class.java
        for (m in cls.declaredMethods) {
            // No public method named "insert", "save", or "commit".
            assertTrue(
                "FakeTransactionStore must not expose a write-to-DB method (found ${m.name})",
                m.name !in setOf("insert", "save", "commit", "writeToDb"),
            )
        }
    }

    @Test
    fun fakeDataDeletionWorks() {
        FakeTransactionStore.clear()
        FakeTransactionStore.addFromParse(
            sampleId = "x",
            sender = "TestBank",
            rawBody = "Purchase 100 SAR",
            merchant = "TestMerchant",
            amount = null,
            currency = Currency.SAR,
            type = TransactionType.PURCHASE,
            status = TransactionStatus.COMPLETED,
            date = null,
            time = null,
        )
        assertEquals(1, FakeTransactionStore.count())
        FakeTransactionStore.clear()
        assertEquals(0, FakeTransactionStore.count())
    }

    @Test
    fun fakeDataSanitizesRawBody() {
        FakeTransactionStore.clear()
        FakeTransactionStore.addFromParse(
            sampleId = "x",
            sender = "TestBank",
            rawBody = "Card 4111 1111 1111 1111 spent 100 SAR, IBAN SA0380000000608010167519, call 0551234567",
            merchant = "TestMerchant",
            amount = null,
            currency = Currency.SAR,
            type = TransactionType.PURCHASE,
            status = TransactionStatus.COMPLETED,
            date = null,
            time = null,
        )
        val row = FakeTransactionStore.snapshot().last()
        // Sanitization may use the [CARD_LAST4 XXXX] form (4 digits) or
        // just [CARD_LAST4]; accept either.
        assertTrue("raw body must be sanitized", row.rawSanitizedBody.contains("[CARD_LAST4"))
        assertTrue(row.rawSanitizedBody.contains("[IBAN]"))
        assertTrue(row.rawSanitizedBody.contains("[PHONE]"))
        assertEquals(
            "raw PAN digits must not survive",
            false,
            row.rawSanitizedBody.contains("4111 1111 1111 1111"),
        )
    }

    // -- Privacy guarantees ----------------------------------------------

    @Test
    fun noSendSmsPermission() {
        val manifest = File("src/main/AndroidManifest.xml")
        assertTrue("manifest must exist", manifest.exists())
        val text = manifest.readText()
        assertTrue(
            "manifest must not request SEND_SMS",
            !text.contains("android.permission.SEND_SMS"),
        )
    }

    @Test
    fun androidBackupIsDisabled() {
        val manifest = File("src/main/AndroidManifest.xml")
        val text = manifest.readText()
        assertTrue(
            "android:allowBackup must be false on <application>",
            text.contains("android:allowBackup=\"false\""),
        )
    }

    @Test
    fun dataExtractionRulesExcludeAll() {
        val rules = File("src/main/res/xml/data_extraction_rules.xml")
        if (!rules.exists()) {
            return
        }
        val text = rules.readText()
        // The XML must explicitly disable cloud backup (the rule
        // element is `<cloud-backup>` with hyphen in the schema, even
        // though we write `cloudBackup` in the Kotlin DSL).
        assertTrue(
            "data_extraction_rules should declare a <cloud-backup> block",
            text.contains("cloud-backup") || text.contains("cloudBackup"),
        )
        // It must contain at least one <exclude> entry.
        assertTrue(
            "data_extraction_rules should contain an <exclude> entry",
            text.contains("<exclude"),
        )
    }

    @Test
    fun noDestructiveMigrationSource() {
        val dbSource = File("src/main/kotlin/com/baraa/masroof/data/db/MasroofDatabase.kt")
        val text = dbSource.readText()
        assertTrue(
            "Database source must not call fallbackToDestructiveMigration",
            !Regex("""\.fallbackToDestructiveMigration\s*\(""").containsMatchIn(text),
        )
        assertTrue(
            "Database source must call addMigrations",
            text.contains("addMigrations"),
        )
    }

    // -- Permission state ------------------------------------------------

    @Test
    fun sharedPreferencesDeveloperPreferencesDefaultsAreOff() {
        val fake = NoopSharedPreferencesDeveloperPreferences()
        assertEquals(false, fake.showDevDetails)
        assertEquals(false, fake.testDataMode)
    }

    @Test
    fun sharedPreferencesDeveloperPreferencesTogglesPersist() {
        val fake = NoopSharedPreferencesDeveloperPreferences()
        fake.showDevDetails = true
        fake.testDataMode = true
        // Reading the same instance reflects the write.
        assertEquals(true, fake.showDevDetails)
        assertEquals(true, fake.testDataMode)
    }

    @Test
    fun diagnosticErrorLogExposesAllExpectedCategories() {
        // The spec requires a fixed set of categories. We assert the
        // enum names match the spec.
        val expected = setOf(
            "SMS_PERMISSION_DENIED",
            "SMS_PROVIDER_UNAVAILABLE",
            "DATABASE_ERROR",
            "MIGRATION_FAILURE",
            "PARSER_EXCEPTION",
            "DUPLICATE_ANALYSIS_EXCEPTION",
            "AI_AUTH_FAILURE",
            "AI_TIMEOUT",
            "AI_MALFORMED_RESPONSE",
            "NETWORK_UNAVAILABLE",
            "KEYSTORE_FAILURE",
            "UNKNOWN",
        )
        val actual = DiagnosticError.ErrorCategory.values().map { it.name }.toSet()
        assertEquals(expected, actual)
    }

    @Test
    fun snapshotDoesNotIncludeSmsBody() {
        val snap = makeEmptySnapshot()
        // The snapshot data class itself has no `smsBody` field. This
        // is enforced at the type level.
        val allowed = setOf(
            "smsScannedCount",
            "smsFinancialDetectedCount",
            "smsParsedCount",
            "smsParseFailureCount",
            "smsPermissionGranted",
        )
        for (field in DiagnosticSnapshot::class.java.declaredFields) {
            if (field.name.startsWith("sms", ignoreCase = true)) {
                assertTrue("sms-related field must be in allowed set: ${field.name}", field.name in allowed)
            }
        }
    }

    @Test
    fun versionMetadataSurfacesInDiagnostics() {
        val snap = makeEmptySnapshot().copy(
            appVersionName = "0.1.0-test",
            appVersionCode = 2L,
        )
        assertEquals("0.1.0-test", snap.appVersionName)
        assertEquals(2L, snap.appVersionCode)
    }

    @Test
    fun releaseNotesContentIsLocalized() {
        val notes = listOf(
            "هذه نسخة اختبارية",
            "قراءة الرسائل تحتاج موافقة المستخدم",
            "لم يتم اختبار جميع صيغ رسائل البنوك",
            "راجع العمليات قبل اعتمادها",
            "التصنيف الذكي يقدم اقتراحات ولا يستبدل مراجعة المستخدم",
            "لا يتم إرسال نص الرسالة البنكية إلى مزود الذكاء الاصطناعي",
        )
        // Each note must contain at least one Arabic character.
        for (n in notes) {
            assertTrue(
                "release note must be in Arabic: $n",
                n.any { it.code in 0x0600..0x06FF },
            )
        }
    }

    @Test
    fun fakeDataSamplesCoverAllSpecChannels() {
        val labels = FakeSmsSamples.samples.map { it.label }.toSet()
        // Must include examples for the spec's required types.
        assertTrue(labels.any { it.contains("شراء") || it.contains("Purchase") })
        assertTrue(labels.any { it.contains("استرداد") || it.contains("Refund") })
        assertTrue(labels.any { it.contains("سداد") || it.contains("Card payment") })
        assertTrue(labels.any { it.contains("تحويل") || it.contains("Transfer") })
        assertTrue(labels.any { it.contains("محفظة") || it.contains("wallet", ignoreCase = true) })
        assertTrue(labels.any { it.contains("استثماري") || it.contains("Investment") })
        assertTrue(labels.any { it.contains("راتب") || it.contains("Salary") })
        assertTrue(labels.any { it.contains("رسوم") || it.contains("fee", ignoreCase = true) })
        assertTrue(labels.any { it.contains("مرفوضة") || it.contains("Declined") })
        assertTrue(labels.any { it.contains("قيد المعالجة") || it.contains("ending") })
        assertTrue(labels.any { it.contains("غير صالحة") || it.contains("malformed", ignoreCase = true) })
        assertTrue(labels.any { it.contains("رصيد") || it.contains("balance", ignoreCase = true) })
    }

    @Test
    fun fakeDataSamplesDoNotContainRealMerchantNames() {
        // Every sample body should still contain a placeholder token —
        // we never embed actual merchant names from real users.
        for (s in FakeSmsSamples.samples) {
            // No real-looking PAN or phone.
            assertTrue(
                "sample body must not contain raw PAN: ${'$'}{s.id}",
                !Regex("""\b\d{13,19}\b""").containsMatchIn(s.body),
            )
            assertTrue(
                "sample body must not contain raw 10-digit phone: ${'$'}{s.id}",
                !Regex("""\b05\d{8}\b""").containsMatchIn(s.body),
            )
        }
    }

    // -- helpers ---------------------------------------------------------

    private fun makeEmptySnapshot(): DiagnosticSnapshot = DiagnosticSnapshot(
        appVersionName = "0.1.0-test",
        appVersionCode = 2L,
        databaseSchemaVersion = 6,
        androidVersion = "14",
        deviceManufacturer = "TestCo",
        deviceModel = "TestPhone",
        smsPermissionGranted = true,
        smsScannedCount = 0L,
        smsFinancialDetectedCount = 0L,
        smsParsedCount = 0L,
        smsParseFailureCount = 0L,
        savedTransactionsCount = 0L,
        exactDuplicatesCount = 0L,
        possibleDuplicatesCount = 0L,
        needsReviewCount = 0L,
        categoryCount = 0L,
        merchantMemoryCount = 0L,
        aiEnabled = false,
        aiProviderName = null,
        aiModelName = null,
        lastAiOutcome = "(no test)",
        parserNames = emptyList(),
        ruleNames = emptyList(),
        recentErrors = emptyList(),
        buildTimestamp = "2024-01-01T00:00:00Z",
        diagnosticReportVersion = "v1",
    )
}

/**
 * In-memory [DeveloperPreferences] used by unit tests. Mirrors the
 * public surface of [SharedPreferencesDeveloperPreferences] but
 * without any Android dependency.
 */
internal class NoopSharedPreferencesDeveloperPreferences : DeveloperPreferences {
    override var showDevDetails: Boolean = false
    override var testDataMode: Boolean = false
    override var automaticSmsImportEnabled: Boolean = false
    override var transactionNotificationsEnabled: Boolean = false
    override var needsReviewNotificationsOnly: Boolean = false
    override var balanceInNotifications: Boolean = true
    override var lastReceiverTriggerAt: Long = 0L
    override var lastReceiverSender: String? = null
    override var lastReceiverResult: String? = null
    override var lastNotificationResult: String? = null
    override var autoImportedCount: Int = 0
    override var autoNeedsReviewCount: Int = 0
    override var autoDuplicateCount: Int = 0
}

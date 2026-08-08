package com.baraa.masroof.sms

import com.baraa.masroof.data.db.MessagePatternDefinitionEntity
import com.baraa.masroof.data.db.MessagePatternStatus
import com.baraa.masroof.transaction.TransactionType
import com.baraa.masroof.transaction.TransactionTypeTaxonomy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternDiscoveryServiceTest {

    private fun sms(id: Long, body: String, ts: Long = id * 1000) =
        SmsMessage(id, "BankX", body, ts, MatchReason.NONE)

    private val purchaseA = """
        شراء عبر الانترنت
        بطاقة: 1234
        مبلغ: 25.50 SAR
        لدى: StoreOne
    """.trimIndent()

    private val purchaseB = """
        شراء عبر الانترنت
        بطاقة: 9876
        مبلغ: 120.00 SAR
        لدى: StoreTwo
    """.trimIndent()

    private val transfer = """
        حوالة صادرة
        من حساب: 1111
        مبلغ: 500.00 SAR
    """.trimIndent()

    @Test
    fun canonicalEqualMessagesMergeIntoOneCluster() {
        val clusters = PatternDiscoveryService.discover(
            listOf(sms(1, purchaseA), sms(2, purchaseB), sms(3, transfer)),
        )
        assertEquals(2, clusters.size)
        val purchase = clusters.first { it.messageCount == 2 }
        assertTrue(purchase.canonicalKey.isNotBlank())
        assertNull(purchase.matchedPatternId)
    }

    @Test
    fun formattingOnlyVariantsCollapseIntoOneCluster() {
        val reformatted = purchaseA
            .lines()
            .joinToString("\n\n") { "  ${it.replace(":", " : ")}  " }
            .replace("StoreOne", "StoreThree")
        val clusters = PatternDiscoveryService.discover(
            listOf(sms(1, purchaseA), sms(2, reformatted)),
        )
        assertEquals(1, clusters.size)
        assertEquals(2, clusters.single().messageCount)
    }

    @Test
    fun clustersMatchingSavedPatternsAreFlagged() {
        val fromSaved = MessageTemplateEngine.buildFromSms(purchaseA)
        val saved = MessagePatternDefinitionEntity(
            id = 42L,
            senderProfileId = 1L,
            userFriendlyName = "شراء عبر الانترنت",
            normalizedSignature = fromSaved.signature,
            canonicalKey = TemplateCanonicalizer.canonicalKey(fromSaved.templateText, fromSaved.signature),
            templateText = fromSaved.templateText,
            transactionType = fromSaved.transactionType?.name,
            status = MessagePatternStatus.APPROVED,
            isActive = true,
            normalizationVersion = NORMALIZATION_VERSION,
            createdAt = 0L,
            updatedAt = 0L,
        )
        val clusters = PatternDiscoveryService.discover(
            listOf(sms(1, purchaseB), sms(2, transfer)),
            existingPatterns = listOf(saved),
        )
        val purchaseCluster = clusters.first {
            it.transactionTypeName == TransactionType.ONLINE_PURCHASE.name ||
                it.friendlyNameHint.contains("انترنت") ||
                it.friendlyNameHint.contains("إنترنت")
        }
        assertEquals(42L, purchaseCluster.matchedPatternId)
        assertEquals(MessagePatternStatus.APPROVED, purchaseCluster.matchedPatternStatus)
        val transferCluster = clusters.first { it.canonicalKey != purchaseCluster.canonicalKey }
        assertNull(transferCluster.matchedPatternId)
    }

    @Test
    fun otpMessagesAreSkippedBeforeTemplateConstruction() {
        val otp = "رمز التحقق: 123456 لا تشاركه مع أحد"
        val result = PatternDiscoveryService.discoverSafely(
            listOf(sms(1, purchaseA), sms(2, otp)),
        )
        assertEquals(1, result.patterns.size)
        assertEquals(1, result.skippedOtp)
        assertEquals(1, result.processedMessages)
    }

    @Test
    fun singleSalaryMessageCreatesCandidateNotDiscarded() {
        val salary = """
            إيداع راتب
            إلى حساب: 3001
            مبلغ العملية: SAR 12,000.00
            في: 09:00 27-07-2026
        """.trimIndent()
        val clusters = PatternDiscoveryService.discover(listOf(sms(1, salary)))
        assertEquals(1, clusters.size)
        val c = clusters.single()
        assertEquals(1, c.messageCount)
        assertEquals(TransactionType.SALARY.name, c.transactionTypeName)
        assertTrue(c.discoveryConfidence > 0)
        assertTrue(c.discoveryConfidence < TransactionTypeTaxonomy.discoveryConfidence(3))
        val partition = com.baraa.masroof.ui.senders.PatternReviewState.partition(clusters)
        assertEquals(1, partition.needsPattern.size)
        assertTrue(partition.excluded.isEmpty())
    }

    @Test
    fun salaryWithIncomingTransferPhraseStaysSalaryNotTransferIn() {
        val body = """
            حوالة واردة
            راتب شهر يوليو
            إلى حساب *3001
            المبلغ: 15,000.00 ر.س
            في: 10:00 27-07-2026
        """.trimIndent()
        val cue = MessageTypeCueCatalog.detect(body)
        assertEquals(TransactionType.SALARY, cue.transactionType)
        assertEquals(com.baraa.masroof.transaction.MoneyFlowDirection.INFLOW.name, cue.direction)
        val clusters = PatternDiscoveryService.discover(
            listOf(
                sms(1, body),
                sms(
                    2,
                    """
                    تحويل وارد
                    من: أحمد
                    إلى حساب *3001
                    المبلغ: 200.00 ر.س
                    في: 11:00 28-07-2026
                    """.trimIndent(),
                ),
            ),
        )
        assertEquals(2, clusters.size)
        val salary = clusters.single { it.transactionTypeName == TransactionType.SALARY.name }
        val transferIn = clusters.single { it.transactionTypeName == TransactionType.TRANSFER_IN.name }
        assertEquals(1, salary.messageCount)
        assertEquals(1, transferIn.messageCount)
        assertTrue(salary.canonicalKey.isNotBlank())
        assertTrue(transferIn.canonicalKey.isNotBlank())
        assertTrue(salary.canonicalKey != transferIn.canonicalKey)
    }

    @Test
    fun salaryAppearsInIncomeFamilyGroup() {
        val body = """
            إيداع راتب
            إلى حساب: 3001
            مبلغ العملية: SAR 12,000.00
        """.trimIndent()
        val clusters = PatternDiscoveryService.discover(listOf(sms(1, body)))
        assertEquals(TransactionType.SALARY.name, clusters.single().transactionTypeName)
        assertEquals(
            com.baraa.masroof.transaction.TransactionTypeFamily.INCOME,
            TransactionTypeTaxonomy.familyOf(TransactionType.SALARY),
        )
    }

    @Test
    fun singleOccurrenceIsNotDiscarded() {
        val clusters = PatternDiscoveryService.discover(listOf(sms(1, transfer)))
        assertEquals(1, clusters.size)
        assertEquals(1, clusters.single().messageCount)
    }

    @Test
    fun dailyLimitChangeIsNonFinancialNotOnlinePurchase() {
        val body = """
            عزيزي العميل
            بناء على طلبكم، تم تغيير الحد اليومي للشراء عبر الانترنت الخاص بك
        """.trimIndent()
        val cue = MessageTypeCueCatalog.detect(body)
        assertEquals(TransactionType.NON_FINANCIAL, cue.transactionType)
        val result = PatternDiscoveryService.discoverSafely(listOf(sms(1, body)))
        assertTrue(result.patterns.isEmpty())
        assertEquals(1, result.skippedNonFinancial)
    }

    @Test
    fun oneFailedMessageDoesNotAbortLargeDiscoveryBatch() {
        val messages = buildList {
            repeat(75) { add(sms((it + 1).toLong(), purchaseA.replace("StoreOne", "Store$it"))) }
            repeat(50) { add(sms((it + 76).toLong(), transfer.replace("500.00", "${500 + it}.00"))) }
            repeat(24) { add(sms((it + 126).toLong(), "رمز التحقق: 1234 لا تشاركه مع أحد")) }
            add(sms(150, "مبلغ:\u0000:\uD800"))
        }
        val result = PatternDiscoveryService.discoverSafely(
            messages,
            emptyList(),
        ) { message, stage ->
            if (message.id == 150L && stage == PatternDiscoveryStage.TEMPLATE_BUILD) {
                error("injected malformed message")
            }
        }

        assertEquals(150, result.inputMessages)
        assertEquals(1, result.failedMessages)
        assertEquals(24, result.skippedOtp)
        assertEquals(PatternDiscoveryStage.TEMPLATE_BUILD, result.failures.single().stage)
        assertTrue(result.patterns.isNotEmpty())
        assertTrue(result.processedMessages > 0)
    }
}

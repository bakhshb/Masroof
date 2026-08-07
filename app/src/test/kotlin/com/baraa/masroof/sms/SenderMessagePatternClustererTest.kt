package com.baraa.masroof.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SenderMessagePatternClustererTest {

    @Test
    fun cluster_sameLabelShape_oneCluster_perSender() {
        val purchaseA = """
            شراء
            بمبلغ: 10.00 SAR
            لدى: StoreA
            بطاقة: 1234
        """.trimIndent()
        val purchaseB = """
            شراء
            بمبلغ: 20.00 SAR
            لدى: StoreB
            بطاقة: 1234
        """.trimIndent()
        val transfer = """
            تحويل
            مبلغ التحويل: 100.00 SAR
            إلى: حساب
            الحساب: 5678
        """.trimIndent()
        val messages = listOf(
            sms(1, "AlRajhi", purchaseA),
            sms(2, "AlRajhi", purchaseB),
            sms(3, "AlRajhi", transfer),
        )
        val clusters = SenderMessagePatternClusterer.cluster(messages)
        assertEquals(2, clusters.size)
        val purchase = clusters.first { it.features.typeCues.any { c -> c.contains("شراء") } }
        val xfer = clusters.first { it.features.typeCues.any { c -> c.contains("تحويل") } }
        assertEquals(2, purchase.messageCount)
        assertEquals(1, xfer.messageCount)
        assertTrue(purchase.structureKey != xfer.structureKey)
        assertEquals(purchase.senderKey, xfer.senderKey)
    }

    @Test
    fun structureKey_stableAcrossAmountChanges() {
        val a = SenderMessagePatternLearner.structureKeyFromBody(
            "بمبلغ: 1 SAR\nلدى: A",
        )
        val b = SenderMessagePatternLearner.structureKeyFromBody(
            "بمبلغ: 99 SAR\nلدى: B",
        )
        assertEquals(a, b)
        assertTrue(a.contains("بمبلغ") || a.contains("|"))
    }

    @Test
    fun matcher_structureKeyMatch_acceptsStyle_rejectsOtherStyle() {
        val purchaseBody = """
            شراء
            بمبلغ: 10.00 SAR
            لدى: A
        """.trimIndent()
        val transferBody = """
            تحويل
            مبلغ التحويل: 50.00 SAR
            إلى: B
        """.trimIndent()
        val features = SenderMessagePatternLearner.learnInclude(listOf(purchaseBody))
        val key = SenderMessagePatternLearner.structureKeyFromBody(purchaseBody)
        val pattern = com.baraa.masroof.data.db.SenderMessagePatternEntity(
            id = 1,
            senderKey = "alrajhi",
            structureKey = key,
            accountId = null,
            kind = com.baraa.masroof.data.db.SenderMessagePatternKind.INCLUDE_TRANSACTION,
            amountLabels = features.amountLabels.toList(),
            typeCues = features.typeCues.toList(),
            lineLabels = features.lineLabels.toList(),
            minScore = 1,
            exampleCount = 1,
            active = true,
            createdAt = 0,
            updatedAt = 0,
        )
        assertTrue(SenderMessagePatternMatcher.matchesInclude(purchaseBody, pattern))
        assertFalse(
            SenderMessagePatternMatcher.anyIncludeMatch(transferBody, listOf(pattern)),
        )
        assertEquals(pattern, SenderMessagePatternMatcher.bestIncludeMatch(purchaseBody, listOf(pattern)))
    }

    private fun sms(id: Long, sender: String, body: String) = SmsMessage(
        id = id,
        sender = sender,
        body = body,
        timestamp = id * 1000L,
        matchReason = MatchReason.NONE,
    )
}

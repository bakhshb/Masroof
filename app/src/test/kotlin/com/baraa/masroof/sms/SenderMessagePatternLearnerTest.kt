package com.baraa.masroof.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class SenderMessagePatternLearnerTest {
    @Test
    fun learnInclude_extractsAmountLabelAndIgnoresBalance() {
        val body = """
            شراء عبر الانترنت
            بمبلغ: 51.99 SAR
            لدى: Store
            بطاقة: 7271
            الرصيد المتاح: 1000.00 SAR
        """.trimIndent()
        val features = SenderMessagePatternLearner.learnInclude(listOf(body))
        assertTrue(features.amountLabels.any { it.contains("بمبلغ") || it == "بمبلغ" })
        assertFalse(features.amountLabels.any { it.contains("رصيد") })
        assertTrue(features.typeCues.any { it.contains("شراء") })
        assertTrue(features.lineLabels.contains("بمبلغ") || features.lineLabels.any { it.contains("بمبلغ") })
    }

    @Test
    fun extractAmountUsingLabels_readsLearnedLabelOnly() {
        val body = "قيمة الشراء: 33.03 SAR\nالرصيد المتاح: 900 SAR"
        val amount = SenderMessagePatternLearner.extractAmountUsingLabels(body, setOf("قيمة الشراء"))
        assertEquals(0, BigDecimal("33.03").compareTo(amount))
        assertEquals(null, SenderMessagePatternLearner.extractAmountUsingLabels(body, setOf("الرصيد المتاح")))
    }

    @Test
    fun matcher_acceptsSimilarPurchase_rejectsOtpWithCode() {
        val learned = SenderMessagePatternLearner.learnInclude(
            listOf(
                """
                شراء
                بمبلغ: 10.00 SAR
                لدى: A
                """.trimIndent(),
            ),
        )
        val pattern = com.baraa.masroof.data.db.SenderMessagePatternEntity(
            id = 1,
            senderKey = "alrajhi",
            structureKey = SenderMessagePatternLearner.structureKeyFromFeatures(learned),
            accountId = null,
            kind = com.baraa.masroof.data.db.SenderMessagePatternKind.INCLUDE_TRANSACTION,
            amountLabels = learned.amountLabels.toList(),
            typeCues = learned.typeCues.toList(),
            lineLabels = learned.lineLabels.toList(),
            minScore = 1,
            exampleCount = 1,
            active = true,
            createdAt = 0,
            updatedAt = 0,
        )
        assertTrue(
            SenderMessagePatternMatcher.matchesInclude(
                """
                شراء عبر الانترنت
                بمبلغ: 20.00 SAR
                لدى: B
                """.trimIndent(),
                pattern,
            ),
        )
        assertTrue(
            BankSmsFilter.isOtpOrAuthenticationMessage("رمز التحقق: 123456 لعملية شراء بمبلغ 20"),
        )
        assertFalse(
            BankSmsFilter.isOtpOrAuthenticationMessage(
                """
                شراء
                بمبلغ: 20.00 SAR
                لا تشارك رمز التحقق مع أي شخص
                """.trimIndent(),
            ),
        )
    }
}

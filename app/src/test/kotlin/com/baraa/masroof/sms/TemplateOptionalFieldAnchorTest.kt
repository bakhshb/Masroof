package com.baraa.masroof.sms

import com.baraa.masroof.data.db.PatternVariantAnchorEntity
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateOptionalFieldAnchorTest {
    @Test
    fun optionalBeneficiaryLinesMayBePresentIndependentlyOrAbsent() {
        val required = mapOf("AMOUNT" to true, "BENEFICIARY" to false)
        val anchors = PatternStructure.anchorsFromTemplate(template, required).map { (anchor, isRequired) ->
            PatternVariantAnchorEntity(
                variantId = 1L,
                normalizedAnchor = anchor,
                required = isRequired,
            )
        }

        listOf(
            "حوالة واردة راتب\nمبلغ: 10000 SAR",
            "حوالة واردة راتب\nمبلغ: 10000 SAR\nإلى: شخص",
            "حوالة واردة راتب\nمبلغ: 10000 SAR\nاسم المرسل: جهة",
            "حوالة واردة راتب\nمبلغ: 10000 SAR\nإلى: شخص\nاسم المرسل: جهة",
        ).forEach { body ->
            assertTrue(body, TemplateMatcher.matches(template, body, anchors))
        }
    }

    private companion object {
        const val template = """
            حوالة واردة راتب
            مبلغ: {AMOUNT} SAR
            إلى: {BENEFICIARY}
            اسم المرسل: {BENEFICIARY}
        """
    }
}

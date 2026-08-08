package com.baraa.masroof.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Locks in the editor digit-run and template-capture fixes:
 *  - the validator locates the offending line, run and a sensible suggested
 *    placeholder token inferred from the line's label.
 *  - MessageTemplateEngine.buildFromSms captures the compact-English merchant
 *    on the amount line (no literal "Amazon SA"), correctly templatizes
 *    the date/time line as {DATE}/{TIME} and the bare Arabic `حساب` label
 *    as {ACCOUNT_LAST4}.
 */
class TemplateGenerationAndValidatorFixTest {

    @Test
    fun findSuspiciousDigitRun_returnsNullForCleanTemplate() {
        val clean = "حوالة صادرة\nمن: {SOURCE_ACCOUNT_LAST4}\nمبلغ: {AMOUNT} SAR"
        assertNull(TemplateEditValidator.findSuspiciousDigitRun(clean))
    }

    @Test
    fun findSuspiciousDigitRun_locatesLineAndSuggestsAmount() {
        val tpl = "بمبلغ: 127.00 SAR\nفي: 2026-08-03"
        val f = TemplateEditValidator.findSuspiciousDigitRun(tpl)
        assertNotNull(f)
        assertEquals(2, f!!.lineNumber)
        assertEquals("2026-08-03", f.rawMatch)
        assertEquals("{DATE}", f!!.suggestedPlaceholder)
    }

    @Test
    fun findSuspiciousDigitRun_suggestsAccountLast4ForAccountLabel() {
        val tpl = "حساب: 1234567890"
        val f = TemplateEditValidator.findSuspiciousDigitRun(tpl)
        assertNotNull(f)
        assertEquals("{ACCOUNT_LAST4}", f!!.suggestedPlaceholder)
    }

    @Test
    fun buildFromSms_compactEnglishMerchantBecomesPlaceholder() {
        val body = "Internet Purchase Credit card: 3478 of: 41.30 SAR At Amazon SA on: 2026-08-02 12:04\nAvailable Balance: 17373.27 SAR Due Amount: 1826.12 SAR"
        val t = MessageTemplateEngine.buildFromSms(body)
        assertEquals(false, t.templateText.contains("Amazon SA"))
        assertEquals(true, t.templateText.contains("{MERCHANT}"))
        // Date line is no longer garbled by the LAST4 regex.
        assertEquals(true, t.templateText.contains("{DATE}"))
        assertEquals(true, t.templateText.contains("{TIME}"))
        // Due Amount is its own {TOTAL_DUE} field, not {AVAILABLE_BALANCE}.
        assertEquals(true, t.templateText.contains("{TOTAL_DUE}"))
    }

    @Test
    fun buildFromSms_bareArabicAccountLabelBecomesPlaceholder() {
        val body = "حوالة صادرة الى حسابك الجاري\nحساب: 3001\nمبلغ: SAR 4,445.67"
        val t = MessageTemplateEngine.buildFromSms(body)
        assertEquals(true, t.templateText.contains("{ACCOUNT_LAST4}"))
        assertEquals(false, t.templateText.contains("3001"))
    }
}

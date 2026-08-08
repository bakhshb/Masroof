package com.baraa.masroof.sms

import com.baraa.masroof.data.db.PatternCanonicalField
import com.baraa.masroof.transaction.LineBasedFieldParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks in the value-aware field suggester + card-payment template so the
 * template ↔ fields contract holds automatically (no spurious "unused field"
 * error, and patterns that legitimately need two last4s — card + account —
 * produce both placeholders AND both field definitions).
 */
class CardPaymentAndValueAwareSuggestTest {

    private val cardPaymentBody = """
        سداد بطاقة ائتمانية
        بطاقة ائتمانية: 7271
        من: 3001
        مبلغ: 500.00 SAR
    """.trimIndent()

    /** Body whose first line's label looks like a credit card but whose value
     *  is the type-indicator word `تسديد` (not a 4-digit). The old label-only
     *  suggester would emit a spurious CREDIT_CARD_LAST4 field for this. */
    private val garbledFirstLineBody = """
        بطاقة إئتمانية: تسديد
        بطاقة ائتمانية: 7271
        من: 3001
        مبلغ: 500.00 SAR
    """.trimIndent()

    @Test
    fun cardPaymentTemplate_supportsCreditCardAndSourceAccountLast4() {
        val t = MessageTemplateEngine.buildFromSms(cardPaymentBody)
        assertTrue(
            "template must carry the credit-card last4, got: " + t.templateText,
            t.templateText.contains("{CREDIT_CARD_LAST4}"),
        )
        assertTrue(
            "template must carry the source-account last4, got: " + t.templateText,
            t.templateText.contains("{ACCOUNT_LAST4}"),
        )
        assertTrue(t.templateText.contains("{AMOUNT}"))
    }

    @Test
    fun valueAwareSuggestFields_cardPayment_proposesBothLast4s() {
        val lines = LineBasedFieldParser.splitLines(cardPaymentBody)
        val fields = PatternDiscoveryService.suggestFields(lines)
        val byToken = fields.associateBy { it.canonicalField }
        assertTrue(
            "card-payment must suggest a CREDIT_CARD_LAST4 field, got $byToken",
            PatternCanonicalField.CREDIT_CARD_LAST4 in byToken,
        )
        assertTrue(
            "card-payment must suggest a SOURCE_ACCOUNT_LAST4 field, got $byToken",
            PatternCanonicalField.SOURCE_ACCOUNT_LAST4 in byToken,
        )
        assertTrue(PatternCanonicalField.TRANSACTION_AMOUNT in byToken)
    }

    @Test
    fun valueAwareSuggestFields_garbledFirstLine_doesNotSpuriouslySuggestCreditCard() {
        val lines = LineBasedFieldParser.splitLines(garbledFirstLineBody)
        val fields = PatternDiscoveryService.suggestFields(lines)
        // Only the second line (value 7271) should propose CREDIT_CARD_LAST4;
        // the first line (value "تسديد") must not.
        val cardFields = fields.filter { it.canonicalField == PatternCanonicalField.CREDIT_CARD_LAST4 }
        assertEquals(
            "expected exactly one CREDIT_CARD_LAST4 suggestion (from the 7271 line), " +
                "got ${cardFields.size}: $cardFields",
            1, cardFields.size,
        )
    }

    @Test
    fun valueAwareSuggestFields_doesNotSuggestLast4ForDateOnly() {
        val body = "سداد بطاقة ائتمانية\nفي: 2026-08-03"
        val fields = PatternDiscoveryService.suggestFields(LineBasedFieldParser.splitLines(body))
        assertFalse(
            "no last4 field for a date-only body, got $fields",
            fields.any {
                it.canonicalField == PatternCanonicalField.CREDIT_CARD_LAST4 ||
                    it.canonicalField == PatternCanonicalField.ACCOUNT_LAST4
            },
        )
    }
}

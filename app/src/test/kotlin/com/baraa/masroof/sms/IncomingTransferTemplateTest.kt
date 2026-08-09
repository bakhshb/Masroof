package com.baraa.masroof.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks in the comprehensive engine fix for the user's incoming-transfer
 * example. The template must:
 *  - use distinct {DESTINATION_ACCOUNT_LAST4} vs {SOURCE_ACCOUNT_LAST4}
 *    (no repeated {ACCOUNT_LAST4}),
 *  - capture the sender name as {BENEFICIARY} (not literal),
 *  - keep the bank line literal (it's part of the pattern identity,
 *    emitting {BANK_NAME} would break strict template matching),
 *  - capture the whole reference (incl. any alphanumeric prefix like
 *    "2BTMS") as {TRANSACTION_ID} (not just the digit run),
 *  - trim label trailing whitespace so "خصمت من حساب : 3002" doesn't
 *    leak the space-before-colon into the template.
 */
class IncomingTransferTemplateTest {

    private val body = """
        حوالة واردة
        أودعت إلى حساب: 3003
        القيمة: SAR 4,445.67
        من: نجاه ط. بنتن
        [بنك الرياض]
        خصمت من حساب: 3002
        في: 2026-08-03 10:38
        رقم المعاملة: 2BTMS11432672163
    """.trimIndent()

    @Test
    fun incomingTransferTemplate_hasDistinctSourceAndDestinationAccountTokens() {
        val t = MessageTemplateEngine.buildFromSms(body)
        assertTrue(
            "template must carry the destination-account last4, got: " + t.templateText,
            t.templateText.contains("{DESTINATION_ACCOUNT_LAST4}"),
        )
        assertTrue(
            "template must carry the source-account last4, got: " + t.templateText,
            t.templateText.contains("{SOURCE_ACCOUNT_LAST4}"),
        )
        // No repeating generic {ACCOUNT_LAST4} — the resolver/matcher would
        // only see one value for the token.
        assertFalse(
            "template must not use the generic {ACCOUNT_LAST4} token when both " +
                "source and destination are present, got: " + t.templateText,
            t.templateText.contains("{ACCOUNT_LAST4}"),
        )
    }

    @Test
    fun incomingTransferTemplate_capturesSenderNameAsBeneficiary() {
        val t = MessageTemplateEngine.buildFromSms(body)
        assertTrue(
            "sender name 'نجاه ط. بنتن' must be captured as {BENEFICIARY}, got: " +
                t.templateText,
            t.templateText.contains("{BENEFICIARY}"),
        )
        assertFalse(
            "literal sender name must not leak into the template, got: " + t.templateText,
            t.templateText.contains("نجاه"),
        )
    }

    @Test
    fun incomingTransferTemplate_keepsBankLineLiteral() {
        val t = MessageTemplateEngine.buildFromSms(body)
        // Bank line stays literal: it's the pattern's counterparty identity.
        // Emitting {BANK_NAME} would break strict template matching because
        // the template label wouldn't match the body label.
        assertTrue(
            "bank line must stay literal, got: " + t.templateText,
            t.templateText.contains("[بنك الرياض]"),
        )
    }

    @Test
    fun incomingTransferTemplate_capturesWholeReferenceIncludingPrefix() {
        val t = MessageTemplateEngine.buildFromSms(body)
        // The entire reference "2BTMS11432672163" must be captured, not just
        // the digit run with the prefix left literal.
        assertTrue(
            "reference must be captured as {TRANSACTION_ID}, got: " + t.templateText,
            t.templateText.contains("{TRANSACTION_ID}"),
        )
        assertFalse(
            "literal '2BTMS' prefix must not leak into the template, got: " +
                t.templateText,
            t.templateText.contains("2BTMS"),
        )
    }

    @Test
    fun incomingTransferTemplate_trimsLabelTrailingWhitespace() {
        // Use a body that has a space before the colon on a labeled line so we
        // can verify the template doesn't echo the stray space.
        val t = MessageTemplateEngine.buildFromSms(
            "حوالة واردة\nخصمت من حساب : 3002\nفي: 2026-08-03"
        )
        // The label in the template should be trimmed (no trailing space
        // before the colon).
        assertFalse(
            "label trailing whitespace before colon must be trimmed, got: " +
                t.templateText,
            t.templateText.contains(" : "),
        )
    }

    @Test
    fun incomingTransferTemplate_bracesAreBalanced() {
        val t = MessageTemplateEngine.buildFromSms(body)
        assertEquals(
            "open/close braces must balance",
            t.templateText.count { it == '{' },
            t.templateText.count { it == '}' },
        )
    }
}

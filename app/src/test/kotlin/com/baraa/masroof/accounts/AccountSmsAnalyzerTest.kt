package com.baraa.masroof.accounts

import com.baraa.masroof.data.db.AccountIdentifierType
import com.baraa.masroof.sms.SmsMessage
import com.baraa.masroof.transaction.AccountType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountSmsAnalyzerTest {
    @Test fun pickerPreviewKeepsLastFourWhileMaskingLongRunsAndOtpBalance() {
        val preview = AccountSmsAnalyzer.sanitizedPreview(
            """
            رمز التحقق 884422
            الرصيد 12345
            شراء بمبلغ 51.99 بطاقة 7271 ومبلغ إضافي 99999
            """.trimIndent(),
        )
        org.junit.Assert.assertFalse(preview.contains("884422"))
        org.junit.Assert.assertFalse(preview.contains("12345"))
        org.junit.Assert.assertFalse(preview.contains("99999"))
        org.junit.Assert.assertTrue(preview.contains("••••7271"))
        org.junit.Assert.assertTrue(preview.contains("••••9999"))
    }

    @Test fun creditCardLabelProducesOnlyCreditCardIdentifierForCardAccount() {
        val result = AccountSmsAnalyzer.analyze(
            SmsMessage(1, "SNB", "شراء ببطاقة ائتمانية: 7271 بمبلغ: 51.99 SAR", 1L),
            AccountType.CREDIT_CARD
        )
        assertEquals(result.toString(), AccountIdentifierType.CREDIT_CARD_LAST4, result?.identifierType)
        assertEquals("7271", result?.lastFour)
    }

    @Test fun creditCardEvidenceIsNotSavedOnBankAccount() {
        val result = AccountSmsAnalyzer.analyze(
            SmsMessage(1, "SNB", "شراء ببطاقة ائتمانية: 7271 بمبلغ: 51.99 SAR", 1L),
            AccountType.BANK_ACCOUNT
        )
        assertNull(result?.identifierType)
    }
}

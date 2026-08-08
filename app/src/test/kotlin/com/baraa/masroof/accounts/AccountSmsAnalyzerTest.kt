package com.baraa.masroof.accounts

import com.baraa.masroof.data.db.AccountIdentifierType
import com.baraa.masroof.data.db.MessagePatternDefinitionEntity
import com.baraa.masroof.data.db.MessagePatternStatus
import com.baraa.masroof.data.db.PatternCanonicalField
import com.baraa.masroof.data.db.PatternFieldDefinitionEntity
import com.baraa.masroof.data.db.PatternFieldRole
import com.baraa.masroof.data.db.PatternValueType
import com.baraa.masroof.data.repository.MessagePattern
import com.baraa.masroof.sms.SmsMessage
import com.baraa.masroof.transaction.AccountType
import com.baraa.masroof.transaction.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountSmsAnalyzerTest {
    private val creditCardPattern = MessagePattern(
        MessagePatternDefinitionEntity(
            id = 1,
            senderProfileId = 1,
            userFriendlyName = "شراء",
            normalizedSignature = "sig",
            canonicalKey = "purchase-card",
            templateText = "شراء ببطاقة ائتمانية: {CREDIT_CARD_LAST4} بمبلغ: {AMOUNT} SAR",
            transactionType = TransactionType.PURCHASE.name,
            status = MessagePatternStatus.APPROVED,
            isActive = true,
            createdAt = 0,
            updatedAt = 0,
        ),
        listOf(
            PatternFieldDefinitionEntity(
                patternId = 1,
                canonicalField = PatternCanonicalField.CREDIT_CARD_LAST4,
                placeholderToken = "CREDIT_CARD_LAST4",
                sourceLabel = "بطاقة ائتمانية",
                role = PatternFieldRole.SOURCE,
                valueType = PatternValueType.LAST4,
            ),
            PatternFieldDefinitionEntity(
                patternId = 1,
                canonicalField = PatternCanonicalField.TRANSACTION_AMOUNT,
                placeholderToken = "AMOUNT",
                sourceLabel = "مبلغ",
                valueType = PatternValueType.MONEY,
            ),
        ),
    )

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
            AccountType.CREDIT_CARD,
            listOf(creditCardPattern),
        )
        assertEquals(result.toString(), AccountIdentifierType.CREDIT_CARD_LAST4, result?.identifierType)
        assertEquals("7271", result?.lastFour)
    }

    @Test fun creditCardEvidenceIsNotSavedOnBankAccount() {
        val result = AccountSmsAnalyzer.analyze(
            SmsMessage(1, "SNB", "شراء ببطاقة ائتمانية: 7271 بمبلغ: 51.99 SAR", 1L),
            AccountType.BANK_ACCOUNT,
            listOf(creditCardPattern),
        )
        assertNull(result?.identifierType)
    }
}

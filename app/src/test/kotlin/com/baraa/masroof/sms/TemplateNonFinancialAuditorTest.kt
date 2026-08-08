package com.baraa.masroof.sms

import com.baraa.masroof.data.db.MessagePatternDefinitionEntity
import com.baraa.masroof.data.db.MessagePatternStatus
import com.baraa.masroof.data.db.PatternCanonicalField
import com.baraa.masroof.data.db.PatternFieldDefinitionEntity
import com.baraa.masroof.data.repository.MessagePattern
import com.baraa.masroof.transaction.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TemplateNonFinancialAuditorTest {

    @Test
    fun limitChangeMisclassifiedAsOnlinePurchaseIsFlagged() {
        val def = MessagePatternDefinitionEntity(
            id = 9L,
            senderProfileId = 1L,
            userFriendlyName = "شراء عبر الإنترنت",
            normalizedSignature = "sig",
            canonicalKey = "key",
            templateText = "عزيزي العميل\nبناء على طلبكم، تم تغيير الحد اليومي للشراء عبر الانترنت الخاص بك",
            transactionType = TransactionType.ONLINE_PURCHASE.name,
            status = MessagePatternStatus.APPROVED,
            createdAt = 0L,
            updatedAt = 0L,
        )
        val finding = TemplateNonFinancialAuditor.audit(MessagePattern(def, emptyList()))
        assertNotNull(finding)
        assertEquals(TransactionType.NON_FINANCIAL, finding!!.suggestedType)
        assertEquals(
            "audit is suggestion-only and cannot alter the definition",
            MessagePatternStatus.APPROVED,
            def.status,
        )
    }

    @Test
    fun genuinePurchaseWithAmountIsNotFlagged() {
        val def = MessagePatternDefinitionEntity(
            id = 2L,
            senderProfileId = 1L,
            userFriendlyName = "شراء",
            normalizedSignature = "sig",
            canonicalKey = "key",
            templateText = "شراء عبر نقاط البيع\nمبلغ: {AMOUNT}\nلدى: {MERCHANT}",
            transactionType = TransactionType.PURCHASE.name,
            status = MessagePatternStatus.APPROVED,
            createdAt = 0L,
            updatedAt = 0L,
        )
        assertNull(TemplateNonFinancialAuditor.audit(MessagePattern(def, emptyList())))
    }

    @Test
    fun amountFieldDefinitionPreventsMissingAmountFalsePositive() {
        val def = MessagePatternDefinitionEntity(
            id = 3L,
            senderProfileId = 1L,
            userFriendlyName = "شراء",
            normalizedSignature = "sig",
            canonicalKey = "key",
            templateText = "شراء عبر نقاط البيع",
            transactionType = TransactionType.PURCHASE.name,
            status = MessagePatternStatus.APPROVED,
            createdAt = 0L,
            updatedAt = 0L,
        )
        val amount = PatternFieldDefinitionEntity(
            patternId = def.id,
            canonicalField = PatternCanonicalField.TRANSACTION_AMOUNT,
            placeholderToken = "AMOUNT",
            sourceLabel = "مبلغ",
        )
        assertNull(TemplateNonFinancialAuditor.audit(MessagePattern(def, listOf(amount))))
    }
}

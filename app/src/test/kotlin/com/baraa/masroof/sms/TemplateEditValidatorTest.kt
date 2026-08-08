package com.baraa.masroof.sms

import com.baraa.masroof.data.db.MessagePatternStatus
import com.baraa.masroof.data.db.PatternCanonicalField
import com.baraa.masroof.data.db.PatternFieldRole
import com.baraa.masroof.data.db.PatternValueType
import com.baraa.masroof.transaction.MoneyFlowDirection
import com.baraa.masroof.transaction.TransactionType
import com.baraa.masroof.transaction.TransactionTypeTaxonomy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateEditValidatorTest {

    private fun draft(
        type: TransactionType = TransactionType.PURCHASE,
        status: MessagePatternStatus = MessagePatternStatus.APPROVED,
        template: String = "مبلغ: {AMOUNT}",
        fields: List<TemplateFieldDraft> = listOf(
            TemplateFieldDraft(
                placeholderToken = "AMOUNT",
                canonicalField = PatternCanonicalField.TRANSACTION_AMOUNT,
                sourceLabel = "مبلغ",
                role = PatternFieldRole.PRIMARY,
                valueType = PatternValueType.MONEY,
                required = true,
            ),
        ),
        active: Boolean = status == MessagePatternStatus.APPROVED,
        direction: MoneyFlowDirection = TransactionTypeTaxonomy.directionOf(type),
    ) = TemplateEditDraft(
        patternId = 1L,
        senderProfileId = 1L,
        displayName = "شراء",
        transactionType = type,
        direction = direction,
        templateText = template,
        status = status,
        active = active,
        fields = fields,
    )

    @Test
    fun financialTemplateRequiresAmountToApprove() {
        val result = TemplateEditValidator.validate(
            draft(template = "عزيزي العميل تم تغيير الحد", fields = emptyList()),
        )
        assertTrue(result is TemplateEditValidation.Error)
    }

    @Test
    fun amountDefinitionWithMatchingPlaceholderAllowsApproval() {
        assertEquals(TemplateEditValidation.Ok, TemplateEditValidator.validate(draft()))
    }

    @Test
    fun amountFieldAllowsApproval() {
        val result = TemplateEditValidator.validate(
            draft(
                template = "عملية {AMOUNT}",
                fields = listOf(
                    TemplateFieldDraft(
                        placeholderToken = "AMOUNT",
                        canonicalField = PatternCanonicalField.TRANSACTION_AMOUNT,
                        sourceLabel = "مبلغ",
                        role = PatternFieldRole.PRIMARY,
                        valueType = PatternValueType.MONEY,
                        required = true,
                    ),
                ),
            ),
        )
        assertEquals(TemplateEditValidation.Ok, result)
    }

    @Test
    fun nonFinancialCanApproveWithoutAmount() {
        assertEquals(
            TemplateEditValidation.Ok,
            TemplateEditValidator.validate(
                draft(
                    type = TransactionType.NON_FINANCIAL,
                    template = "رسالة معلوماتية",
                    fields = emptyList(),
                ),
            ),
        )
    }

    @Test
    fun placeholderDefinitionsMustHaveExactParity() {
        val result = TemplateEditValidator.validate(
            draft(template = "مبلغ: {AMOUNT} لدى {MERCHANT}"),
        )
        assertTrue(result is TemplateEditValidation.Error)
    }

    @Test
    fun approvedTemplateMayBeInactiveWithoutChangingStatus() {
        assertEquals(
            TemplateEditValidation.Ok,
            TemplateEditValidator.validate(draft(active = false)),
        )
    }

    @Test
    fun activeUnknownTemplateIsRejected() {
        val result = TemplateEditValidator.validate(
            draft(status = MessagePatternStatus.UNKNOWN, active = true),
        )
        assertTrue(result is TemplateEditValidation.Error)
    }

    @Test
    fun staticPersonalNumberIsRejected() {
        val result = TemplateEditValidator.validate(
            draft(template = "مبلغ: {AMOUNT} بطاقة 4111 1111 1111 1111"),
        )
        assertTrue(result is TemplateEditValidation.Error)
    }
}

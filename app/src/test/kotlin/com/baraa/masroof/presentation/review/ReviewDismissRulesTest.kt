package com.baraa.masroof.presentation.review

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.MessageFamily
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewDismissRulesTest {
    @Test
    fun unknownWithoutAmountOrMoneyWording_isDismissible() {
        assertTrue(
            shouldOfferNonFinancialDismiss(
                messageFamily = MessageFamily.UNKNOWN,
                reasons = listOf("unknown_message_family"),
                body = "اسم المستفيد : TEST\nحالة: غير نشط",
            ),
        )
    }

    @Test
    fun unknownWithParsedAmount_isNotAutoDismissible() {
        assertFalse(
            shouldOfferNonFinancialDismiss(
                messageFamily = MessageFamily.UNKNOWN,
                reasons = listOf("unknown_message_family"),
                body = "عملية غير معروفة",
                amount = Money.of("100", Currency.SAR),
            ),
        )
    }

    @Test
    fun statementNoticeWithZeroAmount_isDismissible() {
        assertTrue(
            shouldOfferNonFinancialDismiss(
                messageFamily = MessageFamily.UNKNOWN,
                reasons = listOf("unknown_message_family"),
                body = """
                    بطاقة إئتمانية: إصدار كشف حساب
                    إجمالي المبلغ المستحق: SAR 0.00
                    تاريخ الاستحقاق: 07/09/2026
                """.trimIndent(),
                amount = Money.of("0.00", Currency.SAR),
            ),
        )
    }
}

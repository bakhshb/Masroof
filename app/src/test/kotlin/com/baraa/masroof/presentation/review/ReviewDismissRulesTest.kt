package com.baraa.masroof.presentation.review

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
                hasAmount = false,
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
                hasAmount = true,
            ),
        )
    }

    @Test
    fun unknownWithMoneyWordingButNoParsedAmount_staysForManualClassification() {
        assertFalse(
            shouldOfferNonFinancialDismiss(
                messageFamily = MessageFamily.UNKNOWN,
                reasons = listOf("unknown_message_family"),
                body = "عملية بمبلغ: 15000.00 SAR",
                hasAmount = false,
            ),
        )
    }
}

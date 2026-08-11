package com.baraa.masroof.domain.rules

import com.baraa.masroof.domain.model.MessageFamily
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InformationalMessagePolicyTest {
    @Test
    fun unknownWithoutAmountOrMoneyWording_isAutoIgnored() {
        assertTrue(
            InformationalMessagePolicy.shouldAutoIgnore(
                messageFamily = MessageFamily.UNKNOWN,
                hasParsedAmount = false,
                smsBody = "اسم المستفيد : TEST\nحالة: غير نشط",
            ),
        )
    }

    @Test
    fun unknownWithParsedAmount_isNotAutoIgnored() {
        assertFalse(
            InformationalMessagePolicy.shouldAutoIgnore(
                messageFamily = MessageFamily.UNKNOWN,
                hasParsedAmount = true,
                smsBody = "عملية غير معروفة",
            ),
        )
    }

    @Test
    fun unknownWithMoneyWordingButNoParsedAmount_staysForReview() {
        assertFalse(
            InformationalMessagePolicy.shouldAutoIgnore(
                messageFamily = MessageFamily.UNKNOWN,
                hasParsedAmount = false,
                smsBody = "عملية بمبلغ: 15000.00 SAR",
            ),
        )
    }

    @Test
    fun nonFinancialFamily_isAutoIgnored() {
        assertTrue(
            InformationalMessagePolicy.shouldAutoIgnore(
                messageFamily = MessageFamily.NON_FINANCIAL,
                hasParsedAmount = false,
                smsBody = "any",
            ),
        )
    }
}

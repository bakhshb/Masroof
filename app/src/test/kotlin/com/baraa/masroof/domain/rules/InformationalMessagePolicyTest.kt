package com.baraa.masroof.domain.rules

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
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
                parsedAmount = null,
                smsBody = "اسم المستفيد : TEST\nحالة: غير نشط",
            ),
        )
    }

    @Test
    fun unknownWithParsedAmount_isNotAutoIgnored() {
        assertFalse(
            InformationalMessagePolicy.shouldAutoIgnore(
                messageFamily = MessageFamily.UNKNOWN,
                parsedAmount = Money.of("100", Currency.SAR),
                smsBody = "عملية غير معروفة",
            ),
        )
    }

    @Test
    fun unknownWithMoneyWordingButNoParsedAmount_staysForReview() {
        assertFalse(
            InformationalMessagePolicy.shouldAutoIgnore(
                messageFamily = MessageFamily.UNKNOWN,
                parsedAmount = null,
                smsBody = "عملية بمبلغ: 15000.00 SAR",
            ),
        )
    }

    @Test
    fun creditCardStatementWithZeroDue_isAutoIgnored() {
        assertTrue(
            InformationalMessagePolicy.shouldAutoIgnore(
                messageFamily = MessageFamily.UNKNOWN,
                parsedAmount = Money.of("0.00", Currency.SAR),
                smsBody = """
                    بطاقة إئتمانية: إصدار كشف حساب
                    بطاقة: 7271 بطاقة إئتمانية
                    إجمالي المبلغ المستحق: SAR 0.00
                    تاريخ الاستحقاق: 07/09/2026
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun nonFinancialFamily_isAutoIgnored() {
        assertTrue(
            InformationalMessagePolicy.shouldAutoIgnore(
                messageFamily = MessageFamily.NON_FINANCIAL,
                parsedAmount = null,
                smsBody = "any",
            ),
        )
    }
}

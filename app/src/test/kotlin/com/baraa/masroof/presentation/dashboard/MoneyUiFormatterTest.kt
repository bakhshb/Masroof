package com.baraa.masroof.presentation.dashboard

import com.baraa.masroof.application.locale.AppLocale
import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyUiFormatterTest {
    @Test
    fun format_englishUsesSarLabel() {
        val formatted = MoneyUiFormatter.format(Money.of("1234.50", Currency.SAR), AppLocale.TAG_EN)
        assertTrue(formatted.contains("SAR"))
        assertTrue(formatted.contains("1,234.50"))
    }

    @Test
    fun format_arabicUsesRiyalSymbol() {
        val formatted = MoneyUiFormatter.format(Money.of("1234.50", Currency.SAR), AppLocale.TAG_AR)
        assertTrue(formatted.contains("ر.س"))
        assertTrue(formatted.contains("٥٠") || formatted.contains("50"))
    }

    @Test
    fun currencyLabel_respectsLanguage() {
        assertEquals("SAR", MoneyUiFormatter.currencyLabel(Currency.SAR, AppLocale.TAG_EN))
        assertEquals("ر.س", MoneyUiFormatter.currencyLabel(Currency.SAR, AppLocale.TAG_AR))
    }
}

package com.baraa.masroof.bank.aljazira.extraction

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MoneyTokensTest {
    @Test
    fun parsesEurAndGbpAmounts() {
        val eur = MoneyTokens.moneyAfterLabel.find("بمبلغ: EUR 50.00")!!
        assertEquals(Money.of("50.00", Currency.EUR), MoneyTokens.parseMoneyFromMatch(eur))

        val gbp = MoneyTokens.moneyAfterLabel.find("amount: 30.00 GBP")!!
        assertNotNull(gbp)
        assertEquals(Money.of("30.00", Currency.GBP), MoneyTokens.parseMoneyFromMatch(gbp))
    }
}

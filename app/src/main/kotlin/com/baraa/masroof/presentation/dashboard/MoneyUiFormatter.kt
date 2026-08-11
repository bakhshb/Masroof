package com.baraa.masroof.presentation.dashboard

import com.baraa.masroof.application.dashboard.SignedMoneyAmount
import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object MoneyUiFormatter {
    fun format(money: Money): String = format(money.amount, money.currency)

    fun format(signed: SignedMoneyAmount): String = format(signed.amount, signed.currency)

    fun format(amount: BigDecimal, currency: Currency): String {
        val symbols = DecimalFormatSymbols(Locale.US).apply {
            groupingSeparator = ','
            decimalSeparator = '.'
        }
        val formatter = DecimalFormat("#,##0.00", symbols)
        formatter.minimumFractionDigits = Money.SCALE
        formatter.maximumFractionDigits = Money.SCALE
        val number = formatter.format(amount)
        return "$number ${currencyLabel(currency)}"
    }

    fun currencyLabel(currency: Currency): String =
        when (currency) {
            Currency.SAR -> "ر.س"
            Currency.USD -> "USD"
        }
}

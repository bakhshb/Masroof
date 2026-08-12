package com.baraa.masroof.presentation.locale

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.baraa.masroof.application.locale.AppLocale
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.presentation.dashboard.MoneyUiFormatter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object LocaleDisplayFormatters {
    fun localizedContext(context: Context): Context =
        AppLocaleContext.wrap(context, AppLocaleContext.readStoredLanguageTag(context))

    fun money(money: Money, languageTag: String): String =
        MoneyUiFormatter.format(money, languageTag)

    fun transactionDate(date: LocalDate, languageTag: String, pattern: String = "d MMM"): String =
        DateTimeFormatter.ofPattern(pattern, AppLocale.displayLocale(languageTag)).format(date)

    fun currentLanguageTag(context: Context): String =
        AppLocaleContext.readStoredLanguageTag(context)
}

@Composable
fun formatLocalizedMoney(money: Money): String {
    val languageTag = LocalConfiguration.current.locales[0].toLanguageTag()
    return MoneyUiFormatter.format(money, languageTag)
}

@Composable
fun formatLocalizedMoney(signed: com.baraa.masroof.application.dashboard.SignedMoneyAmount): String {
    val languageTag = LocalConfiguration.current.locales[0].toLanguageTag()
    return MoneyUiFormatter.format(signed, languageTag)
}

@Composable
fun formatLocalizedTransactionDate(
    date: LocalDate,
    pattern: String = "d MMM",
): String {
    val locale: Locale = LocalConfiguration.current.locales[0]
    return DateTimeFormatter.ofPattern(pattern, locale).format(date)
}

@Composable
fun localizedContext(): Context =
    LocaleDisplayFormatters.localizedContext(LocalContext.current)

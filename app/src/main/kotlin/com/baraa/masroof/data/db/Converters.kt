package com.baraa.masroof.data.db

import androidx.room.TypeConverter
import com.baraa.masroof.transaction.Currency
import com.baraa.masroof.transaction.TransactionStatus
import com.baraa.masroof.transaction.TransactionType
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

/**
 * Room [TypeConverter]s for the small set of types we persist that Room does
 * not handle out of the box. The string representations chosen are stable so
 * they round-trip without loss of precision (notably [BigDecimal] via
 * [BigDecimal.toPlainString] to avoid scientific notation).
 */
class Converters {

    // -- LocalDate / LocalTime ------------------------------------------------

    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let { LocalDate.parse(it) }

    @TypeConverter
    fun fromLocalTime(value: LocalTime?): String? = value?.toString()

    @TypeConverter
    fun toLocalTime(value: String?): LocalTime? = value?.let { LocalTime.parse(it) }

    // -- BigDecimal (precise) -------------------------------------------------

    @TypeConverter
    fun fromBigDecimal(value: BigDecimal?): String? = value?.toPlainString()

    @TypeConverter
    fun toBigDecimal(value: String?): BigDecimal? = value?.let { BigDecimal(it) }

    // -- List<String> (parsing notes) ----------------------------------------

    @TypeConverter
    fun fromStringList(value: List<String>): String = value.joinToString(separator = "\n")

    @TypeConverter
    fun toStringList(value: String): List<String> =
        if (value.isEmpty()) emptyList() else value.split("\n")

    // -- Enums ----------------------------------------------------------------

    @TypeConverter
    fun fromTransactionType(value: TransactionType): String = value.name

    @TypeConverter
    fun toTransactionType(value: String): TransactionType = TransactionType.valueOf(value)

    @TypeConverter
    fun fromTransactionStatus(value: TransactionStatus): String = value.name

    @TypeConverter
    fun toTransactionStatus(value: String): TransactionStatus = TransactionStatus.valueOf(value)

    @TypeConverter
    fun fromCurrency(value: Currency): String = value.name

    @TypeConverter
    fun toCurrency(value: String): Currency = Currency.valueOf(value)

    @TypeConverter
    fun fromDateSource(value: DateSource): String = value.name

    @TypeConverter
    fun toDateSource(value: String): DateSource = DateSource.valueOf(value)
}

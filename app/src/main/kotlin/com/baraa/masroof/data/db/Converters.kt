package com.baraa.masroof.data.db

import androidx.room.TypeConverter
import com.baraa.masroof.ledger.AccountLinkSource
import com.baraa.masroof.ledger.JournalGeneratedBy
import com.baraa.masroof.ledger.JournalPostingStatus
import com.baraa.masroof.ledger.JournalType
import com.baraa.masroof.ledger.PostingSide
import com.baraa.masroof.ledger.SystemAccountKey
import com.baraa.masroof.ledger.TransactionPostingStatus
import com.baraa.masroof.transaction.AccountNature
import com.baraa.masroof.transaction.AccountType
import com.baraa.masroof.transaction.CategorySource
import com.baraa.masroof.transaction.Currency
import com.baraa.masroof.transaction.FinancialTreatment
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

    @TypeConverter
    fun fromFinancialTreatment(value: FinancialTreatment): String = value.name

    @TypeConverter
    fun toFinancialTreatment(value: String): FinancialTreatment = FinancialTreatment.valueOf(value)

    @TypeConverter
    fun fromCategorySource(value: CategorySource): String = value.name

    @TypeConverter
    fun toCategorySource(value: String): CategorySource = CategorySource.valueOf(value)

    @TypeConverter
    fun fromAccountType(value: AccountType): String = value.name

    @TypeConverter
    fun toAccountType(value: String): AccountType = AccountType.valueOf(value)

    @TypeConverter fun fromAccountNature(value: AccountNature): String = value.name
    @TypeConverter fun toAccountNature(value: String): AccountNature = AccountNature.valueOf(value)
    @TypeConverter fun fromSystemAccountKey(value: SystemAccountKey?): String? = value?.name
    @TypeConverter fun toSystemAccountKey(value: String?): SystemAccountKey? = value?.let(SystemAccountKey::valueOf)
    @TypeConverter fun fromJournalType(value: JournalType): String = value.name
    @TypeConverter fun toJournalType(value: String): JournalType = JournalType.valueOf(value)
    @TypeConverter fun fromJournalStatus(value: JournalPostingStatus): String = value.name
    @TypeConverter fun toJournalStatus(value: String): JournalPostingStatus = JournalPostingStatus.valueOf(value)
    @TypeConverter fun fromPostingSide(value: PostingSide): String = value.name
    @TypeConverter fun toPostingSide(value: String): PostingSide = PostingSide.valueOf(value)
    @TypeConverter fun fromGeneratedBy(value: JournalGeneratedBy): String = value.name
    @TypeConverter fun toGeneratedBy(value: String): JournalGeneratedBy = JournalGeneratedBy.valueOf(value)
    @TypeConverter fun fromAccountLinkSource(value: AccountLinkSource): String = value.name
    @TypeConverter fun toAccountLinkSource(value: String): AccountLinkSource = AccountLinkSource.valueOf(value)
    @TypeConverter fun fromTransactionPostingStatus(value: TransactionPostingStatus): String = value.name
    @TypeConverter fun toTransactionPostingStatus(value: String): TransactionPostingStatus = TransactionPostingStatus.valueOf(value)
    @TypeConverter fun fromOpeningBalanceKind(value: OpeningBalanceKind): String = value.name
    @TypeConverter fun toOpeningBalanceKind(value: String): OpeningBalanceKind = OpeningBalanceKind.valueOf(value)

    @TypeConverter
    fun fromSenderMessagePatternKind(value: SenderMessagePatternKind): String = value.name

    @TypeConverter
    fun toSenderMessagePatternKind(value: String): SenderMessagePatternKind =
        SenderMessagePatternKind.valueOf(value)

    @TypeConverter fun fromMessagePatternStatus(value: MessagePatternStatus): String = value.name
    @TypeConverter fun toMessagePatternStatus(value: String): MessagePatternStatus = MessagePatternStatus.valueOf(value)
    @TypeConverter fun fromPatternOrigin(value: PatternOrigin): String = value.name
    @TypeConverter fun toPatternOrigin(value: String): PatternOrigin = PatternOrigin.valueOf(value)
    @TypeConverter fun fromPatternCanonicalField(value: PatternCanonicalField): String = value.name
    @TypeConverter fun toPatternCanonicalField(value: String): PatternCanonicalField = PatternCanonicalField.valueOf(value)
    @TypeConverter fun fromPatternFieldRole(value: PatternFieldRole): String = value.name
    @TypeConverter fun toPatternFieldRole(value: String): PatternFieldRole = PatternFieldRole.valueOf(value)
    @TypeConverter fun fromPatternValueType(value: PatternValueType): String = value.name
    @TypeConverter fun toPatternValueType(value: String): PatternValueType = PatternValueType.valueOf(value)
    @TypeConverter fun fromPatternExtractionStrategy(value: PatternExtractionStrategy): String = value.name
    @TypeConverter fun toPatternExtractionStrategy(value: String): PatternExtractionStrategy =
        PatternExtractionStrategy.valueOf(value)
}

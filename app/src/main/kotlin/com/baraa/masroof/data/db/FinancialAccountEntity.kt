package com.baraa.masroof.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.baraa.masroof.transaction.AccountNature
import com.baraa.masroof.transaction.AccountType
import com.baraa.masroof.transaction.Currency
import java.math.BigDecimal

/**
 * A financial account the user owns. Used by the [com.baraa.masroof.rules.InternalTransferRule]
 * to detect transfers between two of the user's own accounts (e.g. from
 * their checking account to their savings), and by the liquidity /
 * net-worth calculation services.
 *
 * **No full numbers are stored.** Only the last 4 digits + a list of
 * normalized sender aliases that identify incoming SMS for this account.
 *
 * Opening-balance fields:
 *  - [openingBalance] is a [BigDecimal] (never Float / Double). For
 *    assets this represents the amount owned; for liabilities it
 *    represents the amount owed (entered as a positive number).
 *  - [openingBalanceDate] is the date the opening balance was recorded.
 *  - [includeInNetWorth] / [includeInLiquidity] are user-tunable
 *    toggles with sensible defaults per account type.
 *
 * The opening balance is **not** auto-updated from transactions in this
 * task. Future tasks will reconcile transactions against these balances.
 */
@Entity(tableName = "financial_accounts")
data class FinancialAccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val displayName: String,
    val institutionName: String?,
    val accountType: AccountType,
    val accountNature: AccountNature,
    val lastFourDigits: String?,
    /** Comma-separated normalized sender identifiers. */
    val senderAliases: String = "",
    val currency: Currency = Currency.SAR,
    /**
     * Opening balance. For assets = amount owned; for liabilities =
     * amount owed (entered as a positive number). The accounts service
     * subtracts liabilities from net worth.
     */
    val openingBalance: BigDecimal = BigDecimal.ZERO,
    /**
     * The date the opening balance was recorded. Long (epoch millis) so
     * Room can store it without a custom Date type converter.
     */
    val openingBalanceDate: Long = 0L,
    val includeInNetWorth: Boolean = true,
    val includeInLiquidity: Boolean = false,
    val isOwnedByUser: Boolean = true,
    val isActive: Boolean = true,
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Domain-level read model. */
data class FinancialAccount(
    val id: Long,
    val displayName: String,
    val institutionName: String?,
    val accountType: AccountType,
    val accountNature: AccountNature,
    val lastFourDigits: String?,
    val senderAliases: List<String>,
    val currency: Currency,
    val openingBalance: BigDecimal,
    val openingBalanceDate: Long,
    val includeInNetWorth: Boolean,
    val includeInLiquidity: Boolean,
    val isOwnedByUser: Boolean,
    val isActive: Boolean,
    val notes: String?,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

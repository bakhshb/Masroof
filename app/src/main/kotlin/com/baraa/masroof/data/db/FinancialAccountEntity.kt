package com.baraa.masroof.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.baraa.masroof.transaction.AccountType

/**
 * A financial account the user owns. Used by the [com.baraa.masroof.rules.InternalTransferRule]
 * to detect transfers between two of the user's own accounts (e.g. from
 * their checking account to their savings).
 *
 * **No full numbers are stored.** Only the last 4 digits + a list of
 * normalized sender aliases that identify incoming SMS for this account.
 */
@Entity(tableName = "financial_accounts")
data class FinancialAccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val displayName: String,
    val institutionName: String?,
    val accountType: AccountType,
    val lastFourDigits: String?,
    /** Comma-separated normalized sender identifiers. */
    val senderAliases: String = "",
    val isOwnedByUser: Boolean = true,
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Domain-level read model. */
data class FinancialAccount(
    val id: Long,
    val displayName: String,
    val institutionName: String?,
    val accountType: AccountType,
    val lastFourDigits: String?,
    val senderAliases: List<String>,
    val isOwnedByUser: Boolean,
    val isActive: Boolean,
)

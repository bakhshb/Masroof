package com.baraa.masroof.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.baraa.masroof.transaction.AccountType
import com.baraa.masroof.transaction.FinancialTreatment
import com.baraa.masroof.transaction.TransactionType

/** Safe learned linking signature: never contains SMS text, amounts, or account identifiers. */
@Entity(tableName = "account_link_rules", indices = [Index(value = ["signature"], unique = true), Index(value = ["accountId"])])
data class AccountLinkRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val signature: String,
    val senderKey: String,
    val institutionKey: String?,
    val parserName: String,
    val transactionType: TransactionType,
    val financialTreatment: FinancialTreatment,
    val channel: String,
    val direction: String,
    val expectedAccountType: AccountType,
    val accountId: Long,
    val confirmationCount: Int = 1,
    val lastConfirmedAt: Long,
    val active: Boolean = true,
)

package com.baraa.masroof.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class AccountIdentifierType { ACCOUNT_LAST4, CREDIT_CARD_LAST4, DEBIT_CARD_LAST4, IBAN_LAST4, WALLET_LAST4, SENDER_ALIAS }

@Entity(
    tableName = "account_identifiers",
    indices = [
        Index(value = ["normalizedValue"], unique = true),
        Index(value = ["accountId"]),
    ],
)
data class AccountIdentifierEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val identifierType: AccountIdentifierType,
    val normalizedValue: String,
    val displayLabel: String,
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
)

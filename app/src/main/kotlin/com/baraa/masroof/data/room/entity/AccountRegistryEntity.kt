package com.baraa.masroof.data.room.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * Durable account ownership registry row.
 * Composite identity: [bankId] + [maskedNumber].
 *
 * [Bank.UNKNOWN][com.baraa.masroof.domain.model.Bank.UNKNOWN] must never be stored.
 */
@Entity(
    tableName = "account_registry",
    primaryKeys = ["bankId", "maskedNumber"],
    indices = [
        Index(value = ["bankId", "maskedNumber"], unique = true),
    ],
)
data class AccountRegistryEntity(
    val bankId: String,
    val maskedNumber: String,
    val ownershipStatus: String,
    val firstSeenRawSmsId: String?,
    val lastSeenRawSmsId: String?,
)

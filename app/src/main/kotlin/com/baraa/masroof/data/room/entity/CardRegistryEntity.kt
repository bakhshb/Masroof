package com.baraa.masroof.data.room.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * Durable card ownership registry row.
 * Composite identity: [bankId] + [last4].
 */
@Entity(
    tableName = "card_registry",
    primaryKeys = ["bankId", "last4"],
    indices = [
        Index(value = ["bankId", "last4"], unique = true),
    ],
)
data class CardRegistryEntity(
    val bankId: String,
    val last4: String,
    val ownershipStatus: String,
    val firstSeenRawSmsId: String?,
    val lastSeenRawSmsId: String?,
    val evidenceCount: Int,
)

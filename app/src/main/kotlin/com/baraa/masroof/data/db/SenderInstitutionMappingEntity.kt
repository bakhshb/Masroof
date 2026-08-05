package com.baraa.masroof.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted user-confirmed mapping between an SMS sender and a friendly
 * financial institution. Used by [com.baraa.masroof.ledger.FinancialInstitutionResolver]
 * to recognize future messages from the same sender without re-prompting.
 *
 * The mapping intentionally does NOT store:
 *  - the SMS body
 *  - the transaction amount
 *  - full account/card numbers (only a normalized alias is kept)
 *
 * @param senderKey         normalized SMS sender identifier
 * @param institutionName   friendly Arabic institution name (e.g. "البنك الأهلي السعودي")
 * @param isActive          user-toggled; disabled mappings are not used for future matches
 * @param confirmationCount how many times the user confirmed this mapping
 */
@Entity(
    tableName = "sender_institution_mapping",
    indices = [Index(value = ["senderKey"], unique = true)],
)
data class SenderInstitutionMappingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "senderKey")
    val senderKey: String,
    val institutionName: String,
    val isActive: Boolean = true,
    val confirmationCount: Int = 1,
    val lastConfirmedAt: Long = 0L,
    val createdAt: Long = 0L,
)

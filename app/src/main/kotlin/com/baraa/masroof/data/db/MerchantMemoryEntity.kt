package com.baraa.masroof.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.baraa.masroof.transaction.FinancialTreatment

/**
 * One user-confirmed merchant categorization. The [normalizedKey] is the
 * output of [com.baraa.masroof.transaction.MerchantNormalizer] — it is
 * also the primary key so the same merchant always maps to the same row.
 *
 * When the user categorizes a transaction in the review UI, the import
 * service writes here so the next import of the same merchant auto-applies
 * the same category.
 */
@Entity(tableName = "merchant_memory")
data class MerchantMemoryEntity(
    @PrimaryKey
    val normalizedKey: String,
    val displayName: String,
    val preferredCategoryId: Long?,
    val preferredFinancialTreatment: FinancialTreatment?,
    val confirmationCount: Int = 1,
    val lastConfirmedAt: Long,
    /** When false, the rule engine ignores this row (UI-set disable). */
    val enabled: Boolean = true,
)

/** Domain-level read model. */
data class MerchantMemory(
    val normalizedKey: String,
    val displayName: String,
    val preferredCategoryId: Long?,
    val preferredFinancialTreatment: FinancialTreatment?,
    val confirmationCount: Int,
    val lastConfirmedAt: Long,
    val enabled: Boolean = true,
)

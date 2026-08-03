package com.baraa.masroof.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.baraa.masroof.transaction.Currency

/**
 * The user's local financial-setup record. There is exactly one row
 * (`id = SINGLETON_ID`) per database. The row records:
 *  - [trackingStartDate] — the date from which the app will eventually
 *    calculate historical balances (no historical calculation in this
 *    task).
 *  - [setupCompleted] / [setupCompletedAt] — whether the user has
 *    finished the onboarding setup.
 *  - [defaultCurrency] — currency used for displaying totals.
 *
 * The setup record is **not** authoritative for balances — it is a
 * pointer to the user's preferences. The actual balances live on
 * individual accounts.
 */
@Entity(tableName = "financial_setup")
data class FinancialSetupEntity(
    @PrimaryKey
    val id: Int = SINGLETON_ID,
    val trackingStartDate: Long,
    val setupCompleted: Boolean,
    val setupCompletedAt: Long,
    val defaultCurrency: Currency,
) {
    companion object {
        const val SINGLETON_ID: Int = 1
    }
}

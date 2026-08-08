package com.baraa.masroof.data.repository

import com.baraa.masroof.data.db.TransactionSmsBodyDao
import com.baraa.masroof.data.db.TransactionSmsBodyEntity

/**
 * Compatibility facade for the legacy raw-body table. New SMS content is never
 * persisted or returned; existing rows are deliberately left untouched rather
 * than deleted during a financial-data migration.
 */
class TransactionSmsBodyRepository(
    private val dao: TransactionSmsBodyDao,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun save(transactionId: Long, body: String?) = Unit

    suspend fun getBody(transactionId: Long): String? = null
}

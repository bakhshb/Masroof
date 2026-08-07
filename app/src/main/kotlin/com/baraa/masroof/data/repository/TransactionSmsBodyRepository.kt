package com.baraa.masroof.data.repository

import com.baraa.masroof.data.db.TransactionSmsBodyDao
import com.baraa.masroof.data.db.TransactionSmsBodyEntity

/** Local SMS body store for on-device link assist. Never logs body content. */
class TransactionSmsBodyRepository(
    private val dao: TransactionSmsBodyDao,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun save(transactionId: Long, body: String?) {
        val text = body?.trim().orEmpty()
        if (transactionId <= 0L || text.isEmpty()) return
        dao.upsert(
            TransactionSmsBodyEntity(
                transactionId = transactionId,
                body = text,
                createdAt = now(),
            ),
        )
    }

    suspend fun getBody(transactionId: Long): String? =
        dao.getByTransactionId(transactionId)?.body
}

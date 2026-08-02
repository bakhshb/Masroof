package com.baraa.masroof.data.repository

import com.baraa.masroof.data.db.TransactionDao
import com.baraa.masroof.data.db.TransactionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for the local `transactions` table.
 *
 * Splitting the contract from the Room-backed implementation lets us test the
 * import / edit / delete logic in pure JVM with a [FakeTransactionRepository]
 * without needing an Android device or Robolectric.
 */
interface TransactionRepository {
    fun observeAll(): Flow<List<TransactionEntity>>
    fun observeCount(): Flow<Int>
    suspend fun getAllNewestFirst(): List<TransactionEntity>
    suspend fun getById(id: Long): TransactionEntity?
    suspend fun insert(transaction: TransactionEntity): Long
    suspend fun insertAll(transactions: List<TransactionEntity>): List<Long>
    suspend fun update(transaction: TransactionEntity): Int
    suspend fun delete(transaction: TransactionEntity): Int
    suspend fun deleteAll()
    suspend fun count(): Int
    suspend fun existsByFingerprint(fingerprint: String): Boolean
    suspend fun findBySimilarityKey(key: String): List<TransactionEntity>
}

/** Room-backed implementation. */
class RoomTransactionRepository(private val dao: TransactionDao) : TransactionRepository {
    override fun observeAll(): Flow<List<TransactionEntity>> = dao.observeAll()
    override fun observeCount(): Flow<Int> = dao.observeCount()
    override suspend fun getAllNewestFirst(): List<TransactionEntity> = dao.getAllNewestFirst()
    override suspend fun getById(id: Long): TransactionEntity? = dao.getById(id)
    override suspend fun insert(transaction: TransactionEntity): Long = dao.insert(transaction)
    override suspend fun insertAll(transactions: List<TransactionEntity>): List<Long> =
        dao.insertAll(transactions)
    override suspend fun update(transaction: TransactionEntity): Int = dao.update(transaction)
    override suspend fun delete(transaction: TransactionEntity): Int = dao.delete(transaction)
    override suspend fun deleteAll() = dao.deleteAll()
    override suspend fun count(): Int = dao.count()
    override suspend fun existsByFingerprint(fingerprint: String): Boolean =
        dao.existsByFingerprint(fingerprint)

    override suspend fun findBySimilarityKey(key: String): List<TransactionEntity> =
        dao.findBySimilarityKey(key)
}

/**
 * In-memory [TransactionRepository] for JVM unit tests. Behaves like the
 * Room-backed one (newest-first ordering, IGNORE-on-duplicate, etc.) without
 * any Android dependency.
 */
class FakeTransactionRepository : TransactionRepository {

    private val rows: MutableList<TransactionEntity> = mutableListOf()
    private val flows: MutableList<MutableList<TransactionEntity>> = mutableListOf()

    private fun snapshot(): List<TransactionEntity> = rows.sortedByDescending { it.smsTimestamp }

    private fun publish() {
        val snap = snapshot()
        for (f in flows) {
            f.clear()
            f.addAll(snap)
        }
    }

    override fun observeAll(): Flow<List<TransactionEntity>> = kotlinx.coroutines.flow.MutableStateFlow(snapshot())

    override fun observeCount(): Flow<Int> = kotlinx.coroutines.flow.MutableStateFlow(rows.size)

    override suspend fun getAllNewestFirst(): List<TransactionEntity> = snapshot()

    override suspend fun getById(id: Long): TransactionEntity? = rows.firstOrNull { it.id == id }

    override suspend fun insert(transaction: TransactionEntity): Long {
        if (rows.any { it.uniqueFingerprint == transaction.uniqueFingerprint }) return -1L
        val withId = transaction.copy(id = nextId())
        rows.add(withId)
        publish()
        return withId.id
    }

    override suspend fun insertAll(transactions: List<TransactionEntity>): List<Long> {
        val results = ArrayList<Long>(transactions.size)
        for (t in transactions) {
            results.add(insert(t))
        }
        return results
    }

    override suspend fun update(transaction: TransactionEntity): Int {
        val idx = rows.indexOfFirst { it.id == transaction.id }
        if (idx < 0) return 0
        rows[idx] = transaction
        publish()
        return 1
    }

    override suspend fun delete(transaction: TransactionEntity): Int {
        val removed = rows.removeAll { it.id == transaction.id }
        if (removed) publish()
        return if (removed) 1 else 0
    }

    override suspend fun deleteAll() {
        rows.clear()
        publish()
    }

    override suspend fun count(): Int = rows.size

    override suspend fun existsByFingerprint(fingerprint: String): Boolean =
        rows.any { it.uniqueFingerprint == fingerprint }

    override suspend fun findBySimilarityKey(key: String): List<TransactionEntity> =
        rows.filter { it.transactionSimilarityKey == key }
            .sortedByDescending { it.smsTimestamp }

    private fun nextId(): Long = (rows.maxOfOrNull { it.id } ?: 0L) + 1L
}

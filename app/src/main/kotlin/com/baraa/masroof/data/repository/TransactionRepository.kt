package com.baraa.masroof.data.repository

import com.baraa.masroof.data.db.TransactionDao
import com.baraa.masroof.data.db.TransactionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

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
    suspend fun countByCategory(): Map<Long?, Int>
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

    override suspend fun countByCategory(): Map<Long?, Int> =
        dao.countByCategory().associate { (id, n) -> id to n }
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

    override suspend fun countByCategory(): Map<Long?, Int> =
        rows.filter { it.categoryId != null }
            .groupBy { it.categoryId }
            .mapValues { it.value.size }

    private fun nextId(): Long = (rows.maxOfOrNull { it.id } ?: 0L) + 1L
}

/** In-memory [CategoryRepository] for unit tests. */
class FakeCategoryRepository(
    private val transactionCountByCategory: suspend () -> Map<Long, Int> = { emptyMap() },
) : CategoryRepository {
    private val rows = mutableListOf<com.baraa.masroof.data.db.Category>()
    private val flows = mutableListOf<MutableList<com.baraa.masroof.data.db.Category>>()
    private var nextId = 1L

    override fun observeAll(): Flow<List<com.baraa.masroof.data.db.Category>> =
        kotlinx.coroutines.flow.MutableStateFlow(rows.toList())

    override suspend fun getAll(): List<com.baraa.masroof.data.db.Category> = rows.toList()
    override suspend fun getById(id: Long): com.baraa.masroof.data.db.Category? = rows.firstOrNull { it.id == id }
    override suspend fun count(): Int = rows.size
    override suspend fun any(): Boolean = rows.isNotEmpty()
    override suspend fun seedIfEmpty() { /* no-op for tests */ }
    override suspend fun add(nameAr: String, parentId: Long?, nameEn: String?, sortOrder: Int): Long {
        val id = nextId++
        rows.add(
            com.baraa.masroof.data.db.Category(
                id = id,
                parentId = parentId,
                nameAr = nameAr,
                nameEn = nameEn,
                sortOrder = sortOrder,
                enabled = true,
                isSystem = false,
            )
        )
        return id
    }
    override suspend fun rename(id: Long, newNameAr: String, newNameEn: String?) {
        val idx = rows.indexOfFirst { it.id == id }
        if (idx >= 0) rows[idx] = rows[idx].copy(nameAr = newNameAr, nameEn = newNameEn)
    }
    override suspend fun setEnabled(id: Long, enabled: Boolean) {
        val idx = rows.indexOfFirst { it.id == id }
        if (idx >= 0) rows[idx] = rows[idx].copy(enabled = enabled)
    }
    override suspend fun setSortOrder(id: Long, sortOrder: Int) {
        val idx = rows.indexOfFirst { it.id == id }
        if (idx >= 0) rows[idx] = rows[idx].copy(sortOrder = sortOrder)
    }
    override suspend fun move(id: Long, newParentId: Long?) {
        // Cycle prevention: cannot move to itself.
        if (newParentId == id) {
            throw IllegalArgumentException("Cannot move category into itself")
        }
        // Cycle prevention: cannot move into its own descendant.
        if (newParentId != null) {
            val childrenByParent = rows.groupBy { it.parentId }
            fun descendantsOf(target: Long): Set<Long> {
                val result = HashSet<Long>()
                val stack = ArrayDeque<Long>()
                stack.addLast(target)
                while (stack.isNotEmpty()) {
                    val current = stack.removeLast()
                    val kids = childrenByParent[current].orEmpty().map { it.id }
                    for (k in kids) {
                        if (result.add(k)) stack.addLast(k)
                    }
                }
                return result
            }
            if (newParentId in descendantsOf(id)) {
                throw IllegalArgumentException("Cannot move category into its descendant")
            }
        }
        val idx = rows.indexOfFirst { it.id == id }
        if (idx >= 0) rows[idx] = rows[idx].copy(parentId = newParentId)
    }
    override suspend fun delete(id: Long): DeleteResult {
        val current = rows.firstOrNull { it.id == id } ?: return DeleteResult.Success
        if (current.isSystem) return DeleteResult.Failure("لا يمكن حذف تصنيف من النظام")
        if (rows.any { it.parentId == id }) {
            return DeleteResult.Failure("لا يمكن حذف تصنيف يحتوي على تصنيفات فرعية")
        }
        return if (rows.removeAll { it.id == id }) DeleteResult.Success
        else DeleteResult.Failure("تعذّر الحذف")
    }
    override fun resolveByName(name: String): suspend () -> com.baraa.masroof.data.db.Category? =
        { rows.firstOrNull { it.nameAr == name || it.nameEn == name } }
}

/** In-memory [MerchantMemoryRepository] for unit tests. */
class FakeMerchantMemoryRepository : MerchantMemoryRepository {
    private val rows = mutableListOf<com.baraa.masroof.data.db.MerchantMemory>()

    override fun observeAll(): Flow<List<com.baraa.masroof.data.db.MerchantMemory>> =
        kotlinx.coroutines.flow.MutableStateFlow(rows.toList())

    override suspend fun getAll(): List<com.baraa.masroof.data.db.MerchantMemory> = rows.toList()
    override suspend fun getByKey(key: String): com.baraa.masroof.data.db.MerchantMemory? =
        rows.firstOrNull { it.normalizedKey == key }
    override suspend fun remember(
        rawMerchant: String?,
        displayName: String,
        categoryId: Long?,
        treatment: com.baraa.masroof.transaction.FinancialTreatment?,
    ) {
        val key = com.baraa.masroof.transaction.MerchantNormalizer.normalize(rawMerchant)
        if (key.isBlank()) return
        val existing = rows.firstOrNull { it.normalizedKey == key }
        if (existing == null) {
            rows.add(
                com.baraa.masroof.data.db.MerchantMemory(
                    normalizedKey = key,
                    displayName = displayName,
                    preferredCategoryId = categoryId,
                    preferredFinancialTreatment = treatment,
                    confirmationCount = 1,
                    lastConfirmedAt = 1_700_000_000_000L,
                    enabled = true,
                )
            )
        } else {
            val idx = rows.indexOf(existing)
            rows[idx] = existing.copy(
                preferredCategoryId = categoryId,
                preferredFinancialTreatment = treatment,
                confirmationCount = existing.confirmationCount + 1,
            )
        }
    }
    override suspend fun setEnabled(key: String, enabled: Boolean) {
        val idx = rows.indexOfFirst { it.normalizedKey == key }
        if (idx >= 0) rows[idx] = rows[idx].copy(enabled = enabled)
    }
    override suspend fun delete(key: String) {
        rows.removeAll { it.normalizedKey == key }
    }
    override suspend fun merge(fromKey: String, intoKey: String) {
        if (fromKey == intoKey) return
        val fromIdx = rows.indexOfFirst { it.normalizedKey == fromKey }
        if (fromIdx < 0) return
        val intoIdx = rows.indexOfFirst { it.normalizedKey == intoKey }
        if (intoIdx < 0) return
        val from = rows[fromIdx]
        val into = rows[intoIdx]
        rows[intoIdx] = into.copy(
            confirmationCount = into.confirmationCount + from.confirmationCount,
            lastConfirmedAt = maxOf(into.lastConfirmedAt, from.lastConfirmedAt),
        )
        rows.removeAt(fromIdx)
    }
}

/** In-memory [FinancialAccountRepository] for unit tests. */
class FakeFinancialAccountRepository : FinancialAccountRepository {
    private val rows = mutableListOf<com.baraa.masroof.data.db.FinancialAccount>()
    private var nextId = 1L

    override fun observeAll(): Flow<List<com.baraa.masroof.data.db.FinancialAccount>> =
        kotlinx.coroutines.flow.MutableStateFlow(rows.toList())

    override suspend fun getActive(): List<com.baraa.masroof.data.db.FinancialAccount> =
        rows.filter { it.isActive }

    override suspend fun getOwnedActive(): List<com.baraa.masroof.data.db.FinancialAccount> =
        rows.filter { it.isOwnedByUser && it.isActive }

    override suspend fun getById(id: Long): com.baraa.masroof.data.db.FinancialAccount? =
        rows.firstOrNull { it.id == id }

    override suspend fun add(
        displayName: String,
        accountType: com.baraa.masroof.transaction.AccountType,
        institutionName: String?,
        lastFourDigits: String?,
        senderAliases: List<String>,
        accountNature: com.baraa.masroof.transaction.AccountNature,
        currency: com.baraa.masroof.transaction.Currency,
        openingBalance: java.math.BigDecimal,
        openingBalanceDate: Long,
        includeInNetWorth: Boolean,
        includeInLiquidity: Boolean,
        notes: String?,
    ): Long {
        val id = nextId++
        val now = System.currentTimeMillis()
        rows.add(
            com.baraa.masroof.data.db.FinancialAccount(
                id = id,
                displayName = displayName,
                institutionName = institutionName,
                accountType = accountType,
                accountNature = accountNature,
                lastFourDigits = lastFourDigits?.trim()?.takeIf { it.length == 4 && it.all(Char::isDigit) },
                senderAliases = senderAliases,
                currency = currency,
                openingBalance = openingBalance,
                openingBalanceDate = openingBalanceDate,
                includeInNetWorth = includeInNetWorth,
                includeInLiquidity = includeInLiquidity,
                isOwnedByUser = true,
                isActive = true,
                notes = notes,
                createdAt = now,
                updatedAt = now,
            )
        )
        return id
    }

    override suspend fun update(account: com.baraa.masroof.data.db.FinancialAccount) {
        val idx = rows.indexOfFirst { it.id == account.id }
        if (idx >= 0) rows[idx] = account
    }

    override suspend fun delete(account: com.baraa.masroof.data.db.FinancialAccount) {
        rows.removeAll { it.id == account.id }
    }
}

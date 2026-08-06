package com.baraa.masroof.ai

import com.baraa.masroof.data.db.CategoryEntity
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.data.repository.FakeCategoryRepository
import com.baraa.masroof.data.repository.FakeTransactionRepository

/**
 * Adapters that expose [FakeTransactionRepository] and [FakeCategoryRepository]
 * through the [com.baraa.masroof.data.db.TransactionDao] / [com.baraa.masroof.data.db.CategoryDao]
 * interfaces expected by Room-backed repositories.
 *
 * Only the methods used by the AI suggestion repository are delegated.
 */

internal fun wrapTransactionDao(repo: FakeTransactionRepository): com.baraa.masroof.data.db.TransactionDao =
    object : com.baraa.masroof.data.db.TransactionDao {
        override fun observeAll() = repo.observeAll()
        override fun observeCount() = repo.observeCount()
        override fun observeById(id: Long) = kotlinx.coroutines.flow.flow {
            emit(repo.getById(id))
        }
        override suspend fun getAllNewestFirst() = repo.getAllNewestFirst()
        override suspend fun getById(id: Long) = repo.getById(id)
        override suspend fun insert(transaction: TransactionEntity) = repo.insert(transaction)
        override suspend fun insertAll(transactions: List<TransactionEntity>) = repo.insertAll(transactions)
        override suspend fun update(transaction: TransactionEntity) = repo.update(transaction)
        override suspend fun delete(transaction: TransactionEntity) = repo.delete(transaction)
        override suspend fun deleteAll() = repo.deleteAll()
        override suspend fun count() = repo.count()
        override suspend fun existsByFingerprint(fingerprint: String) = repo.existsByFingerprint(fingerprint)
        override suspend fun findBySimilarityKey(key: String) = repo.findBySimilarityKey(key)
        override suspend fun countByCategory(): List<com.baraa.masroof.data.db.CategoryTxCount> =
            repo.countByCategory().map { com.baraa.masroof.data.db.CategoryTxCount(it.key ?: -1L, it.value) }
    }

internal fun wrapCategoryDao(repo: FakeCategoryRepository): com.baraa.masroof.data.db.CategoryDao =
    object : com.baraa.masroof.data.db.CategoryDao {
        override fun observeAll(): kotlinx.coroutines.flow.Flow<List<CategoryEntity>> = kotlinx.coroutines.flow.flow {
            emit(repo.getAll().map { it.toEntity() })
        }
        override suspend fun getAll(): List<CategoryEntity> = repo.getAll().map { it.toEntity() }
        override suspend fun getById(id: Long): CategoryEntity? = repo.getById(id)?.toEntity()
        override suspend fun count() = repo.count()
        override suspend fun any() = repo.any()
        override suspend fun insert(category: CategoryEntity): Long {
            repo.add(category.nameAr, category.parentId, category.nameEn, category.sortOrder)
            return category.id
        }
        override suspend fun insertAll(categories: List<CategoryEntity>): List<Long> =
            categories.map { insert(it) }
        override suspend fun update(category: CategoryEntity): Int = 0
        override suspend fun setEnabled(id: Long, enabled: Boolean, now: Long): Int {
            repo.setEnabled(id, enabled); return 1
        }
        override suspend fun setSortOrder(id: Long, sortOrder: Int, now: Long): Int {
            repo.setSortOrder(id, sortOrder); return 1
        }
        override suspend fun deleteIfNotSystem(id: Long): Int {
            val r = repo.delete(id)
            return if (r is com.baraa.masroof.data.repository.DeleteResult.Success) 1 else 0
        }
    }

private fun com.baraa.masroof.data.db.Category.toEntity(): CategoryEntity = CategoryEntity(
    id = id, parentId = parentId, nameAr = nameAr, nameEn = nameEn,
    sortOrder = sortOrder, enabled = enabled, isSystem = isSystem,
    createdAt = 0L, updatedAt = 0L
)
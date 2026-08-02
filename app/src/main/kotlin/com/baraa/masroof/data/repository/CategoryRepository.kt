package com.baraa.masroof.data.repository

import com.baraa.masroof.data.db.Category
import com.baraa.masroof.data.db.CategoryDao
import com.baraa.masroof.data.db.CategoryEntity
import com.baraa.masroof.rules.DefaultCategorySeed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface CategoryRepository {
    fun observeAll(): Flow<List<Category>>
    suspend fun getAll(): List<Category>
    suspend fun getById(id: Long): Category?
    suspend fun count(): Int
    suspend fun any(): Boolean
    suspend fun seedIfEmpty()
    suspend fun add(nameAr: String, parentId: Long?, nameEn: String? = null, sortOrder: Int = 0): Long
    suspend fun rename(id: Long, newNameAr: String, newNameEn: String?)
    suspend fun setEnabled(id: Long, enabled: Boolean)
    suspend fun setSortOrder(id: Long, sortOrder: Int)
    suspend fun deleteIfNotSystem(id: Long): Boolean
    fun resolveByName(name: String): suspend () -> Category?
}

class RoomCategoryRepository(
    private val dao: CategoryDao,
    private val now: () -> Long = { System.currentTimeMillis() },
) : CategoryRepository {

    override fun observeAll(): Flow<List<Category>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getAll(): List<Category> = dao.getAll().map { it.toDomain() }
    override suspend fun getById(id: Long): Category? = dao.getById(id)?.toDomain()
    override suspend fun count(): Int = dao.count()
    override suspend fun any(): Boolean = dao.any()

    override suspend fun seedIfEmpty() {
        if (dao.any()) return
        val seed = DefaultCategorySeed.seed(now())
        dao.insertAll(seed)
    }

    override suspend fun add(nameAr: String, parentId: Long?, nameEn: String?, sortOrder: Int): Long {
        val now = now()
        val entity = CategoryEntity(
            parentId = parentId,
            nameAr = nameAr,
            nameEn = nameEn,
            sortOrder = sortOrder,
            enabled = true,
            isSystem = false,
            createdAt = now,
            updatedAt = now,
        )
        return dao.insert(entity)
    }

    override suspend fun rename(id: Long, newNameAr: String, newNameEn: String?) {
        val current = dao.getById(id) ?: return
        dao.update(
            current.copy(
                nameAr = newNameAr,
                nameEn = newNameEn,
                updatedAt = now(),
            )
        )
    }

    override suspend fun setEnabled(id: Long, enabled: Boolean) {
        dao.setEnabled(id, enabled, now())
    }

    override suspend fun setSortOrder(id: Long, sortOrder: Int) {
        dao.setSortOrder(id, sortOrder, now())
    }

    override suspend fun deleteIfNotSystem(id: Long): Boolean =
        dao.deleteIfNotSystem(id) > 0

    override fun resolveByName(name: String): suspend () -> Category? = {
        getAll().firstOrNull { it.nameAr == name || it.nameEn == name }
    }
}

internal fun CategoryEntity.toDomain(): Category = Category(
    id = id,
    parentId = parentId,
    nameAr = nameAr,
    nameEn = nameEn,
    sortOrder = sortOrder,
    enabled = enabled,
    isSystem = isSystem,
)

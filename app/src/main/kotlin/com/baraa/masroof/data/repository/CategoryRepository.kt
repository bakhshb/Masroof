package com.baraa.masroof.data.repository

import com.baraa.masroof.data.db.Category
import com.baraa.masroof.data.db.CategoryDao
import com.baraa.masroof.data.db.CategoryEntity
import com.baraa.masroof.rules.DefaultCategorySeed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Result of a category delete attempt. */
sealed interface DeleteResult {
    data object Success : DeleteResult
    data class Failure(val reason: String) : DeleteResult
}

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
    suspend fun move(id: Long, newParentId: Long?)
    /**
     * Delete a category. Refuses to delete if any transaction references
     * it. Refuses to delete a parent that has children. System categories
     * cannot be deleted (they must remain available for seed-only data).
     */
    suspend fun delete(id: Long): DeleteResult
    fun resolveByName(name: String): suspend () -> Category?
}

class RoomCategoryRepository(
    private val dao: CategoryDao,
    private val transactionCountByCategory: suspend () -> Map<Long, Int> = { emptyMap() },
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
        dao.update(current.copy(nameAr = newNameAr, nameEn = newNameEn, updatedAt = now()))
    }

    override suspend fun setEnabled(id: Long, enabled: Boolean) {
        dao.setEnabled(id, enabled, now())
    }

    override suspend fun setSortOrder(id: Long, sortOrder: Int) {
        dao.setSortOrder(id, sortOrder, now())
    }

    override suspend fun move(id: Long, newParentId: Long?) {
        // Prevent circular relationships: the new parent cannot be the
        // category itself or any of its descendants.
        if (newParentId != null) {
            if (newParentId == id) {
                throw IllegalArgumentException("a category cannot be its own parent")
            }
            val all = getAll().associateBy { it.id }
            var cursor: Long? = newParentId
            val visited = HashSet<Long>()
            while (cursor != null && cursor != id) {
                if (!visited.add(cursor)) {
                    // cycle detected before we returned to id
                    throw IllegalArgumentException("circular parent relationship")
                }
                if (cursor == id) {
                    throw IllegalArgumentException("circular parent relationship")
                }
                cursor = all[cursor]?.parentId
            }
            if (cursor == id) throw IllegalArgumentException("circular parent relationship")
        }
        val current = dao.getById(id) ?: return
        dao.update(current.copy(parentId = newParentId, updatedAt = now()))
    }

    override suspend fun delete(id: Long): DeleteResult {
        val current = dao.getById(id) ?: return DeleteResult.Success
        if (current.isSystem) {
            return DeleteResult.Failure("لا يمكن حذف تصنيف من النظام")
        }
        // Has children?
        val hasChildren = getAll().any { it.parentId == id }
        if (hasChildren) {
            return DeleteResult.Failure("لا يمكن حذف تصنيف يحتوي على تصنيفات فرعية")
        }
        // Referenced by transactions?
        val counts = transactionCountByCategory()
        if ((counts[id] ?: 0) > 0) {
            return DeleteResult.Failure("لا يمكن حذف تصنيف مرتبط بعمليات محفوظة")
        }
        // The FK on `parentId` is RESTRICT, so deleting a parent with
        // children would already fail at the DB layer. The hasChildren
        // check above catches it earlier for a better error message.
        return if (dao.deleteIfNotSystem(id) > 0) {
            DeleteResult.Success
        } else {
            DeleteResult.Failure("تعذّر الحذف")
        }
    }

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

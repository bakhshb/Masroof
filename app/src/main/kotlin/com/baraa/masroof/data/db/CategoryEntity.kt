package com.baraa.masroof.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A category in the user's spending hierarchy. Categories form a two-level
 * tree: parent categories have `parentId = null`; children reference their
 * parent via [parentId].
 *
 * System categories (the ones seeded on first launch) carry `isSystem = true`
 * and cannot be deleted — they can still be disabled, renamed is allowed
 * for system categories but the original Arabic name is kept as a fallback.
 */
@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("parentId"),
        Index(value = ["nameAr", "parentId"], unique = true),
    ],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "parentId")
    val parentId: Long?,
    @ColumnInfo(name = "nameAr")
    val nameAr: String,
    @ColumnInfo(name = "nameEn")
    val nameEn: String?,
    @ColumnInfo(name = "sortOrder")
    val sortOrder: Int,
    @ColumnInfo(name = "enabled")
    val enabled: Boolean = true,
    @ColumnInfo(name = "isSystem")
    val isSystem: Boolean = false,
    @ColumnInfo(name = "createdAt")
    val createdAt: Long,
    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long,
)

/**
 * Domain-level read model that flattens a parent + its optional child into a
 * single display record. The UI uses this directly.
 */
data class Category(
    val id: Long,
    val parentId: Long?,
    val nameAr: String,
    val nameEn: String?,
    val sortOrder: Int,
    val enabled: Boolean,
    val isSystem: Boolean,
)

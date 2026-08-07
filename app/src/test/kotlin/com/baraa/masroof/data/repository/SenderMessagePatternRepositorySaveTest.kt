package com.baraa.masroof.data.repository

import com.baraa.masroof.data.db.SenderMessagePatternDao
import com.baraa.masroof.data.db.SenderMessagePatternEntity
import com.baraa.masroof.data.db.SenderMessagePatternKind
import com.baraa.masroof.sms.LearnedSmsFeatures
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SenderMessagePatternRepositorySaveTest {

    @Test
    fun saveSelectedPatterns_twoStylesSameSender_noAccountId() = runBlocking {
        val dao = FakePatternDao()
        val repo = SenderMessagePatternRepository(dao = dao, now = { 1000L })
        val result = repo.saveSelectedPatterns(
            listOf(
                SenderMessagePatternRepository.DiscoveredPatternSelection(
                    senderKey = "alrajhi",
                    structureKey = "بمبلغ|شراء|لدى",
                    features = LearnedSmsFeatures(
                        amountLabels = setOf("بمبلغ"),
                        typeCues = setOf("شراء"),
                        lineLabels = setOf("بمبلغ", "شراء", "لدى"),
                    ),
                    exampleCount = 3,
                ),
                SenderMessagePatternRepository.DiscoveredPatternSelection(
                    senderKey = "alrajhi",
                    structureKey = "مبلغ التحويل|تحويل|إلى",
                    features = LearnedSmsFeatures(
                        amountLabels = setOf("مبلغ التحويل"),
                        typeCues = setOf("تحويل"),
                        lineLabels = setOf("مبلغ التحويل", "تحويل", "إلى"),
                    ),
                    exampleCount = 2,
                ),
            ),
        )
        assertEquals(2, result.savedCount)
        assertEquals(setOf("alrajhi"), result.senderKeys)
        assertEquals(2, dao.rows.size)
        assertTrue(dao.rows.all { it.accountId == null })
        assertTrue(dao.rows.map { it.structureKey }.toSet().size == 2)
    }

    @Test
    fun saveSelectedPatterns_sameStructureKey_mergesFeatures() = runBlocking {
        val dao = FakePatternDao()
        val repo = SenderMessagePatternRepository(dao = dao, now = { 1000L })
        repo.saveSelectedPatterns(
            listOf(
                SenderMessagePatternRepository.DiscoveredPatternSelection(
                    senderKey = "snb",
                    structureKey = "a|b",
                    features = LearnedSmsFeatures(setOf("مبلغ"), setOf("شراء"), setOf("a", "b")),
                    exampleCount = 1,
                ),
            ),
        )
        repo.saveSelectedPatterns(
            listOf(
                SenderMessagePatternRepository.DiscoveredPatternSelection(
                    senderKey = "snb",
                    structureKey = "a|b",
                    features = LearnedSmsFeatures(setOf("قيمة"), setOf("شراء"), setOf("a", "b", "c")),
                    exampleCount = 1,
                ),
            ),
        )
        assertEquals(1, dao.rows.size)
        assertEquals(2, dao.rows[0].exampleCount)
        assertTrue(dao.rows[0].amountLabels.containsAll(listOf("مبلغ", "قيمة")))
        assertNull(dao.rows[0].accountId)
    }

    private class FakePatternDao : SenderMessagePatternDao {
        val rows = mutableListOf<SenderMessagePatternEntity>()
        private var nextId = 1L

        override suspend fun insert(row: SenderMessagePatternEntity): Long {
            val id = if (row.id == 0L) nextId++ else row.id
            rows.removeAll { it.id == id }
            rows += row.copy(id = id)
            return id
        }

        override suspend fun update(row: SenderMessagePatternEntity) {
            val idx = rows.indexOfFirst { it.id == row.id }
            if (idx >= 0) rows[idx] = row else rows += row
        }

        override suspend fun find(
            senderKey: String,
            structureKey: String,
            kind: SenderMessagePatternKind,
        ): SenderMessagePatternEntity? =
            rows.firstOrNull { it.senderKey == senderKey && it.structureKey == structureKey && it.kind == kind }

        override suspend fun getActive(): List<SenderMessagePatternEntity> = rows.filter { it.active }

        override suspend fun getActiveByKind(kind: SenderMessagePatternKind): List<SenderMessagePatternEntity> =
            rows.filter { it.active && it.kind == kind }

        override suspend fun getActiveBySenderAndKind(
            senderKey: String,
            kind: SenderMessagePatternKind,
        ): List<SenderMessagePatternEntity> =
            rows.filter { it.active && it.senderKey == senderKey && it.kind == kind }

        override suspend fun activeIncludeSenderKeys(): List<String> =
            rows.filter { it.active && it.kind == SenderMessagePatternKind.INCLUDE_TRANSACTION }
                .map { it.senderKey }
                .distinct()

        override suspend fun observeAllActive(): List<SenderMessagePatternEntity> =
            rows.filter { it.active }.sortedByDescending { it.updatedAt }

        override suspend fun deactivate(id: Long, updatedAt: Long) {
            val idx = rows.indexOfFirst { it.id == id }
            if (idx >= 0) rows[idx] = rows[idx].copy(active = false, updatedAt = updatedAt)
        }

        override suspend fun delete(id: Long) {
            rows.removeAll { it.id == id }
        }
    }
}

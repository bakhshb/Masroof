package com.baraa.masroof.domain.repository

import com.baraa.masroof.domain.model.RawSms

/**
 * Domain-facing RawSms persistence. Implementations live in the data layer.
 *
 * Duplicate-aware insertion supports safe P6 ingestion without creating
 * duplicate evidence rows.
 */
interface RawSmsRepository {
    suspend fun insertIfAbsent(rawSms: RawSms): RawSmsInsertResult

    suspend fun getById(id: String): RawSms?

    suspend fun existsById(id: String): Boolean

    suspend fun findByDeviceMessageId(deviceMessageId: String): RawSms?
}

/**
 * Explicit outcome for expected duplicate detection (not exceptional).
 */
sealed interface RawSmsInsertResult {
    data object Inserted : RawSmsInsertResult

    data object AlreadyExists : RawSmsInsertResult
}

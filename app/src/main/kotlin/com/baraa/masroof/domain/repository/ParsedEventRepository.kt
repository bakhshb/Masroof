package com.baraa.masroof.domain.repository

import com.baraa.masroof.domain.model.ParsedEvent
import com.baraa.masroof.parsing.model.ParsedEventDetails

/**
 * Domain-facing ParsedEvent persistence. Implementations live in the data layer.
 *
 * Supports replace-by-rawSmsId for future reprocessing without event history.
 */
interface ParsedEventRepository {
    suspend fun save(event: ParsedEvent, details: ParsedEventDetails = ParsedEventDetails())

    suspend fun getById(id: String): ParsedEventRecord?

    suspend fun findByRawSmsId(rawSmsId: String): ParsedEventRecord?

    /**
     * Removes the parsed result row(s) for [rawSmsId] only.
     * Never deletes the related [com.baraa.masroof.domain.model.RawSms] evidence.
     */
    suspend fun deleteByRawSmsId(rawSmsId: String)
}

/**
 * Reconstructed parse output: domain [ParsedEvent] plus parse-time [ParsedEventDetails].
 */
data class ParsedEventRecord(
    val event: ParsedEvent,
    val details: ParsedEventDetails,
)

package com.baraa.masroof.parsing.repository

import com.baraa.masroof.domain.model.ParsedEvent
import com.baraa.masroof.parsing.model.ParsedEventDetails

/**
 * Parsing-facing persistence for structured parse output.
 *
 * Lives under parsing (not domain) because [ParsedEventDetails] is a parsing-layer
 * type. Implementations live in the data layer.
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

    /** All persisted parse results (for ownership discovery backlog). */
    suspend fun listAll(): List<ParsedEventRecord>
}

/**
 * Reconstructed parse output: domain [ParsedEvent] plus parse-time [ParsedEventDetails].
 */
data class ParsedEventRecord(
    val event: ParsedEvent,
    val details: ParsedEventDetails,
)

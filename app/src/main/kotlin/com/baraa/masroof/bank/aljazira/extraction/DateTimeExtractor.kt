package com.baraa.masroof.bank.aljazira.extraction

import com.baraa.masroof.parsing.model.NormalizedSms
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Fixture-proven local date-time forms only. No timezone invention.
 */
class DateTimeExtractor {
    fun extract(sms: NormalizedSms): LocalDateTime? {
        val text = sms.comparisonBody
        for ((pattern, formatter) in PATTERNS) {
            val match = pattern.find(text) ?: continue
            val token = match.groupValues[1].trim()
            return runCatching { LocalDateTime.parse(token, formatter) }.getOrNull()
        }
        return null
    }

    companion object {
        private val DMY_HM = DateTimeFormatter.ofPattern("HH:mm dd-MM-yyyy")
        private val YMD_HM = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        private val PATTERNS: List<Pair<Regex, DateTimeFormatter>> = listOf(
            // في: 14:32 03-08-2026
            Regex("""(?:في|on|date)\s*:\s*(\d{2}:\d{2}\s+\d{2}-\d{2}-\d{4})""", RegexOption.IGNORE_CASE) to DMY_HM,
            // في: 2026-08-03 16:40 / on: 2026-08-02 12:04 / Date: 2026-08-01 18:20
            Regex("""(?:في|on|date)\s*:\s*(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2})""", RegexOption.IGNORE_CASE) to YMD_HM,
        )
    }
}

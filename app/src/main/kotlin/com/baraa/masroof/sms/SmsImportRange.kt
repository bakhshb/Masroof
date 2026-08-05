package com.baraa.masroof.sms

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * User-selected SMS import window.
 *
 * - `start` is inclusive of the entire start day (00:00 of the device timezone).
 * - `endExclusive` is exclusive — we read up to, but not including, the moment.
 * - The range is intentionally orthogonal to `trackingStartDate`: changing
 *   one never silently mutates the other.
 *
 * @property label       human-readable Arabic label
 * @property quickId     optional quick-range id for the picker ("month-start",
 *                       "last-salary", "last-7-days", "last-30-days", "custom")
 */
data class SmsImportRange(
    val start: LocalDateTime,
    val endExclusive: LocalDateTime,
    val label: String,
    val quickId: String,
) {
    val startEpochMillis: Long get() = start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val endEpochMillis: Long get() = endExclusive.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Inclusive start-of-day millis (for queries). */
    fun startMillis(zone: ZoneId = ZoneId.systemDefault()): Long = start.atZone(zone).toInstant().toEpochMilli()
    /** Exclusive end millis (for queries). */
    fun endMillis(zone: ZoneId = ZoneId.systemDefault()): Long = endExclusive.atZone(zone).toInstant().toEpochMilli()
    /**
     * The last fully-included calendar day. Always returns a [LocalDate]
     * that is on or before today for a valid import range.
     *
     * The display rule: if [endExclusive] lands at midnight (start of
     * next day), the last included day is the day before; otherwise the
     * last included day is the day on which [endExclusive] falls.
     *
     * Examples:
     *  - endExclusive = today.plusDays(1).atStartOfDay() → returns today.
     *  - endExclusive = LocalDateTime.now() (06:30) → returns today.
     *  - endExclusive = today.atTime(23, 59) → returns today.
     */
    val displayEndDate: LocalDate
        get() = if (endExclusive.toLocalTime() == LocalTime.MIDNIGHT) {
            endExclusive.toLocalDate().minusDays(1)
        } else {
            endExclusive.toLocalDate()
        }


    companion object {
        const val QUICK_MONTH_START = "month-start"
        const val QUICK_LAST_SALARY = "last-salary"
        const val QUICK_LAST_SEVEN = "last-7-days"
        const val QUICK_LAST_THIRTY = "last-30-days"
        const val QUICK_CUSTOM = "custom"

        /** The default import range for first-run import dialog. */
        fun default(today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): SmsImportRange {
            val now = LocalDateTime.of(today, java.time.LocalTime.MIDNIGHT).plusDays(1)
            val startOfMonth = today.withDayOfMonth(1).atStartOfDay()
            return SmsImportRange(
                start = startOfMonth,
                endExclusive = now,
                label = "من بداية هذا الشهر إلى اليوم",
                quickId = QUICK_MONTH_START,
            )
        }

        fun sinceLastSalary(today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): SmsImportRange {
            val startDate = ExpectedSalaryDateService.mostRecentSalaryDate(today)
            val start = startDate.atStartOfDay()
            val now = LocalDateTime.now(zone)
            return SmsImportRange(
                start = start,
                endExclusive = now,
                label = "منذ آخر تاريخ راتب متوقع ($startDate) حتى اليوم",
                quickId = QUICK_LAST_SALARY,
            )
        }

        fun lastDays(today: LocalDate, days: Int): SmsImportRange {
            val startDate = today.minusDays((days - 1).toLong())
            val endDateTime = today.plusDays(1).atStartOfDay()
            return SmsImportRange(
                start = startDate.atStartOfDay(),
                endExclusive = endDateTime,
                label = "آخر $days يومًا",
                quickId = if (days == 7) QUICK_LAST_SEVEN else QUICK_LAST_THIRTY,
            )
        }

        fun custom(from: LocalDate, to: LocalDate, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): SmsImportRange {
            require(!from.isAfter(to)) { "Range start must be on or before end" }
            require(!to.isAfter(today)) { "End date cannot be in the future" }
            return SmsImportRange(
                start = from.atStartOfDay(),
                endExclusive = to.plusDays(1).atStartOfDay(),
                label = "${from} → ${to}",
                quickId = QUICK_CUSTOM,
            )
        }

        /**
         * Validates the given (custom) range. Does NOT mutate any
         * persisted configuration.
         */
        fun validateCustom(from: LocalDate?, to: LocalDate?, today: LocalDate = LocalDate.now()): CustomValidationResult {
            if (from == null || to == null) return CustomValidationResult.Missing
            if (from.isAfter(to)) return CustomValidationResult.Reversed
            if (to.isAfter(today)) return CustomValidationResult.Future
            return CustomValidationResult.Valid
        }
    }
}

enum class CustomValidationResult { Valid, Reversed, Future, Missing }

package com.baraa.masroof.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class SmsInboxQueryTest {

    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 8, 7)

    @Test
    fun queryUsesInboxUriAndDateOnlySelection() {
        val range = SmsImportRange.lastDays(today, 7)
        val q = SmsInboxQuery.forRange(range, limit = 5000, zone = zone)
        assertEquals(SmsInboxQuery.INBOX_URI, q.uriString)
        assertTrue(q.selection.contains("${SmsInboxQuery.COL_DATE} >= ?"))
        assertTrue(q.selection.contains("${SmsInboxQuery.COL_DATE} < ?"))
        assertFalse("must not filter by address/sender", q.selection.contains("address", ignoreCase = true))
        assertFalse("must not filter by body/template", q.selection.contains("body", ignoreCase = true))
        assertEquals(2, q.selectionArgs.size)
    }

    @Test
    fun queryBoundsAreEpochMillisecondsNotSeconds() {
        val range = SmsImportRange.custom(today.minusDays(3), today, today)
        val q = SmsInboxQuery.forRange(range, zone = zone)
        // 2026-era millis are 13 digits (~1.7e12); seconds would be ~1.7e9.
        assertTrue("start must be millis: ${q.startMillis}", q.startMillis > 1_000_000_000_000L)
        assertTrue("end must be millis: ${q.endMillisExclusive}", q.endMillisExclusive > 1_000_000_000_000L)
        assertFalse(SmsInboxQuery.looksLikeEpochSeconds(q.startMillis))
        assertFalse(SmsInboxQuery.looksLikeEpochSeconds(q.endMillisExclusive))
        assertEquals(q.startMillis.toString(), q.selectionArgs[0])
        assertEquals(q.endMillisExclusive.toString(), q.selectionArgs[1])
    }

    @Test
    fun looksLikeEpochSecondsDetectsTenDigitValues() {
        assertTrue(SmsInboxQuery.looksLikeEpochSeconds(1_723_000_000L)) // ~2024 as seconds
        assertFalse(SmsInboxQuery.looksLikeEpochSeconds(1_723_000_000_000L)) // millis
    }

    @Test
    fun startInclusiveEndExclusiveBoundaries() {
        val range = SmsImportRange.lastDays(today, 7)
        val q = SmsInboxQuery.forRange(range, zone = zone)
        assertTrue(q.startMillis >= q.startMillis && q.startMillis < q.endMillisExclusive)
        assertFalse(q.endMillisExclusive >= q.startMillis && q.endMillisExclusive < q.endMillisExclusive)
        val endOfToday = today.atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()
        assertTrue(endOfToday >= q.startMillis && endOfToday < q.endMillisExclusive)
    }

    @Test
    fun messagesOutsideDateRangeAreExcludedByWindowHelper() {
        val range = SmsImportRange.custom(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 5), today)
        val q = SmsInboxQuery.forRange(range, zone = zone)
        val before = LocalDate.of(2026, 7, 31).atStartOfDay(zone).toInstant().toEpochMilli()
        val inside = LocalDate.of(2026, 8, 3).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val after = LocalDate.of(2026, 8, 6).atStartOfDay(zone).toInstant().toEpochMilli()
        assertFalse(before >= q.startMillis && before < q.endMillisExclusive)
        assertTrue(inside >= q.startMillis && inside < q.endMillisExclusive)
        assertFalse(after >= q.startMillis && after < q.endMillisExclusive)
    }

    @Test
    fun secondsMistakenAsMillisWouldMissModernSms() {
        val modernSms = LocalDate.of(2026, 8, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val wrongStartSeconds = modernSms / 1000L
        val wrongEndSeconds = wrongStartSeconds + 86_400L
        assertFalse(
            "seconds-vs-millis bug excludes modern SMS",
            modernSms >= wrongStartSeconds && modernSms < wrongEndSeconds,
        )
        assertTrue(
            modernSms >= modernSms && modernSms < modernSms + 86_400_000L,
        )
    }
}

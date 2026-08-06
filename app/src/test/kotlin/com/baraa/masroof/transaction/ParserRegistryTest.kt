package com.baraa.masroof.transaction

import com.baraa.masroof.transaction.banks.AlRajhiParser
import com.baraa.masroof.transaction.banks.AlinmaParser
import com.baraa.masroof.transaction.banks.SNBParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the [BankParserRegistry] priority ordering and fallback behavior.
 *
 * The registry must:
 *  - prefer a dedicated parser when the sender matches its alias list
 *  - fall back to [GenericBankSmsParser] otherwise
 *  - never log the SMS body
 */
class ParserRegistryTest {

    @Test
    fun dedicatedParserSelectedBeforeGeneric() {
        val r = BankParserRegistry.parse(
            sender = "AlRajhi",
            body = "شراء\nبمبلغ: 250 ريال",
            smsTimestampMillis = 1_700_000_000_000L
        )
        assertEquals("AlRajhi", r.parserName)
        assertEquals(0, java.math.BigDecimal("250").compareTo(r.amount))
    }

    @Test
    fun genericFallbackUsedForUnknownSender() {
        val r = BankParserRegistry.parse(
            sender = "Unknown Sender 1234",
            body = "Purchase of SAR 50 at TestMerchant",
            smsTimestampMillis = 1_700_000_000_000L
        )
        assertEquals("Generic", r.parserName)
    }

    @Test
    fun senderAliasMatchIsCaseInsensitive() {
        // Normalization lowercases the sender before matching, so
        // "ALRAJHI" should still select the AlRajhi parser.
        val r = BankParserRegistry.parse(
            sender = "ALRAJHI",
            body = "شراء\nبمبلغ: 100 ريال",
            smsTimestampMillis = 1_700_000_000_000L
        )
        assertEquals("AlRajhi", r.parserName)
    }

    @Test
    fun differentBanksMapToTheirOwnParser() {
        val alRajhi = BankParserRegistry.parse("AlRajhi", "عملية شراء بمبلغ 100 ريال", 1_700_000_000_000L)
        val alinma = BankParserRegistry.parse("Alinma", "عملية شراء بمبلغ 100 ريال", 1_700_000_000_000L)
        val snb = BankParserRegistry.parse("SNB", "عملية شراء بمبلغ 100 ريال", 1_700_000_000_000L)
        assertEquals("AlRajhi", alRajhi.parserName)
        assertEquals("Alinma", alinma.parserName)
        assertEquals("SNB", snb.parserName)
    }

    @Test
    fun parserRegistryReportsRegisteredParsersInOrder() {
        val names = BankParserRegistry.registeredParserNames()
        assertTrue("at least one parser must be registered", names.isNotEmpty())
        // The generic fallback is always present at the end of the priority
        // list (priority 0 < any dedicated bank's priority 100).
        assertEquals("Generic@0", names.last())
        // The first entries must be at priority 100 (dedicated banks).
        assertTrue("first entries must be priority 100", names.dropLast(1).all { it.endsWith("@100") })
    }

    @Test
    fun alRajhiParserHasExpectedMetadata() {
        val p = AlRajhiParser()
        assertEquals("AlRajhi", p.name)
        assertEquals(100, p.priority)
        assertTrue("version must look like semver", p.version.matches(Regex("""^\d+\.\d+\.\d+$""")))
    }

    @Test
    fun alinmaParserCanParseItsAlias() {
        val p = AlinmaParser()
        assertTrue(p.canParse("alinma", "x"))
        assertTrue(p.canParse("Alinma", "x"))
        assertTrue(p.canParse("ALINMA", "x"))
        assertTrue(p.canParse("alinma bank", "x"))
    }

    @Test
    fun genericParserAcceptsAnySender() {
        val p = GenericBankSmsParser()
        assertTrue(p.canParse(null, null))
        assertTrue(p.canParse("any-sender", "any body"))
        assertTrue(p.canParse("+966501234567", "x"))
    }

    @Test
    fun alRajhiParserRejectsForeignSenders() {
        val p = AlRajhiParser()
        assertEquals(false, p.canParse("Alinma", "x"))
        assertEquals(false, p.canParse("SNB", "x"))
        assertEquals(false, p.canParse(null, "x"))
    }

    @Test
    fun snbParserAcceptsLegacyAndNewNames() {
        val p = SNBParser()
        assertTrue(p.canParse("snb", "x"))
        assertTrue(p.canParse("saudi national bank", "x"))
        assertTrue(p.canParse("البنك الأهلي السعودي", "x"))
    }

    @Test
    fun parserNameAlwaysSetOnResult() {
        // Even the empty-body fallback must carry a parser name so the UI can
        // show which parser ran.
        val r = BankParserRegistry.parse(sender = "AlRajhi", body = "", smsTimestampMillis = null)
        assertNotNull(r.parserName)
        assertTrue("parser name must not be blank", r.parserName.isNotBlank())
    }

    @Test
    fun parsersAreListedInDescendingPriorityOrder() {
        val names = BankParserRegistry.registeredParserNames()
        // Entries are like "AlRajhi@100" — extract the priority.
        val priorities = names.map { it.substringAfterLast('@').toInt() }
        assertEquals("priorities must be in non-increasing order", priorities, priorities.sortedDescending())
    }
}

package com.baraa.masroof.transaction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Unit tests for [TransactionFingerprint]. Pure JVM, no Android deps.
 */
class TransactionFingerprintTest {

    private val baseInputs = BaseInputs(
        sender = "AlRajhi",
        smsTimestamp = 1_700_000_000_000L,
        amount = BigDecimal("123.45"),
        currency = Currency.SAR,
        type = TransactionType.PURCHASE,
        merchant = "Starbucks",
        lastFour = "1234",
    )

    private data class BaseInputs(
        val sender: String?,
        val smsTimestamp: Long,
        val amount: BigDecimal,
        val currency: Currency,
        val type: TransactionType,
        val merchant: String?,
        val lastFour: String?,
    )

    private fun fp(b: BaseInputs) = TransactionFingerprint.compute(
        sender = b.sender,
        smsTimestamp = b.smsTimestamp,
        amount = b.amount,
        currency = b.currency,
        type = b.type,
        merchant = b.merchant,
        lastFour = b.lastFour,
    )

    // -- Determinism ---------------------------------------------------------

    @Test
    fun fingerprintIsDeterministic() {
        val a = fp(baseInputs)
        val b = fp(baseInputs)
        assertEquals(a, b)
    }

    @Test
    fun fingerprintIsHexString64Chars() {
        val s = fp(baseInputs)
        assertEquals("SHA-256 hex should be 64 chars", 64, s.length)
        assertTrue("should be lowercase hex", s.all { it in '0'..'9' || it in 'a'..'f' })
    }

    // -- Different inputs → different fingerprint ----------------------------

    @Test
    fun differentAmountsProduceDifferentFingerprints() {
        val a = fp(baseInputs)
        val b = fp(baseInputs.copy(amount = BigDecimal("123.46")))
        assertNotEquals(a, b)
    }

    @Test
    fun differentTimestampsProduceDifferentFingerprints() {
        val a = fp(baseInputs)
        val b = fp(baseInputs.copy(smsTimestamp = baseInputs.smsTimestamp + 1))
        assertNotEquals(a, b)
    }

    @Test
    fun differentCurrenciesProduceDifferentFingerprints() {
        val a = fp(baseInputs)
        val b = fp(baseInputs.copy(currency = Currency.USD))
        assertNotEquals(a, b)
    }

    @Test
    fun differentTypesProduceDifferentFingerprints() {
        val a = fp(baseInputs)
        val b = fp(baseInputs.copy(type = TransactionType.TRANSFER_OUT))
        assertNotEquals(a, b)
    }

    @Test
    fun differentMerchantsProduceDifferentFingerprints() {
        val a = fp(baseInputs)
        val b = fp(baseInputs.copy(merchant = "Caribou"))
        assertNotEquals(a, b)
    }

    @Test
    fun differentLastFourDigitsProduceDifferentFingerprints() {
        val a = fp(baseInputs)
        val b = fp(baseInputs.copy(lastFour = "5678"))
        assertNotEquals(a, b)
    }

    @Test
    fun differentSendersProduceDifferentFingerprints() {
        val a = fp(baseInputs)
        val b = fp(baseInputs.copy(sender = "Alinma"))
        assertNotEquals(a, b)
    }

    // -- Normalization (whitespace / case) ----------------------------------

    @Test
    fun senderWhitespaceIsNormalized() {
        val a = fp(baseInputs)
        val b = fp(baseInputs.copy(sender = "  AlRajhi  "))
        assertEquals("leading/trailing whitespace should normalize", a, b)
    }

    @Test
    fun senderCaseIsNormalized() {
        val a = fp(baseInputs)
        val b = fp(baseInputs.copy(sender = "ALRAJHI"))
        assertEquals("case should normalize", a, b)
    }

    @Test
    fun arabicMerchantWhitespaceIsNormalized() {
        val arabicBase = baseInputs.copy(merchant = "ستاربكس")
        val a = fp(arabicBase)
        val b = fp(arabicBase.copy(merchant = "  ستاربكس  "))
        assertEquals("Arabic merchant whitespace should normalize", a, b)
    }

    // -- Missing optional fields --------------------------------------------

    @Test
    fun nullSenderIsAccepted() {
        val a = fp(baseInputs.copy(sender = null))
        val b = fp(baseInputs.copy(sender = null))
        assertEquals(a, b)
    }

    @Test
    fun nullMerchantIsAccepted() {
        val a = fp(baseInputs.copy(merchant = null))
        val b = fp(baseInputs.copy(merchant = null))
        assertEquals(a, b)
    }

    @Test
    fun nullLastFourIsAccepted() {
        val a = fp(baseInputs.copy(lastFour = null))
        val b = fp(baseInputs.copy(lastFour = null))
        assertEquals(a, b)
    }

    @Test
    fun nullLastFourVsEmptyLastFourProduceSameFingerprint() {
        // The hash treats null and "" as the same "no last four" state so that
        // two parsers that represent the absence differently still dedupe.
        val a = fp(baseInputs.copy(lastFour = null))
        val b = fp(baseInputs.copy(lastFour = ""))
        assertEquals(a, b)
    }
}

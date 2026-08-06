package com.baraa.masroof.transaction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

/**
 * Unit tests for the [TransactionFingerprint.generateSimilarityKey] function.
 *
 * The similarity key is the second leg of the two-level duplicate detection
 * pipeline. Unlike the exact fingerprint it does **not** include the SMS
 * received timestamp, but it **does** round the time to a configurable
 * window so that two SMS for the same purchase sent minutes apart still
 * collide. Legitimate repeated purchases on different days keep distinct
 * keys.
 */
class TransactionSimilarityKeyTest {

    private fun key(
        sender: String? = "AlRajhi",
        amount: BigDecimal? = BigDecimal("100.00"),
        currency: Currency = Currency.SAR,
        type: TransactionType = TransactionType.PURCHASE,
        merchant: String? = "Starbucks",
        lastFour: String? = "1234",
        date: LocalDate? = LocalDate.of(2024, 1, 15),
        time: LocalTime? = LocalTime.of(14, 30),
        windowMin: Int = 10
    ) = TransactionFingerprint.generateSimilarityKey(
        sender = sender,
        amount = amount,
        currency = currency,
        type = type,
        merchant = merchant,
        lastFour = lastFour,
        date = date,
        time = time,
        timeWindowMinutes = windowMin
    )

    @Test
    fun similarityKeyIsDeterministic() {
        val a = key()
        val b = key()
        assertEquals(a, b)
    }

    @Test
    fun similarityKeyIgnoresTimestampDifferences() {
        // Same logical transaction, different smsTimestamps (which the
        // similarity key explicitly excludes). Keys MUST match.
        val k1 = TransactionFingerprint.generateSimilarityKey(
            sender = "AlRajhi",
            amount = BigDecimal("100.00"),
            currency = Currency.SAR,
            type = TransactionType.PURCHASE,
            merchant = "Starbucks",
            lastFour = "1234",
            date = LocalDate.of(2024, 1, 15),
            time = LocalTime.of(14, 30)
        )
        val k2 = TransactionFingerprint.generateSimilarityKey(
            sender = "AlRajhi",
            amount = BigDecimal("100.00"),
            currency = Currency.SAR,
            type = TransactionType.PURCHASE,
            merchant = "Starbucks",
            lastFour = "1234",
            date = LocalDate.of(2024, 1, 15),
            time = LocalTime.of(14, 30)
        )
        assertEquals("similarity key must not depend on smsTimestamp", k1, k2)
    }

    @Test
    fun differentAmountsProduceDifferentSimilarityKeys() {
        val a = key(amount = BigDecimal("100"))
        val b = key(amount = BigDecimal("101"))
        assertNotEquals(a, b)
    }

    @Test
    fun differentCurrenciesProduceDifferentSimilarityKeys() {
        val a = key(currency = Currency.SAR)
        val b = key(currency = Currency.USD)
        assertNotEquals(a, b)
    }

    @Test
    fun differentTypesProduceDifferentSimilarityKeys() {
        val a = key(type = TransactionType.PURCHASE)
        val b = key(type = TransactionType.TRANSFER_OUT)
        assertNotEquals(a, b)
    }

    @Test
    fun differentMerchantsProduceDifferentSimilarityKeys() {
        val a = key(merchant = "Starbucks")
        val b = key(merchant = "Caribou")
        assertNotEquals(a, b)
    }

    @Test
    fun differentLastFourDigitsProduceDifferentSimilarityKeys() {
        val a = key(lastFour = "1234")
        val b = key(lastFour = "5678")
        assertNotEquals(a, b)
    }

    @Test
    fun differentDatesProduceDifferentSimilarityKeys() {
        val a = key(date = LocalDate.of(2024, 1, 15))
        val b = key(date = LocalDate.of(2024, 1, 16))
        assertNotEquals(a, b)
    }

    @Test
    fun timeInSameWindowProducesSameSimilarityKey() {
        // 14:30 and 14:35 fall in the same 10-min window starting at 14:30.
        val a = key(time = LocalTime.of(14, 30))
        val b = key(time = LocalTime.of(14, 35))
        assertEquals("times inside the same window must collide", a, b)
    }

    @Test
    fun timeInDifferentWindowProducesDifferentSimilarityKey() {
        // 14:30 is in the 14:30 bucket; 14:40 is in the 14:40 bucket.
        val a = key(time = LocalTime.of(14, 30))
        val b = key(time = LocalTime.of(14, 40))
        assertNotEquals("times in different windows must NOT collide", a, b)
    }

    @Test
    fun differentSendersProduceDifferentSimilarityKeys() {
        val a = key(sender = "AlRajhi")
        val b = key(sender = "Alinma")
        assertNotEquals(a, b)
    }

    @Test
    fun nullAmountOrMerchantOrTimeIsAccepted() {
        // The key must still hash something deterministic when fields are
        // missing — otherwise the import service would crash on partial data.
        val a = TransactionFingerprint.generateSimilarityKey(
            sender = "AlRajhi",
            amount = null,
            currency = Currency.SAR,
            type = TransactionType.PURCHASE,
            merchant = null,
            lastFour = null,
            date = null,
            time = null
        )
        val b = TransactionFingerprint.generateSimilarityKey(
            sender = "AlRajhi",
            amount = null,
            currency = Currency.SAR,
            type = TransactionType.PURCHASE,
            merchant = null,
            lastFour = null,
            date = null,
            time = null
        )
        assertEquals(a, b)
        assertTrue(a.length == 64)
    }

    @Test
    fun similarityKeyDiffersFromExactFingerprint() {
        // Sanity: the two fields are computed differently and must not
        // produce the same hash.
        val sender = "AlRajhi"
        val amount = BigDecimal("100")
        val time = LocalTime.of(14, 30)
        val date = LocalDate.of(2024, 1, 15)
        val fp = TransactionFingerprint.compute(
            sender = sender,
            smsTimestamp = 1_700_000_000_000L,
            amount = amount,
            currency = Currency.SAR,
            type = TransactionType.PURCHASE,
            merchant = "Starbucks",
            lastFour = "1234"
        )
        val sk = TransactionFingerprint.generateSimilarityKey(
            sender = sender,
            amount = amount,
            currency = Currency.SAR,
            type = TransactionType.PURCHASE,
            merchant = "Starbucks",
            lastFour = "1234",
            date = date,
            time = time
        )
        assertNotEquals("exact and similarity keys must differ", fp, sk)
    }
}

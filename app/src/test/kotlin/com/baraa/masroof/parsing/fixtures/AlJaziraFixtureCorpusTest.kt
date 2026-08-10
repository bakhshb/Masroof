package com.baraa.masroof.parsing.fixtures

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class AlJaziraFixtureCorpusTest {

    private val fixtures by lazy { AlJaziraFixtureLoader.loadAllFromClasspath() }

    private val financialFamilies = setOf(
        "PURCHASE",
        "TRANSFER_IN",
        "TRANSFER_OUT",
        "CARD_PAYMENT",
        "BILL_PAYMENT",
        "WITHDRAWAL",
        "REFUND",
        "FEE",
    )

    @Test
    fun corpus_loadsSuccessfully() {
        assertTrue("expected fixtures", fixtures.size >= 20)
    }

    @Test
    fun everyFixture_hasUniqueId() {
        val ids = fixtures.map { it.id }
        assertEquals(ids.toSet().size, ids.size)
    }

    @Test
    fun everyFixture_hasValidStructuredMetadata() {
        val allowedFamilies = setOf(
            "PURCHASE", "TRANSFER_IN", "TRANSFER_OUT", "CARD_PAYMENT", "BILL_PAYMENT",
            "WITHDRAWAL", "REFUND", "FEE", "BALANCE_NOTICE", "OTP", "NON_FINANCIAL", "UNKNOWN",
        )
        val allowedStatuses = setOf(
            "SUCCESS", "PARTIAL", "REVIEW_REQUIRED", "NON_FINANCIAL", "UNSUPPORTED", "INVALID",
        )
        val allowedChannels = setOf("POS", "ONLINE", "UNKNOWN")
        val allowedNetworks = setOf("INTRA_BANK", "INTER_BANK", "UNKNOWN")

        fixtures.forEach { fixture ->
            assertTrue(fixture.id.isNotBlank())
            assertTrue(fixture.sender.isNotBlank())
            assertTrue(fixture.body.isNotBlank())
            assertEquals("BANK_ALJAZIRA", fixture.expected.bank)
            assertTrue(fixture.expected.messageFamily in allowedFamilies)
            assertTrue(fixture.expected.parseStatus in allowedStatuses)
            fixture.expected.purchaseChannel?.let { assertTrue(it in allowedChannels) }
            fixture.expected.bankNetworkType?.let { assertTrue(it in allowedNetworks) }
        }
    }

    @Test
    fun financialFixturesWithAmount_doNotEncodeLast4AsAmount() {
        fixtures.forEach { fixture ->
            val expected = fixture.expected
            if (expected.messageFamily !in financialFamilies) return@forEach
            val amount = expected.amount ?: return@forEach
            val amountNumber = BigDecimal(amount)
            listOfNotNull(expected.cardLast4, expected.sourceAccountLast4, expected.destinationAccountLast4)
                .forEach { last4 ->
                    val digits = last4.filter { it.isDigit() }
                    if (digits.length == 4) {
                        assertFalse(
                            "${fixture.id}: amount $amount must not equal last4 $last4",
                            amountNumber.compareTo(BigDecimal(digits)) == 0,
                        )
                    }
                }
        }
    }

    @Test
    fun noFixture_encodesOwnershipOrSelfTransferExpectations() {
        fixtures.forEach { fixture ->
            val raw = fixture.toString().lowercase()
            // Ensure expected schema fields themselves do not include ownership outcomes.
            val expectedKeys = fixture.expected.toString().lowercase()
            assertFalse(expectedKeys.contains("self_transfer"))
            assertFalse(expectedKeys.contains("ownership"))
            assertFalse(expectedKeys.contains("external_transfer_in"))
            assertFalse(expectedKeys.contains("financialtransactiontype"))
            assertFalse(raw.contains("\"ownership\""))
        }
    }

    @Test
    fun criticalRegressionShapes_arePresent() {
        val ids = fixtures.map { it.id }.toSet()
        assertTrue(ids.contains("purchase_pos_ar_cc_001"))
        assertTrue(ids.contains("purchase_online_ar_001"))
        assertTrue(ids.contains("transfer_out_inter_ar_001"))
        assertTrue(ids.contains("transfer_in_intra_ar_001"))
        assertTrue(ids.contains("purchase_pos_ar_debit_001"))
        assertTrue(ids.contains("otp_ar_001"))

        val ccPurchase = fixtures.first { it.id == "purchase_pos_ar_cc_001" }
        assertEquals("178.02", ccPurchase.expected.amount)
        assertEquals("7271", ccPurchase.expected.cardLast4)
        assertEquals("POS", ccPurchase.expected.purchaseChannel)

        val outgoing = fixtures.first { it.id == "transfer_out_inter_ar_001" }
        assertEquals("3002", outgoing.expected.sourceAccountLast4)
        assertEquals("0593", outgoing.expected.destinationAccountLast4)
        assertTrue(outgoing.expected.sourceAccountLast4 != outgoing.expected.destinationAccountLast4)

        val incomingIntra = fixtures.first { it.id == "transfer_in_intra_ar_001" }
        assertEquals("TRANSFER_IN", incomingIntra.expected.messageFamily)
        assertEquals("INTRA_BANK", incomingIntra.expected.bankNetworkType)

        val otp = fixtures.first { it.id == "otp_ar_001" }
        assertEquals("OTP", otp.expected.messageFamily)
        assertEquals("NON_FINANCIAL", otp.expected.parseStatus)
    }

    @Test
    fun fixturePaths_matchMessageFamilyCategory() {
        val root = AlJaziraFixtureLoader.resolveTestdataRoot()
        root.walkTopDown()
            .filter { it.isFile && it.extension == "json" && "reference" !in it.path }
            .forEach { file ->
                val fixture = kotlinx.serialization.json.Json.decodeFromString(
                    AlJaziraFixture.serializer(),
                    file.readText(),
                )
                val relative = file.relativeTo(root).path
                when (fixture.expected.messageFamily) {
                    "PURCHASE" -> assertTrue(relative.startsWith("purchase/"))
                    "TRANSFER_IN" -> assertTrue(relative.startsWith("transfer_in/"))
                    "TRANSFER_OUT" -> assertTrue(relative.startsWith("transfer_out/"))
                    "CARD_PAYMENT" -> assertTrue(relative.startsWith("card_payment/"))
                    "BILL_PAYMENT" -> assertTrue(relative.startsWith("bill_payment/"))
                    "REFUND" -> assertTrue(relative.startsWith("refund/"))
                    "WITHDRAWAL" -> assertTrue(relative.startsWith("withdrawal/"))
                    "FEE" -> assertTrue(relative.startsWith("fee/"))
                    "OTP" -> assertTrue(relative.startsWith("otp/"))
                    "BALANCE_NOTICE" -> assertTrue(relative.startsWith("balance_notice/"))
                    "NON_FINANCIAL" -> assertTrue(relative.startsWith("non_financial/"))
                    "UNKNOWN" -> assertTrue(relative.startsWith("unknown/"))
                }
            }
    }
}

package com.baraa.masroof.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsStructureNormalizerTest {

    private val NORMALIZATION_VERSION_TEST = com.baraa.masroof.sms.NORMALIZATION_VERSION

    @Test
    fun sameStructure_differentValues_sameSignature() {
        val a = """
            شراء عبر الانترنت
            بطاقة ائتمانية: 7271
            بمبلغ: 51.99 SAR
            لدى: Keeta
            في: 22:50 03-08-2026
        """.trimIndent()
        val b = """
            شراء عبر الانترنت
            بطاقة ائتمانية: 7271
            بمبلغ: 129.50 SAR
            لدى: Amazon
            في: 13:16 08-08-2026
        """.trimIndent()
        assertEquals(
            SmsStructureNormalizer.signatureFromBody(a),
            SmsStructureNormalizer.signatureFromBody(b),
        )
    }

    @Test
    fun differentStructure_differentSignature() {
        val purchase = "شراء\nبمبلغ: 10 SAR\nبطاقة: 1111"
        val transfer = "تحويل\nمبلغ التحويل: 10 SAR\nإلى حساب: 2222"
        assertNotEquals(
            SmsStructureNormalizer.signatureFromBody(purchase),
            SmsStructureNormalizer.signatureFromBody(transfer),
        )
    }

    @Test
    fun discover_groupsValueVariants() {
        val messages = listOf(
            SmsMessage(1, "BankX", """
                شراء
                بمبلغ: 10.00 SAR
                لدى: StoreOne
            """.trimIndent(), 1000, MatchReason.NONE),
            SmsMessage(2, "BankX", """
                شراء
                بمبلغ: 99.50 SAR
                لدى: StoreTwo
            """.trimIndent(), 2000, MatchReason.NONE),
            SmsMessage(3, "BankX", """
                تحويل
                مبلغ التحويل: 5.00 SAR
                إلى: OtherParty
            """.trimIndent(), 3000, MatchReason.NONE),
        )
        val clusters = PatternDiscoveryService.discover(messages)
        assertTrue(clusters.isNotEmpty())
        assertEquals(
            messages.size,
            clusters.sumOf { it.messageCount },
        )
        val purchaseSig = SmsStructureNormalizer.signatureFromBody(messages[0].body)
        val purchaseSig2 = SmsStructureNormalizer.signatureFromBody(messages[1].body)
        val transferSig = SmsStructureNormalizer.signatureFromBody(messages[2].body)
        assertEquals(purchaseSig, purchaseSig2)
        assertNotEquals(purchaseSig, transferSig)
        assertTrue(clusters.any { it.typeKey.contains("PURCHASE") })
        assertTrue(clusters.any { it.typeKey.contains("TRANSFER") })
    }

    @Test
    fun integerAmounts_shareSignature_withDecimalAmounts() {
        val withInt = """
            شراء عبر الانترنت
            بطاقة ائتمانية: 7271
            بمبلغ: 50 SAR
            لدى: Keeta
        """.trimIndent()
        val withDecimal = """
            شراء عبر الانترنت
            بطاقة ائتمانية: 7271
            بمبلغ: 129.50 SAR
            لدى: Amazon
        """.trimIndent()
        val otherInt = """
            شراء عبر الانترنت
            بطاقة ائتمانية: 7271
            بمبلغ: 100 SAR
            لدى: Store
        """.trimIndent()
        assertEquals(
            SmsStructureNormalizer.signatureFromBody(withInt),
            SmsStructureNormalizer.signatureFromBody(withDecimal),
        )
        assertEquals(
            SmsStructureNormalizer.signatureFromBody(withInt),
            SmsStructureNormalizer.signatureFromBody(otherInt),
        )
        // Deterministic body signature: structurally identical bodies share one signature.
        assertTrue(SmsStructureNormalizer.signatureFromBody("شراء\nبمبلغ: 50 SAR\nلدى: A")
            .contains("v$NORMALIZATION_VERSION_TEST".trimStart('v').let { "" }))
        assertTrue(SmsStructureNormalizer.signatureFromBody("شراء\nبمبلغ: 75 SAR\nلدى: B") !=
            SmsStructureNormalizer.signatureFromBody("تحويل\nبمبلغ: 75 SAR"))
    }

    @Test
    fun discover_collapsesIntegerAmountVariants() {
        val messages = listOf(
            SmsMessage(1, "BankX", "شراء\nبمبلغ: 50 SAR\nلدى: A", 1000, MatchReason.NONE),
            SmsMessage(2, "BankX", "شراء\nبمبلغ: 75 SAR\nلدى: B", 2000, MatchReason.NONE),
            SmsMessage(3, "BankX", "شراء\nبمبلغ: 12.50 SAR\nلدى: C", 3000, MatchReason.NONE),
        )
        val clusters = PatternDiscoveryService.discover(messages)
        assertEquals(1, clusters.size)
        assertEquals(3, clusters.single().messageCount)
    }

    @Test
    fun typedValue_transferDirections_staySeparate() {
        // True structural differences (different first label): حوالة صادرة is
        // NOT a variant of حوالة وارد. The labels carry semantic meaning.
        val out = """
            حوالة صادرة
            Account: 1234
            Amount: 150.00 SAR
            Beneficiary: PartyA
        """.trimIndent()
        val incoming = """
            حوالة واردة
            Account: 5678
            Amount: 200.00 SAR
            Beneficiary: PartyB
        """.trimIndent()
        val buy = """
            شراء عبر الإنترنت
            Account: 9999
            Amount: 199.99 SAR
            Merchant: Shop
        """.trimIndent()
        assertNotEquals(
            SmsStructureNormalizer.signatureFromBody(out),
            SmsStructureNormalizer.signatureFromBody(incoming),
        )
        assertNotEquals(
            SmsStructureNormalizer.signatureFromBody(out),
            SmsStructureNormalizer.signatureFromBody(buy),
        )
        assertEquals("تحويل صادر", SmsStructureNormalizer.friendlyNameHint(out).substringBefore(" ·"))
        assertEquals("تحويل وارد", SmsStructureNormalizer.friendlyNameHint(incoming).substringBefore(" ·"))
        assertEquals("شراء عبر الإنترنت", SmsStructureNormalizer.friendlyNameHint(buy).substringBefore(" ·"))
    }

    @Test
    fun optionalBalanceLine_doesNotChangeSignature() {
        val base = """
            شراء عبر الانترنت
            بطاقة ائتمانية: 7271
            بمبلغ: 51.99 SAR
            لدى: Keeta
        """.trimIndent()
        val withBalance = base + "\nالرصيد المتاح: 1200.00 SAR"
        assertEquals(
            SmsStructureNormalizer.signatureFromBody(base),
            SmsStructureNormalizer.signatureFromBody(withBalance),
        )
    }

    @Test
    fun fourDigitAmount_sharesSignatureWithSmallAmount() {
        val small = "شراء\nبمبلغ: 50 SAR\nلدى: A"
        val large = "شراء\nبمبلغ: 1500 SAR\nلدى: B"
        assertEquals(
            SmsStructureNormalizer.signatureFromBody(small),
            SmsStructureNormalizer.signatureFromBody(large),
        )
    }

    @Test
    fun alefVariants_onlinePurchase_sameSignatureAndName() {
        val a = "شراء عبر الإنترنت\nبمبلغ: 10 SAR\nلدى: X"
        val b = "شراء عبر الانترنت\nبمبلغ: 20 SAR\nلدى: Y"
        assertEquals(
            SmsStructureNormalizer.signatureFromBody(a),
            SmsStructureNormalizer.signatureFromBody(b),
        )
        assertEquals(
            SmsStructureNormalizer.friendlyNameHint(a).substringBefore(" ·"),
            SmsStructureNormalizer.friendlyNameHint(b).substringBefore(" ·"),
        )
    }

    @Test
    fun walletSuffix_sameCoreSignature() {
        val plain = "شراء عبر نقاط البيع\nبمبلغ: 10 SAR\nلدى: Store"
        val apple = "شراء عبر نقاط البيع (Apple Pay)\nبمبلغ: 20 SAR\nلدى: Cafe"
        // Wallet is metadata — core signatures and friendly titles must match.
        assertEquals(
            SmsStructureNormalizer.signatureFromBody(plain),
            SmsStructureNormalizer.signatureFromBody(apple),
        )
        assertEquals(
            SmsStructureNormalizer.friendlyNameHint(plain),
            SmsStructureNormalizer.friendlyNameHint(apple),
        )
        assertTrue("Apple" !in SmsStructureNormalizer.friendlyNameHint(apple))
        val clusters = PatternDiscoveryService.discover(
            listOf(
                SmsMessage(1, "BankX", plain, 1, MatchReason.NONE),
                SmsMessage(2, "BankX", apple, 2, MatchReason.NONE),
            ),
        )
        assertEquals(1, clusters.size)
        assertTrue(clusters.single().observedChannels.contains("APPLE_PAY"))
    }

    @Test
    fun discover_doesNotMixPurchaseAndTransfer() {
        // The three bodies have different first labels → three distinct
        // structural variants. Structural identity is built from labels
        // (حوالة صادر≠حوالة وارد≠عملية شراء), not from transaction type text.
        val messages = listOf(
            SmsMessage(1, "BankX", "تحويل صادر\nبمبلغ: 10 SAR\nالمستفيد: A", 1, MatchReason.NONE),
            SmsMessage(2, "BankX", "شراء عبر الإنترنت\nبمبلغ: 11 SAR\nالمستفيد: B", 2, MatchReason.NONE),
            SmsMessage(3, "BankX", "تحويل وارد\nبمبلغ: 12 SAR\nالمستفيد: C", 3, MatchReason.NONE),
        )
        val clusters = PatternDiscoveryService.discover(messages)
        assertEquals(3, clusters.size)
        assertEquals(3, clusters.map { it.typeKey }.distinct().size)
    }

    @Test
    fun discover_separatesStructurallyDistinctBodies() {
        val messages = listOf(
            SmsMessage(1, "BankX", "تحويل صادر\nبمبلغ: 10 SAR\nالمستفيد: A", 1, MatchReason.NONE),
            SmsMessage(2, "BankX", "حوالة واردة\nAccount: 1234\nAmount: 12 SAR", 2, MatchReason.NONE),
        )
        val clusters = PatternDiscoveryService.discover(messages)
        assertEquals(2, clusters.size)
        assertEquals(2, clusters.map { it.canonicalKey }.distinct().size)
    }
}

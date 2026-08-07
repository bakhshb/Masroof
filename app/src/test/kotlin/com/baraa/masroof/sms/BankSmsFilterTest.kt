package com.baraa.masroof.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [BankSmsFilter].
 *
 * Naming convention: `methodUnderTest_expectedBehavior` so test failures
 * are self-describing in the JUnit report.
 */
class BankSmsFilterTest {

    // -- Sender normalization -------------------------------------------------

    @Test
    fun normalizeSender_trimsAndLowercases() {
        assertEquals("alrajhi", BankSmsFilter.normalizeSender("  AlRajhi  "))
    }

    @Test
    fun normalizeSender_removesSpaces() {
        assertEquals("alrajhibank", BankSmsFilter.normalizeSender("Al Rajhi Bank"))
    }

    @Test
    fun normalizeSender_removesHyphens() {
        assertEquals("alrajhi", BankSmsFilter.normalizeSender("al-rajhi"))
    }

    @Test
    fun normalizeSender_removesUnderscoresAndDotsAndSlashes() {
        // Underscore, dot, and slash are all treated as separators; letters
        // themselves (like the "uk" in "co/uk") are NOT stripped because we
        // only collapse separator characters, not arbitrary letters.
        assertEquals("stcbankco", BankSmsFilter.normalizeSender("STC_Bank.co"))
    }

    @Test
    fun normalizeSender_stripsAdPrefix() {
        assertEquals("alrajhi", BankSmsFilter.normalizeSender("AD-AlRajhi"))
    }

    @Test
    fun normalizeSender_stripsSmsPrefix() {
        assertEquals("meem", BankSmsFilter.normalizeSender("SMS-Meem"))
    }

    @Test
    fun normalizeSender_appliesNFKCForFullWidthAndLigatures() {
        // "ＡｌＲａｊｈｉ" is full-width; NFKC folds to "AlRajhi"
        assertEquals("alrajhi", BankSmsFilter.normalizeSender("ＡｌＲａｊｈｉ"))
    }

    @Test
    fun normalizeSender_emptyAndNullReturnEmpty() {
        assertEquals("", BankSmsFilter.normalizeSender(""))
        assertEquals("", BankSmsFilter.normalizeSender("   "))
        assertEquals("", BankSmsFilter.normalizeSender(null))
    }

    // -- Known sender matching (including spaces / hyphens) -------------------

    @Test
    fun isKnownFinancialSender_exactRawNameMatches() {
        assertTrue(BankSmsFilter.isKnownFinancialSender("AlRajhi"))
        assertTrue(BankSmsFilter.isKnownFinancialSender("meem"))
        assertTrue(BankSmsFilter.isKnownFinancialSender("STCBank"))
    }

    @Test
    fun isKnownFinancialSender_isCaseInsensitive() {
        assertTrue(BankSmsFilter.isKnownFinancialSender("VISA"))
        assertTrue(BankSmsFilter.isKnownFinancialSender("visa"))
        assertTrue(BankSmsFilter.isKnownFinancialSender("ViSa"))
    }

    @Test
    fun isKnownFinancialSender_toleratesSpaces() {
        assertTrue(BankSmsFilter.isKnownFinancialSender("Al Rajhi"))
        assertTrue(BankSmsFilter.isKnownFinancialSender("Riyad Bank"))
    }

    @Test
    fun isKnownFinancialSender_toleratesHyphens() {
        assertTrue(BankSmsFilter.isKnownFinancialSender("Bank-Albilad"))
        assertTrue(BankSmsFilter.isKnownFinancialSender("al-rajhi"))
    }

    @Test
    fun isKnownFinancialSender_toleratesPrefixes() {
        assertTrue(BankSmsFilter.isKnownFinancialSender("AD-Meem"))
        assertTrue(BankSmsFilter.isKnownFinancialSender("SMS-Mastercard"))
    }

    @Test
    fun isKnownFinancialSender_unknownSenderDoesNotMatch() {
        assertFalse(BankSmsFilter.isKnownFinancialSender("RandomTelco"))
        assertFalse(BankSmsFilter.isKnownFinancialSender("PizzaPlace"))
        assertFalse(BankSmsFilter.isKnownFinancialSender("+1-555-0100"))
    }

    @Test
    fun isKnownFinancialSender_emptyAndNullReturnFalse() {
        assertFalse(BankSmsFilter.isKnownFinancialSender(""))
        assertFalse(BankSmsFilter.isKnownFinancialSender("   "))
        assertFalse(BankSmsFilter.isKnownFinancialSender(null))
    }

    // -- Arabic keyword matching ----------------------------------------------

    @Test
    fun arabicKeywords_purchaseWithRialMatches() {
        assertTrue(
            BankSmsFilter.isLikelyFinancialMessage(
                sender = "Unknown",
                body = "شراء\nبمبلغ: 50 ريال\nمن حسابك\nالرصيد المتبقي: 1000 ريال"
            )
        )
    }

    @Test
    fun arabicKeywords_withdrawalMatches() {
        assertTrue(
            BankSmsFilter.isLikelyFinancialMessage(
                sender = "Unknown",
                body = "تم سحب مبلغ 200 ريال من حسابك في البنك"
            )
        )
    }

    @Test
    fun arabicKeywords_transferMatches() {
        assertTrue(
            BankSmsFilter.isLikelyFinancialMessage(
                sender = "Unknown",
                body = "تم تحويل مبلغ 500 ريال إلى حساب آخر بنجاح"
            )
        )
    }

    @Test
    fun arabicKeywords_depositMatches() {
        assertTrue(
            BankSmsFilter.isLikelyFinancialMessage(
                sender = "Unknown",
                body = "تم إيداع مبلغ 1500 ريال في حسابك. الرصيد الجديد 3000 ريال."
            )
        )
    }

    @Test
    fun arabicKeywords_singleKeywordAloneDoesNotMatch() {
        // Only one Arabic keyword ("حساب") — under the 2-keyword threshold.
        assertFalse(
            BankSmsFilter.isLikelyFinancialMessage(
                sender = "Unknown",
                body = "افتح حساب جديد اليوم عبر موقعنا"
            )
        )
    }

    @Test
    fun arabicKeywords_diacriticsDoNotPreventMatch() {
        // Harakat on letters should not break substring search.
        assertTrue(
            BankSmsFilter.isLikelyFinancialMessage(
                sender = "Unknown",
                body = "تم شِراءٍ بمبلغ 50 رِيالٍ من حِسابك"
            )
        )
    }

    // -- English keyword matching ---------------------------------------------

    @Test
    fun englishKeywords_purchaseWithSarMatches() {
        assertTrue(
            BankSmsFilter.isLikelyFinancialMessage(
                sender = "Unknown",
                body = "Purchase of SAR 50 at Merchant. Balance 1000 SAR."
            )
        )
    }

    @Test
    fun englishKeywords_transferWithAmountMatches() {
        assertTrue(
            BankSmsFilter.isLikelyFinancialMessage(
                sender = "Unknown",
                body = "Transfer of amount 100 SAR completed. Account balance updated."
            )
        )
    }

    @Test
    fun englishKeywords_depositMatches() {
        assertTrue(
            BankSmsFilter.isLikelyFinancialMessage(
                sender = "Unknown",
                body = "A deposit of amount 200 SAR has been credited. Your balance is 1500."
            )
        )
    }

    @Test
    fun englishKeywords_refundMatches() {
        assertTrue(
            BankSmsFilter.isLikelyFinancialMessage(
                sender = "Unknown",
                body = "A refund of amount 35 SAR has been issued to your card."
            )
        )
    }

    @Test
    fun englishKeywords_singleKeywordAloneDoesNotMatch() {
        // Only one English keyword ("card") — under the 2-keyword threshold.
        assertFalse(
            BankSmsFilter.isLikelyFinancialMessage(
                sender = "Unknown",
                body = "Please bring your card to the next visit."
            )
        )
    }

    // -- Mixed Arabic + English -----------------------------------------------

    @Test
    fun mixedArabicAndEnglish_matches() {
        assertTrue(
            BankSmsFilter.isLikelyFinancialMessage(
                sender = "Unknown",
                body = "Purchase at STORE_X: amount 50 SAR. " +
                    "تم خصم 50 ريال من حسابك. Balance: 1000 SAR."
            )
        )
    }

    @Test
    fun mixedArabicAndEnglish_knownSenderBypassesKeywordRequirement() {
        // A known sender should match even if the body is purely Arabic with a
        // single strong keyword.
        assertTrue(
            BankSmsFilter.isLikelyFinancialMessage(
                sender = "Al Rajhi",
                body = "تم تنفيذ العملية بنجاح"
            )
        )
    }

    // -- Rejection: OTP, ads, personal ----------------------------------------

    @Test
    fun otpOnlyMessage_rejected() {
        assertFalse(
            BankSmsFilter.isLikelyFinancialMessage(
                sender = "Verify",
                body = "Your OTP code is 123456. Do not share it with anyone."
            )
        )
    }

    @Test
    fun arabicOtp_rejected() {
        assertFalse(
            BankSmsFilter.isLikelyFinancialMessage(
                sender = "خدمة",
                body = "رمز التحقق الخاص بك هو 123456. لا تشاركه مع أحد."
            )
        )
    }

    @Test
    fun otpFromKnownBankWithPurchaseAmount_stillRejected() {
        // Banks often send a 3-D Secure / OTP SMS that mentions the same
        // purchase amount as the later receipt — must not become a duplicate tx.
        assertFalse(
            BankSmsFilter.isLikelyFinancialMessage(
                sender = "AlRajhi",
                body = """
                    رمز التحقق: 482913
                    لعملية شراء بمبلغ 51.99 ريال
                    لا تشارك الرمز مع أي شخص
                """.trimIndent(),
            ),
        )
        assertTrue(
            BankSmsFilter.isOtpOrAuthenticationMessage(
                """
                    رمز التحقق: 482913
                    لعملية شراء بمبلغ 51.99 ريال
                    لا تشارك الرمز مع أي شخص
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun purchaseReceiptWithOtpDisclaimerOnly_isStillFinancial() {
        // Real receipts often end with a safety note mentioning «رمز التحقق»
        // without issuing a code — that must NOT wipe the purchase SMS.
        val body = """
            شراء عبر الانترنت
            بمبلغ: 51.99 SAR
            لدى: Store
            بطاقة: 7271
            لا تشارك رمز التحقق أو بيانات بطاقتك مع أي شخص
        """.trimIndent()
        assertFalse(BankSmsFilter.isOtpOrAuthenticationMessage(body))
        assertTrue(BankSmsFilter.isLikelyFinancialMessage("AlRajhi", body))
    }

    @Test
    fun englishOtpFromKnownBank_rejected() {
        assertFalse(
            BankSmsFilter.isLikelyFinancialMessage(
                sender = "SNB",
                body = "Your OTP is 123456 for a purchase of SAR 50. Do not share this code.",
            ),
        )
    }

    @Test
    fun hotpotMerchant_notTreatedAsOtpSubstring() {
        assertFalse(
            BankSmsFilter.isOtpOrAuthenticationMessage("شراء لدى HOTPOT بمبلغ 50 ريال"),
        )
    }

    @Test
    fun normalPurchaseReceipt_notTreatedAsOtp() {
        assertTrue(
            BankSmsFilter.isLikelyFinancialMessage(
                sender = "AlRajhi",
                body = """
                    شراء عبر الانترنت
                    بمبلغ: 51.99 SAR
                    لدى: Store
                    بطاقة: 7271
                """.trimIndent(),
            ),
        )
        assertFalse(
            BankSmsFilter.isOtpOrAuthenticationMessage(
                """
                    شراء عبر الانترنت
                    بمبلغ: 51.99 SAR
                    لدى: Store
                    بطاقة: 7271
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun advertisement_rejected() {
        assertFalse(
            BankSmsFilter.isLikelyFinancialMessage(
                sender = "ShoesStore",
                body = "50% off all shoes today only! Subscribe now for more deals."
            )
        )
    }

    @Test
    fun arabicAdvertisement_rejected() {
        assertFalse(
            BankSmsFilter.isLikelyFinancialMessage(
                sender = "متجر",
                body = "خصومات كبيرة اليوم فقط على جميع الأحذية! اشترك الآن"
            )
        )
    }

    @Test
    fun deliveryNotification_rejected() {
        assertFalse(
            BankSmsFilter.isLikelyFinancialMessage(
                sender = "CargoCo",
                body = "Your package has been shipped and will arrive tomorrow."
            )
        )
    }

    @Test
    fun personalMessage_rejected() {
        assertFalse(
            BankSmsFilter.isLikelyFinancialMessage(
                sender = "Friend",
                body = "Hey, can you call me back when you can? Thanks!"
            )
        )
    }

    @Test
    fun arabicPersonalMessage_rejected() {
        assertFalse(
            BankSmsFilter.isLikelyFinancialMessage(
                sender = "صديق",
                body = "صباح الخير، اتصل بي عندما تستطيع"
            )
        )
    }

    // -- Match reason classification ------------------------------------------

    @Test
    fun classifyMessage_knownSenderReasonIsKnownSender() {
        val r = BankSmsFilter.classifyMessage("Al Rajhi", "any body content")
        assertTrue(r.isMatch)
        assertEquals(MatchReason.KNOWN_SENDER, r.reason)
    }

    @Test
    fun classifyMessage_unknownSenderWithKeywordsReasonIsKeywords() {
        val r = BankSmsFilter.classifyMessage("Unknown", "Purchase of SAR 100 today")
        assertTrue(r.isMatch)
        assertEquals(MatchReason.KEYWORDS, r.reason)
    }

    @Test
    fun classifyMessage_knownSenderWithKeywordsReasonIsBoth() {
        val r = BankSmsFilter.classifyMessage("Al Rajhi", "Purchase of SAR 100 today")
        assertTrue(r.isMatch)
        assertEquals(MatchReason.BOTH, r.reason)
    }

    @Test
    fun classifyMessage_irrelevantMessageReasonIsNone() {
        val r = BankSmsFilter.classifyMessage("Friend", "call me later")
        assertFalse(r.isMatch)
        assertEquals(MatchReason.NONE, r.reason)
    }

    // -- Null safety ----------------------------------------------------------

    @Test
    fun nullSender_doesNotCrash() {
        // Use a body with no financial keywords so the verdict is false; the
        // important property is that no NPE escapes when sender is null.
        assertFalse(
            BankSmsFilter.isLikelyFinancialMessage(
                sender = null,
                body = "Hey, are you free this weekend?"
            )
        )
    }

    @Test
    fun nullBody_doesNotCrash() {
        // Known sender should still match a null body.
        assertTrue(
            BankSmsFilter.isLikelyFinancialMessage(
                sender = "Al Rajhi",
                body = null
            )
        )
    }

    @Test
    fun bothNull_doesNotCrash() {
        val r = BankSmsFilter.classifyMessage(null, null)
        assertFalse(r.isMatch)
        assertEquals(MatchReason.NONE, r.reason)
    }
}

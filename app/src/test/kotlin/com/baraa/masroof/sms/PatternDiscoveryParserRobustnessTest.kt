package com.baraa.masroof.sms

import com.baraa.masroof.transaction.LineBasedFieldParser
import kotlin.random.Random
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternDiscoveryParserRobustnessTest {
    private val messageShapes = listOf(
        """
        عملية شراء
        بطاقة مدى: ****1234
        المبلغ: 25.50 SAR
        لدى: متجر تجريبي
        """.trimIndent(),
        """
        Online Purchase
        Credit Card: XXXX4321
        Amount: SAR 0.0
        Merchant: Example Store
        """.trimIndent(),
        """
        تحويل صادر
        من حساب: ****1111
        إلى آيبان: SA00••••2222
        المبلغ: 513 SAR
        """.trimIndent(),
        """
        تحويل وارد
        من آيبان: SA00XXXX3333
        إلى حساب: ****4444
        المبلغ: 100 SAR
        """.trimIndent(),
        """
        إيداع راتب
        إلى حساب: ****5555
        مبلغ العملية: 12000 SAR
        """.trimIndent(),
        """
        One Time Password for Online Purchase
        Code: 8164
        For: Example Merchant
        Amount: SAR 0.0
        Date: 2026-08-08 15:30
        """.trimIndent(),
        """
        رمز التحقق: 2214
        السبب: تحويل محلي - تطبيق الجوال
        المبلغ: 513
        التاريخ: 2026-08-08 15:00
        """.trimIndent(),
        "المبلغ:\nالمرجع: ${"9".repeat(10_000)}",
        "Amount:: SAR 10::Reference: ABC",
        "شراء\r\nالمبلغ: 10 SAR\rالتاجر: Example\n\n",
        "Mixed عربي English 😀\nAmount: 20 SAR\nAmount: 20 SAR",
        "No colon or structured value",
        "Card: XXXX1234 IBAN: SA00XXXX5678 Amount: 30 SAR at: Example",
    )

    @Test
    fun everyPublicDiscoveryParserHandlesKnownAndMalformedShapes() {
        messageShapes.forEach { body ->
            MessageTemplateEngine.buildFromSms(body)
            SemanticPatternCanonicalizer.fromBody(body)
            CanonicalMessageNormalizer.normalizeBody(body)
            LineBasedFieldParser.splitLines(body)
            SemanticPatternSchemaNormalizer.fromBody(body)
        }
    }

    @Test
    fun deterministicUnicodeFuzzDoesNotThrowPublicParsers() {
        val random = Random(20260808)
        val alphabet = listOf(
            "ا", "ب", "م", "غ", "A", "z", "0", "9", ":", "=", "\n", "\r",
            "\u0000", "\u200f", "\u202e", "😀",
        )
        repeat(500) {
            val body = buildString {
                repeat(random.nextInt(0, 500)) {
                    append(alphabet[random.nextInt(alphabet.size)])
                }
            }
            MessageTemplateEngine.buildFromSms(body)
            SemanticPatternCanonicalizer.fromBody(body)
            CanonicalMessageNormalizer.normalizeBody(body)
            LineBasedFieldParser.splitLines(body)
        }
    }

    @Test
    fun otpIsClassifiedBeforeTemplateStage() {
        val stages = mutableListOf<PatternDiscoveryStage>()
        val otp = SmsMessage(
            id = 1L,
            sender = "Bank",
            body = messageShapes[5],
            timestamp = 1L,
        )
        val result = PatternDiscoveryService.discoverSafely(
            listOf(otp),
            emptyList(),
        ) { _, stage -> stages += stage }

        assertTrue(result.patterns.isEmpty())
        assertTrue(result.skippedOtp == 1)
        assertFalse(PatternDiscoveryStage.TEMPLATE_BUILD in stages)
    }
}

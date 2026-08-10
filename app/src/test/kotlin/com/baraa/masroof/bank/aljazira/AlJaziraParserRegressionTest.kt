package com.baraa.masroof.bank.aljazira

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.BankNetworkType
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.ParseStatus
import com.baraa.masroof.parsing.model.AmountCandidate
import com.baraa.masroof.parsing.model.AmountSourceKind
import com.baraa.masroof.parsing.model.ParseResult
import com.baraa.masroof.parsing.model.ParsedEventDraft
import com.baraa.masroof.parsing.model.SmsParseInput
import com.baraa.masroof.parsing.validator.DefaultParsedEventValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Mandatory P4 regression coverage beyond the parameterized fixture corpus.
 */
class AlJaziraParserRegressionTest {
    private val pipeline = AlJaziraParsingPipeline()
    private val validator = DefaultParsedEventValidator()

    @Test
    fun cardLast4BeforeAmount_doesNotBecomeAmount() {
        val result = parse(
            """
            شراء عبر الانترنت
            بطاقة: 7271
            لدى: Keeta
            بمبلغ: 51.99 SAR
            في: 14:32 03-08-2026
            الرصيد المتاح: SAR 17230.03
            """.trimIndent(),
        )
        val success = result as ParseResult.Success
        assertEquals(Money.of("51.99", Currency.SAR), success.event.amount)
        assertEquals("7271", success.event.cardRef?.last4)
        assertEquals(Money.of("17230.03", Currency.SAR), success.details.availableBalance)
    }

    @Test
    fun cardBalancesAroundAmount_stayDistinct() {
        val result = parse(
            """
            شراء عبر نقاط البيع (Samsung Pay)
            بطاقة ائتمانية: 7271
            لدى: TEST_SHOP_C
            بمبلغ: 178.02 SAR
            في: 09:08 30-07-2026
            الرصيد المتاح: 18346.84 SAR
            إجمالي المبلغ المستحق:802.62 SAR
            """.trimIndent(),
        ) as ParseResult.Success
        assertEquals(Money.of("178.02", Currency.SAR), result.event.amount)
        assertEquals("7271", result.event.cardRef?.last4)
        assertEquals(Money.of("18346.84", Currency.SAR), result.details.availableBalance)
        assertEquals(Money.of("802.62", Currency.SAR), result.details.outstandingBalance)
    }

    @Test
    fun sourceDestinationAndReference_stayDistinct() {
        val result = parse(
            """
            عملية حوالة مالية صادرة مقبولة
            خصمت من حساب: 3002
            الى: TEST_BENEFICIARY
            مبلغ العملية: 13,258.00 SAR
            المعرف البديل \الايبان : 0593
            [البنك العربي الوطني]
            في: 2026-08-01 12:26
            رقم المعاملة: TEST_REFERENCE_1
            """.trimIndent(),
        ) as ParseResult.Success
        assertEquals("3002", result.event.sourceAccountRef?.maskedNumber)
        assertEquals("0593", result.event.destinationAccountRef?.maskedNumber)
        assertEquals(Money.of("13258.00", Currency.SAR), result.event.amount)
        assertEquals("TEST_REFERENCE_1", result.details.transactionReference)
        assertTrue(result.event.sourceAccountRef?.maskedNumber != result.event.destinationAccountRef?.maskedNumber)
    }

    @Test
    fun intraBankTransfer_isNotSelfTransfer() {
        val result = parse(
            """
            حوالة واردة داخلية
            مبلغ: SAR 4,445.67
            إلى: 3003
            اسم المرسل: TEST_PERSON
            رقم حساب المرسل: 3001
            البنك المرسل: بنك الجزيرة
            في: 2026-08-03 10:38
            """.trimIndent(),
        ) as ParseResult.Success
        assertEquals(MessageFamily.TRANSFER_IN, result.event.messageFamily)
        assertEquals(BankNetworkType.INTRA_BANK, result.event.bankNetworkType)
        assertFalse(result.toString().contains("SELF_TRANSFER"))
    }

    @Test
    fun crossBankTransfer_isInterBank() {
        val result = parse(
            """
            حوالة واردة: محلية
            عبر: بنك الرياض
            مبلغ: SAR 4,445.67
            إلى: 3001
            اسم المرسل: TEST_COMPANY
            البنك المرسل: بنك الرياض
            في: 2026-08-03 09:12
            """.trimIndent(),
        ) as ParseResult.Success
        assertEquals(BankNetworkType.INTER_BANK, result.event.bankNetworkType)
    }

    @Test
    fun creditCardPurchase_isPurchaseNotCardPayment() {
        val result = parse(
            """
            شراء عبر نقاط البيع (Samsung Pay)
            بطاقة ائتمانية: 7271
            لدى: TEST_SHOP_C
            بمبلغ: 178.02 SAR
            في: 09:08 30-07-2026
            """.trimIndent(),
        ) as ParseResult.Success
        assertEquals(MessageFamily.PURCHASE, result.event.messageFamily)
        assertTrue(result.event.messageFamily != MessageFamily.CARD_PAYMENT)
    }

    @Test
    fun creditCardPayment_isCardPaymentNotPurchase() {
        val result = parse(
            """
            سداد بطاقة ائتمانية
            من حساب: 3001
            بطاقة: 7271
            بمبلغ: 802.62 SAR
            في: 2026-08-04 09:00
            """.trimIndent(),
        ) as ParseResult.Success
        assertEquals(MessageFamily.CARD_PAYMENT, result.event.messageFamily)
        assertTrue(result.event.messageFamily != MessageFamily.PURCHASE)
    }

    @Test
    fun otp_isNonFinancialWithoutAmount() {
        val result = parse("رمز التحقق الخاص بك هو 482911. لا تشاركه مع أي شخص.")
        assertTrue(result is ParseResult.NonFinancial)
        val nf = result as ParseResult.NonFinancial
        assertEquals(MessageFamily.OTP, nf.event?.messageFamily)
        assertEquals(ParseStatus.NON_FINANCIAL, nf.event?.parseStatus)
        assertNull(nf.event?.amount)
    }

    @Test
    fun balanceNotice_extractsBalanceWithoutTransactionAmount() {
        val result = parse(
            """
            إشعار رصيد
            حساب: 3001
            الرصيد المتاح: SAR 17230.03
            في: 2026-08-03 08:00
            """.trimIndent(),
        ) as ParseResult.NonFinancial
        assertEquals(MessageFamily.BALANCE_NOTICE, result.event?.messageFamily)
        assertNull(result.event?.amount)
        assertEquals(Money.of("17230.03", Currency.SAR), result.details.availableBalance)
    }

    @Test
    fun unknownFormat_requiresReviewWithoutGuessedFamily() {
        val result = parse("تنبيه بنك الجزيرة: حدث تحديث في خدماتك. راجع التطبيق للتفاصيل.")
        assertTrue(result is ParseResult.ReviewRequired)
        val review = result as ParseResult.ReviewRequired
        assertEquals(MessageFamily.UNKNOWN, review.event?.messageFamily)
        assertEquals(ParseStatus.REVIEW_REQUIRED, review.event?.parseStatus)
        assertNull(review.event?.amount)
    }

    @Test
    fun billPayment_keepsBillerSeparateFromMerchant() {
        val result = parse(
            """
            سداد فاتورة
            المفوتر: TEST_BILLER
            بمبلغ: 210.00 SAR
            من حساب: 3001
            في: 2026-08-03 16:40
            """.trimIndent(),
        ) as ParseResult.Success
        assertEquals(MessageFamily.BILL_PAYMENT, result.event.messageFamily)
        assertEquals("TEST_BILLER", result.details.biller)
        assertNull(result.event.merchant)
    }

    @Test
    fun coincidentalAmountEqualToLast4_isNotUniversallyRejected() {
        val money = Money.of("3001.00", Currency.SAR)
        val draft = ParsedEventDraft(
            rawSmsId = "coin-1",
            bank = Bank.BANK_ALJAZIRA,
            messageFamily = MessageFamily.TRANSFER_OUT,
            amount = money,
            sourceAccountRef = com.baraa.masroof.domain.model.AccountReference(Bank.BANK_ALJAZIRA, "3001"),
            confidence = com.baraa.masroof.domain.model.Confidence(0.9),
            parseStatus = ParseStatus.SUCCESS,
            amountCandidates = listOf(
                AmountCandidate(money, "مبلغ", AmountSourceKind.TRANSACTION_AMOUNT),
            ),
            selectedAmount = AmountCandidate(money, "مبلغ", AmountSourceKind.TRANSACTION_AMOUNT),
        )
        val validation = validator.validate(draft)
        assertTrue(validation.errors.none { it.code == "V-001" })
        assertTrue(validation.errors.none { it.code == "V-002" })
        assertTrue(validation.isAcceptableForAutomaticUse)
    }

    @Test
    fun unrecognizedSender_isUnsupported() {
        val result = pipeline.parse(
            SmsParseInput(
                rawSmsId = "other",
                sender = "OtherBank",
                body = "شراء عبر الانترنت بمبلغ: 10.00 SAR",
                receivedAt = Instant.parse("2026-08-10T00:00:00Z"),
            ),
        )
        assertTrue(result is ParseResult.Unsupported)
    }

    private fun parse(body: String): ParseResult =
        pipeline.parse(
            SmsParseInput(
                rawSmsId = "regression",
                sender = "AlJazira",
                body = body,
                receivedAt = Instant.parse("2026-08-10T00:00:00Z"),
            ),
        )
}

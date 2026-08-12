package com.baraa.masroof.bank.aljazira.classification

import com.baraa.masroof.domain.rules.OtpMessageHeuristics
import com.baraa.masroof.domain.model.BankNetworkType
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.MoneyDirection
import com.baraa.masroof.domain.model.PurchaseChannel
import com.baraa.masroof.parsing.model.NormalizedSms

data class AlJaziraClassification(
    val family: MessageFamily,
    val direction: MoneyDirection? = null,
    val purchaseChannel: PurchaseChannel? = null,
    val bankNetworkType: BankNetworkType? = null,
    val evidence: List<String> = emptyList(),
    val confidence: Double = 0.9,
)

/**
 * Evidence-based family classification from fixture-supported aliases only.
 */
class AlJaziraMessageClassifier {
    fun classify(sms: NormalizedSms): AlJaziraClassification {
        val text = sms.comparisonBody

        when {
            OtpMessageHeuristics.isOtpMessage(text) ->
                return AlJaziraClassification(
                    family = if (text.contains("رمز التفعيل") || text.contains("لإضافة المستفيد")) {
                        MessageFamily.NON_FINANCIAL
                    } else {
                        MessageFamily.OTP
                    },
                    evidence = listOf(
                        when {
                            text.contains("رمز التفعيل") -> "activation_code"
                            text.contains("one time password") || text.contains("one-time password") ->
                                "english_otp"
                            text.contains("كلمة مرور") || text.contains("كلمة المرور") ||
                                text.contains("صالحة لمرة واحدة") -> "password_ar"
                            else -> "otp_indicator"
                        },
                    ),
                    confidence = 1.0,
                )

            text.contains("تم تسجيل الدخول") ->
                return AlJaziraClassification(
                    family = MessageFamily.NON_FINANCIAL,
                    evidence = listOf("login_notice"),
                    confidence = 1.0,
                )

            text.contains("مكافآتي") ||
                text.contains("رصيد نقاطك") ||
                text.contains("برنامج مكاف") ->
                return AlJaziraClassification(
                    family = MessageFamily.NON_FINANCIAL,
                    evidence = listOf("loyalty_points_notice"),
                    confidence = 1.0,
                )

            text.contains("اسم المستفيد") ||
                text.contains("الاسم المختصر") ||
                text.contains("تم إضافة المستفيد") ||
                text.contains("إضافة مستفيد") ||
                (text.contains("حالة") && text.contains("غير نشط")) ->
                return AlJaziraClassification(
                    family = MessageFamily.NON_FINANCIAL,
                    evidence = listOf("beneficiary_notice"),
                    confidence = 1.0,
                )

            text.contains("إشعار رصيد") || text.contains("اشعار رصيد") ->
                return AlJaziraClassification(
                    family = MessageFamily.BALANCE_NOTICE,
                    evidence = listOf("balance_notice"),
                    confidence = 1.0,
                )

            text.contains("إصدار كشف حساب") ||
                text.contains("كشف حساب") ||
                (text.contains("تاريخ الاستحقاق") && text.contains("المبلغ المستحق")) ->
                return AlJaziraClassification(
                    family = MessageFamily.NON_FINANCIAL,
                    evidence = listOf("statement_notice"),
                    confidence = 1.0,
                )

            text.contains("قسط تمويل") || text.contains("خصم: قسط") ->
                return AlJaziraClassification(
                    family = MessageFamily.UNKNOWN,
                    direction = MoneyDirection.OUTGOING,
                    evidence = listOf("financing_installment_unmapped"),
                    confidence = 0.4,
                )

            text.contains("سداد بطاقة") ->
                return AlJaziraClassification(
                    family = MessageFamily.CARD_PAYMENT,
                    direction = MoneyDirection.OUTGOING,
                    evidence = listOf("card_payment"),
                    confidence = 0.95,
                )

            text.contains("سداد فاتورة") || text.contains("المفوتر") ->
                return AlJaziraClassification(
                    family = MessageFamily.BILL_PAYMENT,
                    direction = MoneyDirection.OUTGOING,
                    evidence = listOf("bill_payment"),
                    confidence = 0.95,
                )

            text.contains("استرداد") || text.contains("refund") ->
                return AlJaziraClassification(
                    family = MessageFamily.REFUND,
                    direction = MoneyDirection.INCOMING,
                    evidence = listOf("refund"),
                    confidence = 0.95,
                )

            isPosPurchase(text) ->
                return AlJaziraClassification(
                    family = MessageFamily.PURCHASE,
                    direction = MoneyDirection.OUTGOING,
                    purchaseChannel = PurchaseChannel.POS,
                    evidence = listOf("purchase_pos"),
                    confidence = 0.95,
                )

            isOnlinePurchase(text) ->
                return AlJaziraClassification(
                    family = MessageFamily.PURCHASE,
                    direction = MoneyDirection.OUTGOING,
                    purchaseChannel = PurchaseChannel.ONLINE,
                    evidence = listOf("purchase_online"),
                    confidence = 0.95,
                )

            text.contains("رسوم") ->
                return AlJaziraClassification(
                    family = MessageFamily.FEE,
                    direction = MoneyDirection.OUTGOING,
                    evidence = listOf("fee"),
                    confidence = 0.95,
                )

            text.contains("سحب نقدي") || text.contains("withdrawal") ->
                return AlJaziraClassification(
                    family = MessageFamily.WITHDRAWAL,
                    direction = MoneyDirection.OUTGOING,
                    evidence = listOf("withdrawal"),
                    confidence = 0.95,
                )

            text.contains("حوالة واردة") ||
                text.contains("حوالة مالية واردة") ||
                text.contains("incoming transfer") ->
                return AlJaziraClassification(
                    family = MessageFamily.TRANSFER_IN,
                    direction = MoneyDirection.INCOMING,
                    bankNetworkType = detectNetwork(text, incoming = true),
                    evidence = listOf("transfer_in"),
                    confidence = 0.95,
                )

            text.contains("حوالة صادرة") ||
                text.contains("حوالة مالية صادرة") ||
                text.contains("outgoing transfer") ->
                return AlJaziraClassification(
                    family = MessageFamily.TRANSFER_OUT,
                    direction = MoneyDirection.OUTGOING,
                    bankNetworkType = detectNetwork(text, incoming = false),
                    evidence = listOf("transfer_out"),
                    confidence = 0.95,
                )

            else ->
                return AlJaziraClassification(
                    family = MessageFamily.UNKNOWN,
                    evidence = listOf("unrecognized_aljazira_format"),
                    confidence = 0.3,
                )
        }
    }

    private fun isPosPurchase(text: String): Boolean {
        val hasPos = text.contains("نقاط البيع") ||
            text.contains("pos purchase") ||
            Regex("""(?<![\p{L}])pos(?![\p{L}])""").containsMatchIn(text)
        val hasPurchase = text.contains("شراء") || text.contains("purchase")
        return hasPos && (hasPurchase || text.contains("pos purchase"))
    }

    private fun isOnlinePurchase(text: String): Boolean {
        if (OtpMessageHeuristics.isOtpMessage(text)) return false
        return text.contains("شراء عبر الانترنت") ||
            text.contains("شراء من الانترنت") ||
            text.contains("online purchase") ||
            text.contains("internet purchase")
    }

    private fun detectNetwork(text: String, incoming: Boolean): BankNetworkType {
        if (text.contains("داخلية") || text.contains("حسابك الجاري")) {
            return BankNetworkType.INTRA_BANK
        }
        if (text.contains("البنك المرسل: بنك الجزيرة") || text.contains("البنك المرسل:بنك الجزيرة")) {
            return BankNetworkType.INTRA_BANK
        }
        // External bank markers in brackets or named other banks
        if (Regex("""\[[^\]]+\]""").containsMatchIn(text)) {
            return BankNetworkType.INTER_BANK
        }
        if (text.contains("عبر:") && !text.contains("بنك الجزيرة")) {
            // e.g. عبر: بنك الرياض
            if (text.contains("بنك") && !text.contains("عبر: بنك الجزيرة")) {
                return BankNetworkType.INTER_BANK
            }
        }
        if (text.contains("البنك المرسل:") && !text.contains("البنك المرسل: بنك الجزيرة")) {
            return BankNetworkType.INTER_BANK
        }
        if (incoming && text.contains("محلية") && text.contains("بنك الرياض")) {
            return BankNetworkType.INTER_BANK
        }
        return BankNetworkType.UNKNOWN
    }
}

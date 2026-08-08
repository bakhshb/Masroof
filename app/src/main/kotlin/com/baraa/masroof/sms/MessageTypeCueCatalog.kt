package com.baraa.masroof.sms

import com.baraa.masroof.transaction.MoneyFlowDirection
import com.baraa.masroof.transaction.TransactionType
import com.baraa.masroof.transaction.TransactionTypeTaxonomy
import java.util.Locale

/**
 * Shared catalog for SMS type / direction / channel cues.
 * Used by signature clustering and friendly display names.
 */
data class MessageTypeCue(
    val displayNameAr: String,
    val transactionType: TransactionType?,
    val direction: String?,
    val channel: String?,
    /** Stable token for signatures, e.g. TYPE:TRANSFER_OUT */
    val typeToken: String,
    val channelToken: String? = null,
)

object MessageTypeCueCatalog {

    private data class Rule(
        val phrases: List<String>,
        val displayNameAr: String,
        val transactionType: TransactionType?,
        val direction: MoneyFlowDirection?,
        val typeToken: String,
    )

    /**
     * Longer / more specific phrases first.
     * Non-financial and limit-change MUST precede purchase/online-purchase so
     * phrases like «الحد اليومي للشراء عبر الانترنت» do not become ONLINE_PURCHASE.
     */
    private val RULES: List<Rule> = listOf(
        Rule(
            listOf("رمز التحقق", "otp", "one-time password", "رمز المرور لمرة واحدة"),
            "رمز التحقق", TransactionType.NON_FINANCIAL, MoneyFlowDirection.NONE, "TYPE:OTP",
        ),
        Rule(
            listOf(
                "تم تغيير الحد اليومي",
                "تغيير الحد اليومي",
                "الحد اليومي للشراء",
                "الحد اليومي للشراء عبر الانترنت",
                "الحد اليومي للشراء عبر الإنترنت",
                "daily purchase limit",
                "daily limit",
                "تغيير حد الرصيد",
                "تم تغيير الحد الائتماني",
                "تغيير الحد الائتماني",
                "الحد الائتماني الجديد",
                "حد ائتماني جديد",
                "تحديث الحد الائتماني",
                "تغيير الحد",
                "credit limit change",
                "new credit limit",
                "credit limit has been changed",
                "your credit limit",
            ),
            "تغيير حد", TransactionType.NON_FINANCIAL, MoneyFlowDirection.NONE, "TYPE:NON_FINANCIAL",
        ),
        Rule(
            listOf(
                "حوالة صادرة بين حساباتك", "حوالة واردة بين حساباتك", "حوالة بين حساباتك",
                "حوالة واردة داخلية", "حوالة صادرة داخلية", "حوالة داخلية",
                "تحويل داخلي", "تحويل بين حساباتي", "بين حساباتك", "بين حساباتي",
                "internal transfer",
            ),
            "تحويل داخلي", TransactionType.INTERNAL_TRANSFER, MoneyFlowDirection.TRANSFER, "TYPE:INTERNAL_TRANSFER",
        ),
        // SALARY must precede TRANSFER_IN: bank salary SMS often also contain
        // «حوالة واردة» / transfer phrasing. Type and direction stay separate —
        // both are INFLOW, but they must not share a template cluster.
        Rule(
            listOf(
                "إيداع راتب", "ايداع راتب", "salary credit", "salary deposit",
                "صرف راتب", "تم إيداع راتب", "تم ايداع راتب",
                "راتب", "salary", "wages", "payroll",
            ),
            "راتب", TransactionType.SALARY, MoneyFlowDirection.INFLOW, "TYPE:SALARY",
        ),
        Rule(
            listOf(
                "عملية حوالة مالية صادرة مقبولة", "حوالة مالية صادرة", "حوالة صادرة",
                "حوالة خارجة", "تحويل صادر", "تحويل خارج", "outgoing transfer", "transfer out",
            ),
            "تحويل صادر", TransactionType.TRANSFER_OUT, MoneyFlowDirection.OUTFLOW, "TYPE:TRANSFER_OUT",
        ),
        Rule(
            listOf(
                "عملية حوالة مالية واردة", "حوالة مالية واردة", "حوالة واردة",
                "تحويل وارد", "وارد إليك", "incoming transfer", "transfer in",
            ),
            "تحويل وارد", TransactionType.TRANSFER_IN, MoneyFlowDirection.INFLOW, "TYPE:TRANSFER_IN",
        ),
        Rule(
            listOf("استرداد", "مسترد", "refund", "reversed transaction"),
            "استرداد", TransactionType.REFUND, MoneyFlowDirection.INFLOW, "TYPE:REFUND",
        ),
        Rule(
            listOf(
                "شراء عبر الإنترنت", "شراء عبر الانترنت", "online purchase", "online_purchase",
            ),
            "شراء عبر الإنترنت", TransactionType.ONLINE_PURCHASE, MoneyFlowDirection.OUTFLOW, "TYPE:ONLINE_PURCHASE",
        ),
        Rule(
            listOf("شراء عبر نقاط البيع", "نقاط البيع", "pos purchase"),
            "شراء عبر نقاط البيع", TransactionType.PURCHASE, MoneyFlowDirection.OUTFLOW, "TYPE:POS_PURCHASE",
        ),
        Rule(
            listOf("عملية شراء", "شراء", "purchase"),
            "شراء عبر نقاط البيع", TransactionType.PURCHASE, MoneyFlowDirection.OUTFLOW, "TYPE:PURCHASE",
        ),
        Rule(
            listOf("سحب نقدي", "سحب من الصراف", "atm withdrawal", "cash withdrawal", "withdrawal", "سحب"),
            "سحب نقدي", TransactionType.CASH_WITHDRAWAL, MoneyFlowDirection.OUTFLOW, "TYPE:CASH_WITHDRAWAL",
        ),
        Rule(
            listOf("إيداع", "ايداع", "deposit"),
            "إيداع", TransactionType.OTHER_FINANCIAL, MoneyFlowDirection.INFLOW, "TYPE:DEPOSIT",
        ),
        Rule(
            listOf("سداد بطاقة ائتمانية", "سداد بطاقة", "card payment", "credit card payment"),
            "سداد بطاقة", TransactionType.CARD_PAYMENT, MoneyFlowDirection.OUTFLOW, "TYPE:CARD_PAYMENT",
        ),
        Rule(
            listOf("سداد فاتورة", "سداد فاتوره", "دفع فاتورة", "bill payment", "sadad", "سداد"),
            "سداد فاتورة", TransactionType.BILL_PAYMENT, MoneyFlowDirection.OUTFLOW, "TYPE:BILL_PAYMENT",
        ),
        Rule(
            listOf("رسوم", "bank fee", "service charge", "fee"),
            "رسوم بنكية", TransactionType.FEE, MoneyFlowDirection.OUTFLOW, "TYPE:FEE",
        ),
        Rule(
            listOf("قسط تمويل", "قسط القرض", "خصم قسط", "قسط شهري", "loan installment", "installment"),
            "سداد فاتورة", TransactionType.BILL_PAYMENT, MoneyFlowDirection.OUTFLOW, "TYPE:BILL_PAYMENT",
        ),
        Rule(
            listOf("تحويل", "حوالة", "transfer"),
            "تحويل صادر", TransactionType.TRANSFER_OUT, MoneyFlowDirection.OUTFLOW, "TYPE:TRANSFER",
        ),
    )

    private val CHANNELS: List<Pair<List<String>, String>> = listOf(
        listOf("apple pay", "ابل باي", "أبل باي") to "APPLE_PAY",
        listOf("google pay", "جوجل باي") to "GOOGLE_PAY",
        listOf("samsung pay", "سامسونج باي") to "SAMSUNG_PAY",
        listOf("mada pay", "مدى pay") to "MADA_PAY",
    )

    fun foldArabic(text: String): String {
        var s = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
        s = s.replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
            .replace('ة', 'ه').replace('ى', 'ي')
        return s.replace(Regex("\\s+"), " ").trim()
    }

    fun detect(body: String?): MessageTypeCue {
        val raw = body.orEmpty()
        val folded = foldArabic(BankSmsFilter.normalizeForKeywordSearch(raw))
        val channel = detectChannel(folded)
        for (rule in RULES) {
            if (rule.phrases.any { foldArabic(it) in folded }) {
                return toCue(rule, channel)
            }
        }
        return MessageTypeCue(
            displayNameAr = "نمط رسالة",
            transactionType = null,
            direction = null,
            channel = channel?.first,
            typeToken = "TYPE:UNKNOWN",
            channelToken = channel?.second,
        )
    }

    /** Detect type cue from a single label or value fragment. */
    fun detectFromFragment(text: String?): MessageTypeCue? {
        if (text.isNullOrBlank()) return null
        val folded = foldArabic(text)
        if (folded.length < 2) return null
        for (rule in RULES) {
            if (rule.phrases.any { phrase ->
                    val p = foldArabic(phrase)
                    p.isNotEmpty() && (p in folded || (folded.length >= 4 && folded in p))
                }
            ) {
                val channel = detectChannel(folded)
                return toCue(rule, channel)
            }
        }
        return null
    }

    fun stripWalletSuffix(label: String): Pair<String, String?> {
        val wallet = Regex(
            """\s*[\(（]\s*(apple\s*pay|google\s*pay|samsung\s*pay|mada\s*pay|ابل باي|أبل باي|جوجل باي|سامسونج باي|مدى\s*pay)\s*[\)）]\s*$""",
            RegexOption.IGNORE_CASE,
        )
        val match = wallet.find(label)
        if (match == null) return label.trim() to null
        val cleaned = label.removeRange(match.range).trim()
        val token = detectChannel(foldArabic(match.value))?.second?.removePrefix("CHANNEL:")
        return cleaned to token
    }

    fun isAmountLikeLabel(label: String): Boolean {
        return com.baraa.masroof.transaction.MonetaryFieldClassifier.isTransactionAmount(label)
    }

    fun isOptionalContextLabel(label: String): Boolean {
        return com.baraa.masroof.transaction.MonetaryFieldClassifier.isInformational(label)
    }

    fun isLast4LikeLabel(label: String): Boolean {
        val n = foldArabic(label)
        return listOf("بطاقه", "بطاقة", "حساب", "آيبان", "ايبان", "iban", "card", "account", "محفظه", "محفظة")
            .any { it in n }
    }

    fun isNonFinancialCue(body: String?): Boolean {
        val cue = detect(body)
        return cue.transactionType == TransactionType.NON_FINANCIAL ||
            cue.typeToken == "TYPE:OTP" ||
            cue.typeToken == "TYPE:NON_FINANCIAL"
    }

    private fun toCue(rule: Rule, channel: Pair<String, String>?): MessageTypeCue {
        val dir = rule.direction?.let { TransactionTypeTaxonomy.directionStorageName(it) }
            ?: rule.transactionType?.let {
                TransactionTypeTaxonomy.directionStorageName(TransactionTypeTaxonomy.directionOf(it))
            }
        return MessageTypeCue(
            displayNameAr = rule.displayNameAr,
            transactionType = rule.transactionType,
            direction = dir,
            channel = channel?.first,
            typeToken = rule.typeToken,
            channelToken = channel?.second,
        )
    }

    private fun detectChannel(foldedBody: String): Pair<String, String>? {
        for ((phrases, token) in CHANNELS) {
            if (phrases.any { foldArabic(it) in foldedBody }) {
                val display = when (token) {
                    "APPLE_PAY" -> "Apple Pay"
                    "GOOGLE_PAY" -> "Google Pay"
                    "SAMSUNG_PAY" -> "Samsung Pay"
                    "MADA_PAY" -> "Mada Pay"
                    else -> token
                }
                return display to "CHANNEL:$token"
            }
        }
        return null
    }
}

package com.baraa.masroof.transaction

/**
 * Semantic role of a monetary value found in a bank SMS.
 *
 * Only [TRANSACTION_AMOUNT] may become [ParsedTransaction.amount].
 * Informational balances, dues, limits, fees-as-context, etc. must not.
 */
enum class MonetaryRole {
    TRANSACTION_AMOUNT,
    AVAILABLE_BALANCE,
    OUTSTANDING_BALANCE,
    TOTAL_DUE,
    CREDIT_LIMIT,
    FEE,
    TAX,
    CASHBACK,
    OTHER_INFORMATIONAL_AMOUNT,
    UNKNOWN,
}

/**
 * Single source of truth: map a field label to its [MonetaryRole].
 *
 * Used by the generic parser, pattern discovery, and pattern extraction so
 * the same label never means "transaction amount" in one layer and
 * "due balance" in another.
 */
object MonetaryFieldClassifier {

    private fun fold(label: String): String {
        var s = java.text.Normalizer.normalize(label, java.text.Normalizer.Form.NFKC)
            .lowercase(java.util.Locale.ROOT)
            .trim()
        s = s.replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
            .replace('ة', 'ه').replace('ى', 'ي')
        return s.replace(Regex("\\s+"), " ")
    }

    private fun foldedSet(vararg labels: String): Set<String> = labels.map { fold(it) }.toSet()

    /** Exact / phrase labels for the money that moved in the transaction. */
    private val TRANSACTION_AMOUNT_LABELS = foldedSet(
        "of",
        "amount",
        "transaction amount",
        "purchase amount",
        "transfer amount",
        "payment amount",
        "debited amount",
        "credited amount",
        "withdrawal amount",
        "بمبلغ",
        "المبلغ",
        "بقيمة",
        "مبلغ",
        "مبلغ العملية",
        "قيمة العملية",
        "قيمة الشراء",
        "قيمة التحويل",
        "مبلغ التحويل",
        "قيمة السحب",
        "مبلغ السحب",
        "المبلغ المخصوم",
        "المبلغ المحول",
        "المبلغ المستقطع",
        "قيمة الخصم",
        "مبلغ الخصم",
        "قيمة الشحن",
        "مبلغ الشحن",
        "القيمة",
        "القسط",
    )

    private val AVAILABLE_BALANCE_LABELS = foldedSet(
        "available balance",
        "available balance is",
        "current balance",
        "remaining balance",
        "الرصيد",
        "الرصيد المتاح",
        "الرصيد الحالي",
        "المتبقي",
        "المبلغ المتبقي",
    )

    private val DUE_LABELS = foldedSet(
        "due amount",
        "amount due",
        "total amount due",
        "total due",
        "outstanding balance",
        "outstanding amount",
        "card amount due",
        "المبلغ المستحق",
        "اجمالي المبلغ المستحق",
        "إجمالي المبلغ المستحق",
        "المبلغ المستحق الكلي",
    )

    private val CREDIT_LIMIT_LABELS = foldedSet(
        "credit limit",
        "new credit limit",
        "الحد الائتماني",
        "الحد الائتماني الجديد",
        "حد ائتماني جديد",
    )

    private val FEE_LABELS = foldedSet(
        "fee",
        "bank fee",
        "service charge",
        "رسوم",
        "رسوم خدمة",
        "عمولة",
    )

    private val TAX_LABELS = foldedSet("tax", "vat", "ضريبة", "ضريبة القيمة المضافة")

    private val CASHBACK_LABELS = foldedSet("cashback", "cash back", "استرداد نقدي", "كاش باك")

    fun classify(label: String?): MonetaryRole {
        if (label.isNullOrBlank()) return MonetaryRole.UNKNOWN
        val n = fold(label)
        if (n.isEmpty()) return MonetaryRole.UNKNOWN

        // Exact matches first (order matters: due before bare "amount").
        when (n) {
            in DUE_LABELS -> return MonetaryRole.TOTAL_DUE
            in AVAILABLE_BALANCE_LABELS -> return MonetaryRole.AVAILABLE_BALANCE
            in CREDIT_LIMIT_LABELS -> return MonetaryRole.CREDIT_LIMIT
            in FEE_LABELS -> return MonetaryRole.FEE
            in TAX_LABELS -> return MonetaryRole.TAX
            in CASHBACK_LABELS -> return MonetaryRole.CASHBACK
            in TRANSACTION_AMOUNT_LABELS -> return MonetaryRole.TRANSACTION_AMOUNT
        }

        // Phrase containment for compound / bank-variant labels.
        // Due / outstanding MUST be checked before any "amount" substring.
        if (
            "due" in n || "مستحق" in n || "outstanding" in n ||
            "amount due" in n || "total amount due" in n
        ) {
            return MonetaryRole.TOTAL_DUE
        }
        if (
            "balance" in n || "رصيد" in n || "remaining" in n ||
            "available" in n && "balance" in n
        ) {
            return MonetaryRole.AVAILABLE_BALANCE
        }
        if ("credit limit" in n || ("حد" in n && "ائتمان" in n)) {
            return MonetaryRole.CREDIT_LIMIT
        }
        if ("cashback" in n || "cash back" in n || "كاش باك" in n) {
            return MonetaryRole.CASHBACK
        }
        if ("vat" in n || "tax" in n || "ضريبة" in n) {
            return MonetaryRole.TAX
        }
        if ("fee" in n || "رسوم" in n || "عمولة" in n || "service charge" in n) {
            // Standalone fee lines can be the transaction itself; labeled
            // "fee amount" under a purchase SMS is informational. Prefer
            // OTHER unless the whole label is a known fee-as-amount cue.
            return if (n in FEE_LABELS || n.endsWith(" fee") || n.startsWith("fee ")) {
                MonetaryRole.FEE
            } else {
                MonetaryRole.OTHER_INFORMATIONAL_AMOUNT
            }
        }

        // Positive transaction-amount cues (never bare "amount" alone here —
        // bare "amount" is already in TRANSACTION_AMOUNT_LABELS as exact match).
        if (
            (n.endsWith(" amount") && "due" !in n && "balance" !in n && "limit" !in n) ||
            "transaction amount" in n || "purchase amount" in n ||
            "transfer amount" in n || "payment amount" in n ||
            "debited" in n || "credited" in n ||
            "بمبلغ" in n || "مبلغ العمليه" in n || "مبلغ العملية" in n ||
            "قيمه العمليه" in n || "قيمة العملية" in n ||
            "قيمه الشراء" in n || "قيمه التحويل" in n || "مبلغ التحويل" in n ||
            "قيمه السحب" in n || "قيمه الشحن" in n || n == "القيمه" || n == "قيمه"
        ) {
            return MonetaryRole.TRANSACTION_AMOUNT
        }

        return MonetaryRole.UNKNOWN
    }

    fun isTransactionAmount(label: String?): Boolean =
        classify(label) == MonetaryRole.TRANSACTION_AMOUNT

    fun isInformational(label: String?): Boolean = when (classify(label)) {
        MonetaryRole.TRANSACTION_AMOUNT, MonetaryRole.UNKNOWN -> false
        else -> true
    }

    /** Map to pattern canonical field when the role is pattern-persistable. */
    fun toPatternField(role: MonetaryRole): com.baraa.masroof.data.db.PatternCanonicalField? =
        when (role) {
            MonetaryRole.TRANSACTION_AMOUNT ->
                com.baraa.masroof.data.db.PatternCanonicalField.TRANSACTION_AMOUNT
            MonetaryRole.AVAILABLE_BALANCE ->
                com.baraa.masroof.data.db.PatternCanonicalField.AVAILABLE_BALANCE
            MonetaryRole.TOTAL_DUE, MonetaryRole.OUTSTANDING_BALANCE ->
                com.baraa.masroof.data.db.PatternCanonicalField.CARD_AMOUNT_DUE
            MonetaryRole.CREDIT_LIMIT ->
                com.baraa.masroof.data.db.PatternCanonicalField.CARD_AMOUNT_DUE
            else -> null
        }
}

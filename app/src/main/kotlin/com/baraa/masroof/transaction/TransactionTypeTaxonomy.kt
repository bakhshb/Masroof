package com.baraa.masroof.transaction

/**
 * Single source of truth for transaction-type taxonomy used by templates,
 * discovery, import, and Manual Review.
 *
 * Room migration 26→27 canonicalizes old stored names before this model reads
 * them, so no competing legacy taxonomy is retained here.
 */
enum class TransactionTypeFamily {
    PURCHASES,
    TRANSFERS,
    INCOME,
    PAYMENTS,
    OTHER,
    NON_FINANCIAL,
}

data class TransactionTypeChoice(
    val type: TransactionType,
    val labelAr: String,
    val hintAr: String,
    val direction: MoneyFlowDirection,
    val treatment: FinancialTreatment,
) {
    /** Stable chooser id — TransactionType name (shared by templates and review). */
    val id: String get() = type.name
    val label: String get() = labelAr
    val hint: String get() = hintAr
}

object TransactionTypeTaxonomy {

    /** Canonical types shown in template editor and Manual Review choosers. */
    val choosableTypes: List<TransactionType> = listOf(
        TransactionType.PURCHASE,
        TransactionType.ONLINE_PURCHASE,
        TransactionType.TRANSFER_OUT,
        TransactionType.TRANSFER_IN,
        TransactionType.INTERNAL_TRANSFER,
        TransactionType.SALARY,
        TransactionType.REFUND,
        TransactionType.CASH_WITHDRAWAL,
        TransactionType.BILL_PAYMENT,
        TransactionType.CARD_PAYMENT,
        TransactionType.FEE,
        TransactionType.OTHER_FINANCIAL,
        TransactionType.NON_FINANCIAL,
    )

    fun parse(stored: String?): TransactionType? {
        if (stored.isNullOrBlank()) return null
        return runCatching { TransactionType.valueOf(stored.trim()) }.getOrNull()
    }

    fun directionOf(type: TransactionType): MoneyFlowDirection = when (type) {
        TransactionType.PURCHASE,
        TransactionType.ONLINE_PURCHASE,
        TransactionType.TRANSFER_OUT,
        TransactionType.CASH_WITHDRAWAL,
        TransactionType.BILL_PAYMENT,
        TransactionType.CARD_PAYMENT,
        TransactionType.FEE,
        -> MoneyFlowDirection.OUTFLOW

        TransactionType.TRANSFER_IN,
        TransactionType.SALARY,
        TransactionType.REFUND,
        -> MoneyFlowDirection.INFLOW

        TransactionType.INTERNAL_TRANSFER -> MoneyFlowDirection.TRANSFER
        TransactionType.NON_FINANCIAL,
        TransactionType.OTHER_FINANCIAL,
        -> MoneyFlowDirection.NONE
    }

    fun familyOf(type: TransactionType): TransactionTypeFamily = when (type) {
        TransactionType.PURCHASE, TransactionType.ONLINE_PURCHASE -> TransactionTypeFamily.PURCHASES
        TransactionType.TRANSFER_OUT,
        TransactionType.TRANSFER_IN,
        TransactionType.INTERNAL_TRANSFER,
        -> TransactionTypeFamily.TRANSFERS
        TransactionType.SALARY -> TransactionTypeFamily.INCOME
        TransactionType.BILL_PAYMENT, TransactionType.CARD_PAYMENT -> TransactionTypeFamily.PAYMENTS
        TransactionType.NON_FINANCIAL -> TransactionTypeFamily.NON_FINANCIAL
        else -> TransactionTypeFamily.OTHER
    }

    fun familyLabelAr(family: TransactionTypeFamily): String = when (family) {
        TransactionTypeFamily.PURCHASES -> "المشتريات"
        TransactionTypeFamily.TRANSFERS -> "التحويلات"
        TransactionTypeFamily.INCOME -> "الدخل"
        TransactionTypeFamily.PAYMENTS -> "المدفوعات"
        TransactionTypeFamily.OTHER -> "أخرى"
        TransactionTypeFamily.NON_FINANCIAL -> "غير مالية"
    }

    /** Stable family order for UI grouping. */
    val familyDisplayOrder: List<TransactionTypeFamily> = listOf(
        TransactionTypeFamily.PURCHASES,
        TransactionTypeFamily.TRANSFERS,
        TransactionTypeFamily.INCOME,
        TransactionTypeFamily.PAYMENTS,
        TransactionTypeFamily.OTHER,
        TransactionTypeFamily.NON_FINANCIAL,
    )

    fun labelAr(type: TransactionType): String = when (type) {
        TransactionType.PURCHASE -> "شراء عبر نقاط البيع"
        TransactionType.ONLINE_PURCHASE -> "شراء عبر الإنترنت"
        TransactionType.TRANSFER_OUT -> "تحويل صادر"
        TransactionType.TRANSFER_IN -> "تحويل وارد"
        TransactionType.INTERNAL_TRANSFER -> "تحويل داخلي"
        TransactionType.SALARY -> "راتب"
        TransactionType.REFUND -> "استرداد"
        TransactionType.CASH_WITHDRAWAL -> "سحب نقدي"
        TransactionType.BILL_PAYMENT -> "سداد فاتورة"
        TransactionType.CARD_PAYMENT -> "سداد بطاقة"
        TransactionType.FEE -> "رسوم بنكية"
        TransactionType.OTHER_FINANCIAL -> "عملية مالية أخرى"
        TransactionType.NON_FINANCIAL -> "رسالة غير مالية"
    }

    fun directionLabelAr(direction: MoneyFlowDirection): String = when (direction) {
        MoneyFlowDirection.INFLOW -> "إضافة"
        MoneyFlowDirection.OUTFLOW -> "خصم"
        MoneyFlowDirection.TRANSFER -> "تحويل"
        MoneyFlowDirection.NONE -> "لا ينطبق"
    }

    fun directionStorageName(direction: MoneyFlowDirection): String = direction.name

    /** Read the canonical persisted direction. */
    fun parseDirection(stored: String?, type: TransactionType? = null): MoneyFlowDirection {
        when (stored?.trim()?.uppercase()) {
            "INFLOW" -> return MoneyFlowDirection.INFLOW
            "OUTFLOW" -> return MoneyFlowDirection.OUTFLOW
            "TRANSFER" -> return MoneyFlowDirection.TRANSFER
            "NONE" -> return MoneyFlowDirection.NONE
        }
        return type?.let { directionOf(it) } ?: MoneyFlowDirection.NONE
    }

    fun defaultTreatment(type: TransactionType): FinancialTreatment = when (type) {
        TransactionType.PURCHASE, TransactionType.ONLINE_PURCHASE -> FinancialTreatment.EXPENSE
        TransactionType.TRANSFER_OUT -> FinancialTreatment.EXPENSE
        TransactionType.TRANSFER_IN, TransactionType.SALARY -> FinancialTreatment.INCOME
        TransactionType.INTERNAL_TRANSFER -> FinancialTreatment.INTERNAL_TRANSFER
        TransactionType.CARD_PAYMENT -> FinancialTreatment.CREDIT_CARD_PAYMENT
        TransactionType.CASH_WITHDRAWAL -> FinancialTreatment.CASH_WITHDRAWAL
        TransactionType.FEE -> FinancialTreatment.BANK_FEE
        TransactionType.REFUND -> FinancialTreatment.REFUND
        TransactionType.BILL_PAYMENT -> FinancialTreatment.EXPENSE
        TransactionType.OTHER_FINANCIAL -> FinancialTreatment.PENDING_REVIEW
        TransactionType.NON_FINANCIAL -> FinancialTreatment.IGNORED
    }

    /** Financial transaction types that must have a transaction amount before approval. */
    fun requiresAmount(type: TransactionType): Boolean = when (type) {
        TransactionType.NON_FINANCIAL -> false
        else -> true
    }

    fun isFinancial(type: TransactionType): Boolean =
        type != TransactionType.NON_FINANCIAL

    val reviewChoices: List<TransactionTypeChoice> = choosableTypes.map { type ->
        TransactionTypeChoice(
            type = type,
            labelAr = labelAr(type),
            hintAr = hintAr(type),
            direction = directionOf(type),
            treatment = defaultTreatment(type),
        )
    }

    fun hintAr(type: TransactionType): String = when (type) {
        TransactionType.PURCHASE -> "شراء أو خدمة عبر نقاط البيع — يُنقص الرصيد وصافي الثروة."
        TransactionType.ONLINE_PURCHASE -> "شراء عبر الإنترنت — يُنقص الرصيد وصافي الثروة."
        TransactionType.TRANSFER_OUT -> "تحويل لشخص أو جهة خارج حساباتك — يُنقص الرصيد وصافي الثروة."
        TransactionType.TRANSFER_IN -> "تحويل من شخص أو جهة خارج حساباتك — يزيد الرصيد وصافي الثروة."
        TransactionType.INTERNAL_TRANSFER -> "من حساب لك إلى حساب آخر لك — لا يغيّر صافي الثروة."
        TransactionType.SALARY -> "راتب — يزيد الرصيد وصافي الثروة."
        TransactionType.REFUND -> "استرداد يزيد الرصيد."
        TransactionType.CASH_WITHDRAWAL -> "سحب نقدي من الصراف — يُنقص رصيد الحساب."
        TransactionType.BILL_PAYMENT -> "سداد فاتورة أو قسط — يُنقص الرصيد."
        TransactionType.CARD_PAYMENT -> "سداد بطاقة ائتمانية — لا يُحسب مصروفًا جديدًا."
        TransactionType.FEE -> "رسوم بنكية تُنقص الرصيد والثروة."
        TransactionType.OTHER_FINANCIAL -> "عملية مالية تحتاج مراجعة."
        TransactionType.NON_FINANCIAL -> "رسالة معلوماتية (OTP، إعدادات، إعلان) — ليست عملية مالية."
        else -> ""
    }

    fun choiceFor(type: TransactionType): TransactionTypeChoice {
        return reviewChoices.firstOrNull { it.type == type }
            ?: TransactionTypeChoice(
                type = type,
                labelAr = labelAr(type),
                hintAr = hintAr(type),
                direction = directionOf(type),
                treatment = defaultTreatment(type),
            )
    }

    /** Confidence score for a discovered pattern from occurrence count (0–100). */
    fun discoveryConfidence(exampleCount: Int): Int = when {
        exampleCount <= 0 -> 0
        exampleCount == 1 -> 35
        exampleCount == 2 -> 55
        exampleCount in 3..4 -> 70
        else -> 85
    }
}

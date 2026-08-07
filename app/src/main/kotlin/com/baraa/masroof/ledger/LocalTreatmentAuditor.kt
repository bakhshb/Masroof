package com.baraa.masroof.ledger

import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.transaction.FinancialTreatment
import com.baraa.masroof.transaction.TransactionType
import java.util.Locale

/**
 * On-device, deterministic treatment classifier for bank SMS.
 *
 * Uses explicit Arabic/English type phrases already present in messages —
 * not a neural model and never leaves the device. High-confidence results
 * can auto-apply; ambiguous internal transfers stay for review.
 */
object LocalTreatmentAuditor {
    data class Result(
        val treatment: FinancialTreatment,
        val confidence: Int,
        val reasonAr: String,
        /** True when import/relink may apply without asking the user. */
        val autoApply: Boolean,
    )

    private val INTERNAL_BODY_CUES = listOf(
        "حوالة واردة داخلية",
        "حوالة صادرة داخلية",
        "حوالة داخلية",
        "تحويل داخلي",
        "تحويل بين حساباتي",
        "بين حساباتك",
        "internal transfer",
    )

    private val WALLET_TOPUP_BODY_CUES = listOf(
        "شحن المحفظة",
        "شحن محفظة",
        "شحن رصيد",
        "إضافة رصيد للمحفظة",
        "تمويل المحفظة",
        "شحن الحساب بالبطاقة",
        "wallet top-up",
        "wallet top up",
        "top-up wallet",
        "top up wallet",
        "card funding",
    )

    private val SALARY_BODY_CUES = listOf(
        "إيداع راتب",
        "ايداع راتب",
        "راتب شهر",
        "تحويل راتب",
        "salary credit",
        "salary deposit",
        "wages",
    )

    private val FEE_BODY_CUES = listOf(
        "رسوم خدمة",
        "رسوم بنكية",
        "رسوم إدارية",
        "عمولة تحويل",
        "عمولة بنكية",
        "service charge",
        "bank fee",
        "transaction fee",
    )

    private val CASH_WITHDRAWAL_BODY_CUES = listOf(
        "سحب نقدي",
        "سحب من الصراف",
        "سحب آلي",
        "atm withdrawal",
        "cash withdrawal",
    )

    private val CARD_PAYMENT_BODY_CUES = listOf(
        "سداد بطاقة ائتمانية",
        "سداد البطاقة",
        "دفع بطاقة ائتمان",
        "credit card payment",
        "card payment",
    )

    private val REFUND_BODY_CUES = listOf(
        "استرداد",
        "مرتجع",
        "عملية مستردة",
        "refund",
        "reversed",
    )

    private val CREDIT_LIMIT_BODY_CUES = listOf(
        "تغيير حد الرصيد",
        "تم تغيير الحد الائتماني",
        "تغيير الحد الائتماني",
        "الحد الائتماني الجديد",
        "حد ائتماني جديد",
        "تحديث الحد الائتماني",
        "credit limit change",
        "new credit limit",
        "credit limit has been changed",
    )

    /**
     * Audit from a stored transaction row (type + optional notes that may
     * retain body cues). Prefer [audit] with the raw SMS body when available.
     */
    fun auditTransaction(
        tx: TransactionEntity,
        smsBody: String? = null,
        hasConfirmedTwoOwnedSides: Boolean = false,
    ): Result = audit(
        type = tx.transactionType,
        body = smsBody ?: tx.parsingNotes.joinToString(" "),
        currentTreatment = tx.financialTreatment,
        hasConfirmedTwoOwnedSides = hasConfirmedTwoOwnedSides,
    )

    fun audit(
        type: TransactionType,
        body: String? = null,
        currentTreatment: FinancialTreatment = FinancialTreatment.PENDING_REVIEW,
        hasConfirmedTwoOwnedSides: Boolean = false,
    ): Result {
        if (currentTreatment != FinancialTreatment.PENDING_REVIEW &&
            currentTreatment != FinancialTreatment.IGNORED
        ) {
            return Result(
                treatment = currentTreatment,
                confidence = 100,
                reasonAr = "تصنيف محفوظ مسبقًا",
                autoApply = true,
            )
        }

        val normalized = body.orEmpty().lowercase(Locale.ROOT)
        if (CREDIT_LIMIT_BODY_CUES.any { it.lowercase(Locale.ROOT) in normalized } ||
            type == TransactionType.CREDIT_LIMIT_CHANGE
        ) {
            return Result(
                treatment = FinancialTreatment.IGNORED,
                confidence = 98,
                reasonAr = "تغيير حد ائتماني — لا يُحسب مصروفًا ولا يغيّر الرصيد",
                autoApply = true,
            )
        }

        if (WALLET_TOPUP_BODY_CUES.any { it.lowercase(Locale.ROOT) in normalized }) {
            return Result(
                treatment = FinancialTreatment.INTERNAL_TRANSFER,
                confidence = 82,
                reasonAr = "شحن محفظة في الرسالة — حدد حساب البطاقة والمحفظة",
                autoApply = hasConfirmedTwoOwnedSides,
            )
        }

        val internalCue = INTERNAL_BODY_CUES.any { it.lowercase(Locale.ROOT) in normalized } ||
            type == TransactionType.INTERNAL_TRANSFER

        if (internalCue) {
            return if (hasConfirmedTwoOwnedSides) {
                Result(
                    treatment = FinancialTreatment.INTERNAL_TRANSFER,
                    confidence = 90,
                    reasonAr = "حوالة داخلية بين حسابين مملوكين",
                    autoApply = true,
                )
            } else {
                Result(
                    treatment = FinancialTreatment.INTERNAL_TRANSFER,
                    confidence = 75,
                    reasonAr = "عبارة تحويل داخلي في الرسالة — يلزم تحديد الحسابين",
                    autoApply = false,
                )
            }
        }

        if (FEE_BODY_CUES.any { it.lowercase(Locale.ROOT) in normalized } ||
            type == TransactionType.BANK_FEE
        ) {
            return Result(
                treatment = FinancialTreatment.BANK_FEE,
                confidence = 88,
                reasonAr = "رسوم بنكية في الرسالة",
                autoApply = true,
            )
        }

        if (SALARY_BODY_CUES.any { it.lowercase(Locale.ROOT) in normalized } ||
            type == TransactionType.SALARY
        ) {
            return Result(
                treatment = FinancialTreatment.INCOME,
                confidence = 90,
                reasonAr = "راتب أو إيداع راتب في الرسالة",
                autoApply = true,
            )
        }

        if (CASH_WITHDRAWAL_BODY_CUES.any { it.lowercase(Locale.ROOT) in normalized } ||
            type == TransactionType.CASH_WITHDRAWAL
        ) {
            return Result(
                treatment = FinancialTreatment.CASH_WITHDRAWAL,
                confidence = 88,
                reasonAr = "سحب نقدي في الرسالة",
                autoApply = true,
            )
        }

        if (CARD_PAYMENT_BODY_CUES.any { it.lowercase(Locale.ROOT) in normalized } ||
            type == TransactionType.CARD_PAYMENT
        ) {
            return Result(
                treatment = FinancialTreatment.CREDIT_CARD_PAYMENT,
                confidence = 85,
                reasonAr = "سداد بطاقة في الرسالة",
                autoApply = false,
            )
        }

        if (REFUND_BODY_CUES.any { it.lowercase(Locale.ROOT) in normalized } ||
            type == TransactionType.REFUND
        ) {
            return Result(
                treatment = FinancialTreatment.REFUND,
                confidence = 82,
                reasonAr = "استرداد في الرسالة",
                autoApply = true,
            )
        }

        return when (type) {
            TransactionType.PURCHASE, TransactionType.ONLINE_PURCHASE -> Result(
                treatment = FinancialTreatment.EXPENSE,
                confidence = 85,
                reasonAr = "عبارة شراء في الرسالة",
                autoApply = true,
            )
            TransactionType.LOAN_INSTALLMENT -> Result(
                treatment = FinancialTreatment.EXPENSE,
                confidence = 90,
                reasonAr = "خصم قسط تمويل من الحساب",
                autoApply = true,
            )
            TransactionType.BILL_PAYMENT -> Result(
                treatment = FinancialTreatment.EXPENSE,
                confidence = 90,
                reasonAr = "سداد فاتورة",
                autoApply = true,
            )
            TransactionType.TRANSFER_OUT -> Result(
                treatment = FinancialTreatment.EXPENSE,
                confidence = 80,
                reasonAr = "حوالة صادرة/خارجية — تُحسب كخروج مال ما لم يثبت أنها بين حساباتك",
                autoApply = true,
            )
            TransactionType.TRANSFER_IN -> Result(
                treatment = FinancialTreatment.INCOME,
                confidence = 80,
                reasonAr = "حوالة واردة خارجية — تُحسب كدخل ما لم يثبت أنها داخلية",
                autoApply = true,
            )
            TransactionType.DEPOSIT, TransactionType.SALARY -> Result(
                treatment = FinancialTreatment.INCOME,
                confidence = 85,
                reasonAr = "إيداع أو راتب",
                autoApply = true,
            )
            TransactionType.CASH_WITHDRAWAL -> Result(
                treatment = FinancialTreatment.CASH_WITHDRAWAL,
                confidence = 85,
                reasonAr = "سحب نقدي",
                autoApply = true,
            )
            TransactionType.CARD_PAYMENT -> Result(
                treatment = FinancialTreatment.CREDIT_CARD_PAYMENT,
                confidence = 85,
                reasonAr = "سداد بطاقة في الرسالة",
                autoApply = false, // needs bank + card accounts
            )
            TransactionType.BANK_FEE -> Result(
                treatment = FinancialTreatment.BANK_FEE,
                confidence = 85,
                reasonAr = "رسوم بنكية",
                autoApply = true,
            )
            TransactionType.REFUND -> Result(
                treatment = FinancialTreatment.REFUND,
                confidence = 80,
                reasonAr = "استرداد",
                autoApply = true,
            )
            TransactionType.INVESTMENT_TRANSFER -> Result(
                treatment = FinancialTreatment.INVESTMENT,
                confidence = 80,
                reasonAr = "تحويل استثماري",
                autoApply = false,
            )
            TransactionType.INTERNAL_TRANSFER -> Result(
                treatment = FinancialTreatment.INTERNAL_TRANSFER,
                confidence = 75,
                reasonAr = "تحويل داخلي — يلزم حسابان",
                autoApply = hasConfirmedTwoOwnedSides,
            )
            TransactionType.DECLINED -> Result(
                treatment = FinancialTreatment.IGNORED,
                confidence = 95,
                reasonAr = "عملية مرفوضة",
                autoApply = true,
            )
            TransactionType.CREDIT_LIMIT_CHANGE -> Result(
                treatment = FinancialTreatment.IGNORED,
                confidence = 98,
                reasonAr = "تغيير حد ائتماني — لا يُحسب مصروفًا ولا يغيّر الرصيد",
                autoApply = true,
            )
            TransactionType.UNKNOWN -> Result(
                treatment = FinancialTreatment.PENDING_REVIEW,
                confidence = 20,
                reasonAr = "نوع العملية غير واضح من الرسالة",
                autoApply = false,
            )
        }
    }

    /** Treatment only — used by fallback rules and review suggestions. */
    fun treatmentFor(
        type: TransactionType,
        body: String? = null,
    ): FinancialTreatment = audit(type = type, body = body).treatment
}

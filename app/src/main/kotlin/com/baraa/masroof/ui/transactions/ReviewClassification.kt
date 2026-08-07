package com.baraa.masroof.ui.transactions

import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.ledger.LocalTreatmentAuditor
import com.baraa.masroof.transaction.FinancialTreatment
import com.baraa.masroof.transaction.TransactionType
import com.baraa.masroof.ui.TransactionPresentationFactory

/**
 * One chip in the review dialog. Several choices may map to the same
 * [FinancialTreatment] (e.g. purchase vs external outgoing transfer) so the
 * ledger stays simple while the Arabic labels stay clear.
 */
data class ReviewTreatmentChoice(
    val id: String,
    val label: String,
    val hint: String,
    val treatment: FinancialTreatment,
)

/**
 * Helpers for the review queue: Arabic labels and a suggested treatment
 * so the user can classify transfers / payments before posting.
 */
object ReviewClassification {
    val choosableChoices: List<ReviewTreatmentChoice> = listOf(
        ReviewTreatmentChoice(
            id = "expense_purchase",
            label = "مصروف / شراء",
            hint = "شراء أو خدمة — يُنقص رصيد الحساب وصافي الثروة.",
            treatment = FinancialTreatment.EXPENSE,
        ),
        ReviewTreatmentChoice(
            id = "transfer_out_external",
            label = "حوالة صادرة خارجية",
            hint = "تحويل لشخص أو جهة خارج حساباتك — يُنقص الرصيد وصافي الثروة.",
            treatment = FinancialTreatment.EXPENSE,
        ),
        ReviewTreatmentChoice(
            id = "income_other",
            label = "دخل / راتب",
            hint = "راتب أو دخل آخر — يزيد الرصيد وصافي الثروة.",
            treatment = FinancialTreatment.INCOME,
        ),
        ReviewTreatmentChoice(
            id = "transfer_in_external",
            label = "حوالة واردة خارجية",
            hint = "تحويل من شخص أو جهة خارج حساباتك — يزيد الرصيد وصافي الثروة.",
            treatment = FinancialTreatment.INCOME,
        ),
        ReviewTreatmentChoice(
            id = "internal_transfer",
            label = "تحويل داخلي بين حساباتي",
            hint = "من حساب لك إلى حساب آخر لك (واردة أو صادرة داخلية) — لا يغيّر صافي الثروة. حدّد حساب الخصم وحساب الإضافة.",
            treatment = FinancialTreatment.INTERNAL_TRANSFER,
        ),
        ReviewTreatmentChoice(
            id = "credit_card_payment",
            label = "سداد بطاقة ائتمانية",
            hint = "من حساب بنكي إلى بطاقة — لا يُحسب مصروفًا جديدًا.",
            treatment = FinancialTreatment.CREDIT_CARD_PAYMENT,
        ),
        ReviewTreatmentChoice(
            id = "cash_withdrawal",
            label = "سحب نقدي",
            hint = "من حساب الراتب أو البنك عبر الصراف — يُنقص رصيد ذلك الحساب مباشرة. لا تحتاج حساب نقد منفصل.",
            treatment = FinancialTreatment.CASH_WITHDRAWAL,
        ),
        ReviewTreatmentChoice(
            id = "bank_fee",
            label = "رسوم بنكية",
            hint = "رسوم تُنقص الرصيد والثروة.",
            treatment = FinancialTreatment.BANK_FEE,
        ),
        ReviewTreatmentChoice(
            id = "refund",
            label = "استرداد",
            hint = "استرداد يزيد الرصيد.",
            treatment = FinancialTreatment.REFUND,
        ),
    )

    /** Treatments still exposed for filters / legacy callers. */
    val choosableTreatments: List<FinancialTreatment> =
        choosableChoices.map { it.treatment }.distinct()

    fun friendlyType(tx: TransactionEntity): String =
        TransactionPresentationFactory.friendlyTransactionType(tx.transactionType)

    fun treatmentLabel(treatment: FinancialTreatment): String = when (treatment) {
        FinancialTreatment.EXPENSE -> "مصروف / حوالة صادرة خارجية"
        FinancialTreatment.INCOME -> "دخل / حوالة واردة خارجية"
        FinancialTreatment.INTERNAL_TRANSFER -> "تحويل داخلي بين حساباتي"
        FinancialTreatment.CREDIT_CARD_PAYMENT -> "سداد بطاقة ائتمانية"
        FinancialTreatment.CASH_WITHDRAWAL -> "سحب نقدي"
        FinancialTreatment.BANK_FEE -> "رسوم بنكية"
        FinancialTreatment.REFUND -> "استرداد"
        FinancialTreatment.INVESTMENT -> "تحويل استثماري"
        FinancialTreatment.PENDING_REVIEW -> "غير مصنّف — اختر النوع"
        FinancialTreatment.IGNORED -> "مهمل"
    }

    fun treatmentHint(treatment: FinancialTreatment): String =
        choosableChoices.firstOrNull { it.treatment == treatment }?.hint
            ?: when (treatment) {
                FinancialTreatment.INVESTMENT -> "تحويل إلى حساب استثماري."
                else -> ""
            }

    /**
     * Prefer an already-resolved treatment; otherwise use the on-device
     * [LocalTreatmentAuditor] from parser type / body cues.
     */
    fun suggestedTreatment(tx: TransactionEntity): FinancialTreatment =
        LocalTreatmentAuditor.auditTransaction(tx).treatment

    /** Chip to pre-select in the review dialog from parser type + treatment. */
    fun suggestedChoice(tx: TransactionEntity): ReviewTreatmentChoice {
        val treatment = suggestedTreatment(tx)
        val byType = when (tx.transactionType) {
            TransactionType.TRANSFER_OUT -> choice("transfer_out_external")
            TransactionType.TRANSFER_IN -> choice("transfer_in_external")
            TransactionType.INTERNAL_TRANSFER -> choice("internal_transfer")
            TransactionType.LOAN_INSTALLMENT, TransactionType.BILL_PAYMENT -> choice("expense_purchase")
            TransactionType.SALARY, TransactionType.DEPOSIT -> choice("income_other")
            TransactionType.PURCHASE, TransactionType.ONLINE_PURCHASE -> choice("expense_purchase")
            TransactionType.CARD_PAYMENT -> choice("credit_card_payment")
            TransactionType.CASH_WITHDRAWAL -> choice("cash_withdrawal")
            TransactionType.BANK_FEE -> choice("bank_fee")
            TransactionType.REFUND -> choice("refund")
            else -> null
        }
        if (byType != null && byType.treatment == treatment) return byType
        return choosableChoices.firstOrNull { it.treatment == treatment }
            ?: choice("expense_purchase")
    }

    fun choice(id: String): ReviewTreatmentChoice =
        choosableChoices.first { it.id == id }

    fun isSourceSide(treatment: FinancialTreatment): Boolean = treatment in setOf(
        FinancialTreatment.EXPENSE,
        FinancialTreatment.BANK_FEE,
        FinancialTreatment.CASH_WITHDRAWAL,
        FinancialTreatment.INTERNAL_TRANSFER,
        FinancialTreatment.CREDIT_CARD_PAYMENT,
        FinancialTreatment.INVESTMENT,
    )
}

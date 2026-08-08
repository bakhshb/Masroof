package com.baraa.masroof.ui.transactions

import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.ledger.LocalTreatmentAuditor
import com.baraa.masroof.transaction.FinancialTreatment
import com.baraa.masroof.transaction.MoneyFlowDirection
import com.baraa.masroof.transaction.TransactionType
import com.baraa.masroof.transaction.TransactionTypeChoice
import com.baraa.masroof.transaction.TransactionTypeTaxonomy
import com.baraa.masroof.ui.TransactionPresentationFactory
import com.baraa.masroof.ui.TransactionTypeVisuals

/**
 * Manual Review classification uses the same [TransactionType] taxonomy as
 * templates. [FinancialTreatment] is derived for ledger posting only.
 */
typealias ReviewTreatmentChoice = TransactionTypeChoice

/**
 * Helpers for the review queue: Arabic labels and a suggested type
 * so the user can classify transfers / payments before posting.
 */
object ReviewClassification {
    /** Same ordered list as template editor / [TransactionTypeTaxonomy.reviewChoices]. */
    val choosableChoices: List<TransactionTypeChoice> = TransactionTypeTaxonomy.reviewChoices

    /** Derived treatments exposed for review filters. */
    val choosableTreatments: List<FinancialTreatment> =
        choosableChoices.map { it.treatment }.distinct()

    fun friendlyType(tx: TransactionEntity): String =
        TransactionPresentationFactory.friendlyTransactionType(tx.transactionType)

    fun treatmentLabel(treatment: FinancialTreatment): String = when (treatment) {
        FinancialTreatment.EXPENSE -> "مصروف"
        FinancialTreatment.INCOME -> "دخل"
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
        choosableChoices.firstOrNull { it.treatment == treatment }?.hintAr
            ?: when (treatment) {
                FinancialTreatment.INVESTMENT -> "تحويل إلى حساب استثماري."
                else -> ""
            }

    fun directionLabel(direction: MoneyFlowDirection): String =
        "اتجاه الأموال: ${TransactionTypeTaxonomy.directionLabelAr(direction)}"

    fun typeLabel(type: TransactionType): String = TransactionTypeVisuals.label(type)

    /**
     * Prefer an already-resolved treatment; otherwise use the on-device
     * [LocalTreatmentAuditor] from parser type / body cues.
     */
    fun suggestedTreatment(tx: TransactionEntity): FinancialTreatment =
        LocalTreatmentAuditor.auditTransaction(tx).treatment

    /** Chip to pre-select in the review dialog from parser type. */
    fun suggestedChoice(tx: TransactionEntity): TransactionTypeChoice {
        val normalized = tx.transactionType
        val byType = choosableChoices.firstOrNull { it.type == normalized }
        if (byType != null) return byType
        val treatment = suggestedTreatment(tx)
        return choosableChoices.firstOrNull { it.treatment == treatment }
            ?: TransactionTypeTaxonomy.choiceFor(TransactionType.PURCHASE)
    }

    fun choice(type: TransactionType): TransactionTypeChoice =
        TransactionTypeTaxonomy.choiceFor(type)

    fun isSourceSide(treatment: FinancialTreatment): Boolean = treatment in setOf(
        FinancialTreatment.EXPENSE,
        FinancialTreatment.BANK_FEE,
        FinancialTreatment.CASH_WITHDRAWAL,
        FinancialTreatment.INTERNAL_TRANSFER,
        FinancialTreatment.CREDIT_CARD_PAYMENT,
        FinancialTreatment.INVESTMENT,
    )
}

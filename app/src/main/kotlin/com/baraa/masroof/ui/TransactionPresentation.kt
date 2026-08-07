package com.baraa.masroof.ui

import com.baraa.masroof.ledger.InstitutionIdentificationSource
import com.baraa.masroof.ledger.InstitutionResolution
import com.baraa.masroof.transaction.AccountType
import com.baraa.masroof.transaction.FinancialTreatment
import com.baraa.masroof.transaction.TransactionType
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

/**
 * Decoupled transaction-row state used by the redesigned UI.
 *
 * The presentation model intentionally omits:
 *  - the raw SMS body
 *  - parser internals
 *  - journal or posting terminology
 *
 * Technical details stay hidden behind the "إظهار التفاصيل الفنية" toggle
 * via the optional `technicalDetails` field.
 */
data class TransactionPresentation(
    val transactionId: Long,
    val amount: BigDecimal,
    val amountLabel: String,
    val isExpense: Boolean?,
    val merchantOrLabel: String,
    val friendlyType: String,
    val institutionDisplayName: String,
    val institutionSource: InstitutionIdentificationSource,
    val accountOrInstrumentLabel: String,
    val channelLabel: String?,
    val currency: String,
    val dateLabel: String,
    val requiresReview: Boolean,
    val needsAttention: Boolean,
    val exclusionReason: String?,
    val isBeforeTrackingStart: Boolean,
    val technicalDetails: TechnicalDetails? = null,
) {
    data class TechnicalDetails(
        val parserName: String,
        val confidence: Int,
        val lastFour: String?,
        val identifierType: String,
    )
}

object TransactionPresentationFactory {
    fun create(
        transactionId: Long,
        amount: BigDecimal?,
        type: TransactionType,
        treatment: FinancialTreatment,
        currency: String,
        merchantOrBeneficiary: String?,
        accountOrCardLastFourDigits: String?,
        accountType: AccountType?,
        transactionDate: LocalDate?,
        transactionTime: LocalTime?,
        requiresReview: Boolean,
        exclusionReason: String?,
        isBeforeTrackingStart: Boolean,
        needsAttention: Boolean,
        institution: InstitutionResolution,
        showTechnical: Boolean,
        parserName: String,
        confidence: Int,
    ): TransactionPresentation {
        val amountLabel = amount?.let { "${it.toPlainString()} $currency" } ?: "—"
        val friendlyType = friendlyTransactionType(type)
        val isExpense = when (treatment) {
            FinancialTreatment.EXPENSE, FinancialTreatment.BANK_FEE -> true
            FinancialTreatment.INCOME, FinancialTreatment.REFUND -> false
            FinancialTreatment.CREDIT_CARD_PAYMENT, FinancialTreatment.INTERNAL_TRANSFER, FinancialTreatment.INVESTMENT -> null
            else -> null
        }
        return TransactionPresentation(
            transactionId = transactionId,
            amount = amount ?: BigDecimal.ZERO,
            amountLabel = amountLabel,
            isExpense = isExpense,
            merchantOrLabel = merchantOrBeneficiary?.takeIf { it.isNotBlank() } ?: friendlyType,
            friendlyType = friendlyType,
            institutionDisplayName = institution.institutionDisplayName,
            institutionSource = institution.source,
            accountOrInstrumentLabel = accountOrInstrumentLabel(accountType, accountOrCardLastFourDigits),
            channelLabel = channelLabel(type, transactionDate, transactionTime),
            currency = currency,
            dateLabel = formatDateTime(transactionDate, transactionTime),
            requiresReview = requiresReview,
            needsAttention = needsAttention,
            exclusionReason = exclusionReason,
            isBeforeTrackingStart = isBeforeTrackingStart,
            technicalDetails = if (showTechnical) {
                TransactionPresentation.TechnicalDetails(
                    parserName = parserName,
                    confidence = confidence,
                    lastFour = accountOrCardLastFourDigits?.takeLast(4),
                    identifierType = "ACCOUNT_LAST_FOUR",
                )
            } else null,
        )
    }

    fun friendlyTransactionType(type: TransactionType): String = when (type) {
        TransactionType.PURCHASE -> "شراء"
        TransactionType.ONLINE_PURCHASE -> "شراء عبر الإنترنت"
        TransactionType.CASH_WITHDRAWAL -> "سحب نقدي"
        TransactionType.TRANSFER_OUT -> "حوالة صادرة"
        TransactionType.TRANSFER_IN -> "حوالة واردة"
        TransactionType.CARD_PAYMENT -> "سداد بطاقة"
        TransactionType.REFUND -> "استرداد"
        TransactionType.SALARY -> "راتب"
        TransactionType.DEPOSIT -> "إيداع"
        TransactionType.BANK_FEE -> "رسوم بنكية"
        TransactionType.INTERNAL_TRANSFER -> "تحويل داخلي"
        TransactionType.INVESTMENT_TRANSFER -> "تحويل استثماري"
        TransactionType.LOAN_INSTALLMENT -> "قسط تمويل"
        TransactionType.BILL_PAYMENT -> "سداد فاتورة"
        TransactionType.DECLINED -> "عملية مرفوضة"
        TransactionType.CREDIT_LIMIT_CHANGE -> "تغيير حد الرصيد"
        TransactionType.UNKNOWN -> "عملية غير مصنفة"
    }

    private fun accountOrInstrumentLabel(accountType: AccountType?, lastFour: String?): String {
        val safeLastFour = lastFour?.takeLast(4)?.takeIf { it.isNotBlank() } ?: return "غير مرتبط بحساب"
        return when (accountType) {
            AccountType.BANK_ACCOUNT -> "حساب ••••$safeLastFour"
            AccountType.CREDIT_CARD -> "بطاقة ائتمانية ••••$safeLastFour"
            AccountType.DIGITAL_WALLET, AccountType.WALLET -> "بطاقة مدى ••••$safeLastFour"
            AccountType.DIGITAL_WALLET -> "محفظة رقمية ••••$safeLastFour"
            AccountType.CASH -> "نقد"
            AccountType.INVESTMENT_ACCOUNT, AccountType.SUKUK_ACCOUNT -> "استثمار"
            AccountType.LOAN, AccountType.OTHER_LIABILITY -> "التزام ••••$safeLastFour"
            AccountType.OTHER_ASSET, AccountType.OTHER -> "حساب ••••$safeLastFour"
            null -> "غير مرتبط بحساب"
        }
    }

    private fun channelLabel(type: TransactionType, date: LocalDate?, time: LocalTime?): String? {
        return when (type) {
            TransactionType.ONLINE_PURCHASE -> "عبر الإنترنت"
            TransactionType.PURCHASE -> "نقاط البيع"
            else -> null
        }
    }

    private fun formatDateTime(date: LocalDate?, time: LocalTime?): String {
        if (date == null) return "—"
        val dateStr = date.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale("ar")))
        return if (time != null) "$dateStr • ${time.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale("ar")))}" else dateStr
    }
}

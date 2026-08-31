package com.baraa.masroof.bank.aljazira

import com.baraa.masroof.domain.model.LoanType

/**
 * Maps Bank AlJazira financing SMS labels (لـ: …) to [LoanType] at parse time.
 */
object AlJaziraLoanTypeMapper {
    fun fromFinancingLabel(label: String?): LoanType? {
        val normalized = label?.trim().orEmpty()
        if (normalized.isEmpty()) return null
        return when {
            normalized.contains("تمويل شخصي") || normalized.contains("شخصي") ->
                LoanType.PERSONAL
            normalized.contains("سيارة") || normalized.contains("مركبة") || normalized.contains("أوتو") ->
                LoanType.AUTO
            normalized.contains("عقار") || normalized.contains("مسكن") || normalized.contains("رهن") ||
                normalized.contains("عقاري") ->
                LoanType.MORTGAGE
            normalized.contains("تمويل") -> LoanType.PERSONAL
            else -> null
        }
    }
}

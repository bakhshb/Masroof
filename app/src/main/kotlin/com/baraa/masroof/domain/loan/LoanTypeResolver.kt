package com.baraa.masroof.domain.loan

import com.baraa.masroof.domain.model.LoanType

/**
 * Maps AlJazira financing SMS labels (لـ: …) to [LoanType].
 */
object LoanTypeResolver {
    fun fromLabel(label: String?): LoanType? {
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

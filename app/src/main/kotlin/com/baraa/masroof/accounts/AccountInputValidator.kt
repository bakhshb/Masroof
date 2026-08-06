package com.baraa.masroof.accounts

import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.transaction.AccountNature
import com.baraa.masroof.transaction.AccountType
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Pure-JVM validation for account input. Used by the setup wizard
 * and the account editor. Returns a list of error keys; the caller
 * (UI) maps each key to an Arabic message.
 *
 * No I/O; no Room; no logging.
 */
object AccountInputValidator {

    enum class ErrorKey {
        BLANK_NAME,
        NEGATIVE_OPENING_BALANCE,
        FUTURE_DATE,
        INVALID_DATE,
    }

    data class ValidationError(val key: ErrorKey, val field: String)

    /**
     * Validate an account before save.
     *
     * @param name The display name (required, non-blank).
     * @param openingBalance The opening balance (must be ≥ 0 — the
     *   caller is responsible for entering liability amounts as
     *   positive numbers; the calculator subtracts them).
     * @param openingBalanceDate The date (must be today or earlier).
     */
    fun validate(
        name: String,
        openingBalance: BigDecimal,
        openingBalanceDate: LocalDate,
        today: LocalDate = LocalDate.now(),
    ): List<ValidationError> {
        val errors = ArrayList<ValidationError>()
        if (name.isBlank()) {
            errors += ValidationError(ErrorKey.BLANK_NAME, "name")
        }
        if (openingBalance.signum() < 0) {
            errors += ValidationError(ErrorKey.NEGATIVE_OPENING_BALANCE, "openingBalance")
        }
        if (openingBalanceDate.isAfter(today)) {
            // We do NOT flag dates equal to today as "future". We only
            // flag dates strictly after today. This is the spec's
            // "opening date is not after today" rule.
            errors += ValidationError(ErrorKey.FUTURE_DATE, "openingBalanceDate")
        }
        return errors
    }
}

/**
 * Lightweight duplicate-detection helper. Warns when the user creates an
 * account with the same (institution, type) as an existing account.
 *
 * Identifier uniqueness is enforced separately via typed
 * [com.baraa.masroof.data.db.AccountIdentifierEntity] rows.
 *
 * Detection is case-insensitive on institution.
 *
 * The duplicate check does NOT block the save — it only yields a
 * boolean the UI can use to show a warning. The user may legitimately
 * want two checking accounts at the same bank (e.g. joint + personal).
 */
object DuplicateAccountDetector {

    fun isDuplicate(
        candidate: AccountToCheck,
        existing: List<FinancialAccount>,
    ): Boolean = existing.any { match(it, candidate) }

    data class AccountToCheck(
        val institutionName: String?,
        val accountType: AccountType,
        val accountNature: AccountNature,
    )

    private fun match(existing: FinancialAccount, candidate: AccountToCheck): Boolean {
        val sameType = existing.accountType == candidate.accountType
        val sameInstitution = existing.institutionName?.trim()?.lowercase() ==
            candidate.institutionName?.trim()?.lowercase()
        return sameType && sameInstitution
    }
}

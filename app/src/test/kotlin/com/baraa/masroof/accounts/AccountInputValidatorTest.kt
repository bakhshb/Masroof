package com.baraa.masroof.accounts

import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.transaction.AccountLiquidityDefaults
import com.baraa.masroof.transaction.AccountNature
import com.baraa.masroof.transaction.AccountType
import com.baraa.masroof.transaction.Currency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Tests for [AccountInputValidator] and [DuplicateAccountDetector].
 */
class AccountInputValidatorTest {

    private fun today(): LocalDate = LocalDate.of(2024, 6, 15)

    @Test
    fun blankAccountNameFails() {
        val errors = AccountInputValidator.validate(
            name = "  ",
            openingBalance = BigDecimal("1000"),
            openingBalanceDate = today(),
            today = today(),
        )
        assertTrue(
            "blank name must produce BLANK_NAME error",
            errors.any { it.key == AccountInputValidator.ErrorKey.BLANK_NAME },
        )
    }

    @Test
    fun emptyNameFails() {
        val errors = AccountInputValidator.validate(
            name = "",
            openingBalance = BigDecimal.ZERO,
            openingBalanceDate = today(),
            today = today(),
        )
        assertEquals(1, errors.size)
        assertEquals(AccountInputValidator.ErrorKey.BLANK_NAME, errors.first().key)
    }

    @Test
    fun validNamePasses() {
        val errors = AccountInputValidator.validate(
            name = "Al Rajhi Checking",
            openingBalance = BigDecimal("1000"),
            openingBalanceDate = today(),
            today = today(),
        )
        assertTrue("valid input should produce no errors", errors.isEmpty())
    }

    @Test
    fun futureOpeningDateFails() {
        val fixedToday = LocalDate.of(2024, 6, 15)
        val future = fixedToday.plusDays(1)
        val errors = AccountInputValidator.validate(
            name = "X",
            openingBalance = BigDecimal.ZERO,
            openingBalanceDate = future,
            today = fixedToday,
        )
        assertTrue(errors.any { it.key == AccountInputValidator.ErrorKey.FUTURE_DATE })
    }

    @Test
    fun todayOpeningDatePasses() {
        val errors = AccountInputValidator.validate(
            name = "X",
            openingBalance = BigDecimal.ZERO,
            openingBalanceDate = today(),
            today = today(),
        )
        assertTrue(errors.isEmpty())
    }

    @Test
    fun negativeOpeningBalanceFails() {
        val errors = AccountInputValidator.validate(
            name = "X",
            openingBalance = BigDecimal("-100"),
            openingBalanceDate = today(),
            today = today(),
        )
        assertTrue(
            errors.any { it.key == AccountInputValidator.ErrorKey.NEGATIVE_OPENING_BALANCE },
        )
    }

    @Test
    fun zeroOpeningBalanceIsValid() {
        val errors = AccountInputValidator.validate(
            name = "X",
            openingBalance = BigDecimal.ZERO,
            openingBalanceDate = today(),
            today = today(),
        )
        assertTrue(errors.isEmpty())
    }

    // -- Duplicate detector --------------------------------------------

    private fun existingAccount(
        institution: String? = "Al Rajhi",
        type: AccountType = AccountType.BANK_ACCOUNT,
        nature: AccountNature = AccountNature.ASSET,
    ) = FinancialAccount(
        id = 1,
        displayName = "Al Rajhi",
        institutionName = institution,
        accountType = type,
        accountNature = nature,
        currency = Currency.SAR,
        openingBalance = BigDecimal.ZERO,
        openingBalanceDate = 0L,
        includeInNetWorth = true,
        includeInLiquidity = AccountLiquidityDefaults.defaultFor(type),
        isOwnedByUser = true,
        isActive = true,
        notes = null,
    )

    @Test
    fun duplicateDetectionFlagsExactMatch() {
        val existing = listOf(existingAccount())
        val match = DuplicateAccountDetector.isDuplicate(
            candidate = DuplicateAccountDetector.AccountToCheck(
                institutionName = "Al Rajhi",
                accountType = AccountType.BANK_ACCOUNT,
                accountNature = AccountNature.ASSET,
            ),
            existing = existing,
        )
        assertTrue("exact match must be flagged as duplicate", match)
    }

    @Test
    fun duplicateDetectionIsCaseInsensitiveOnInstitution() {
        val existing = listOf(existingAccount(institution = "AL RAJHI"))
        val match = DuplicateAccountDetector.isDuplicate(
            candidate = DuplicateAccountDetector.AccountToCheck(
                institutionName = "al rajhi",
                accountType = AccountType.BANK_ACCOUNT,
                accountNature = AccountNature.ASSET,
            ),
            existing = existing,
        )
        assertTrue(match)
    }

    @Test
    fun duplicateDetectionAllowsDifferentInstitution() {
        val existing = listOf(existingAccount(institution = "Al Rajhi"))
        val noMatch = DuplicateAccountDetector.isDuplicate(
            candidate = DuplicateAccountDetector.AccountToCheck(
                institutionName = "Alinma",
                accountType = AccountType.BANK_ACCOUNT,
                accountNature = AccountNature.ASSET,
            ),
            existing = existing,
        )
        assertFalse("different institution must not be flagged", noMatch)
    }

    @Test
    fun duplicateDetectionDoesNotBlockAccountCreation() {
        val existing = listOf(existingAccount())
        try {
            DuplicateAccountDetector.isDuplicate(
                DuplicateAccountDetector.AccountToCheck(
                    institutionName = "Al Rajhi",
                    accountType = AccountType.BANK_ACCOUNT,
                    accountNature = AccountNature.ASSET,
                ),
                existing = existing,
            )
        } catch (e: Throwable) {
            throw AssertionError("duplicate detector must not throw", e)
        }
    }

    @Test
    fun duplicateDetectionIgnoresNatureDifference() {
        val existing = listOf(existingAccount())
        val isDuplicate = DuplicateAccountDetector.isDuplicate(
            DuplicateAccountDetector.AccountToCheck(
                institutionName = "Al Rajhi",
                accountType = AccountType.BANK_ACCOUNT,
                accountNature = AccountNature.LIABILITY,
            ),
            existing = existing,
        )
        assertTrue(
            "same institution and type must be flagged regardless of nature",
            isDuplicate,
        )
    }
}

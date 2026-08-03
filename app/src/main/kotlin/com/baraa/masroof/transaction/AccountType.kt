package com.baraa.masroof.transaction

/**
 * Kind of financial account the user owns.
 *
 * The original set (BANK_ACCOUNT, CREDIT_CARD, WALLET, CASH, INVESTMENT_ACCOUNT,
 * OTHER) is preserved for backward compatibility. New types
 * [DIGITAL_WALLET], [SUKUK_ACCOUNT], [LOAN], [OTHER_ASSET], [OTHER_LIABILITY]
 * are added for the liquidity / net-worth foundation.
 *
 * [WALLET] is kept as a legacy alias for [DIGITAL_WALLET] — older code may
 * still use it; new code should prefer [DIGITAL_WALLET].
 */
enum class AccountType {
    BANK_ACCOUNT,
    CREDIT_CARD,
    DIGITAL_WALLET,
    /** Legacy alias for [DIGITAL_WALLET]. */
    WALLET,
    CASH,
    INVESTMENT_ACCOUNT,
    SUKUK_ACCOUNT,
    LOAN,
    OTHER_ASSET,
    OTHER_LIABILITY,
    OTHER,
    ;

    companion object {
        /** The nine account types presented to users during setup. */
        val setupTypes: List<AccountType> = listOf(
            BANK_ACCOUNT,
            CREDIT_CARD,
            DIGITAL_WALLET,
            CASH,
            INVESTMENT_ACCOUNT,
            SUKUK_ACCOUNT,
            LOAN,
            OTHER_ASSET,
            OTHER_LIABILITY,
        )
    }
}

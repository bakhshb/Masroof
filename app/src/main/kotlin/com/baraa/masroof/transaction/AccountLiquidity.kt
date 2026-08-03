package com.baraa.masroof.transaction

/**
 * Default `includeInLiquidity` value for each [AccountType]. The user
 * can override the default per account.
 *
 * Convention:
 *  - **BANK_ACCOUNT / DIGITAL_WALLET / CASH** count as immediately
 *    available money → included in liquidity.
 *  - **CREDIT_CARD / INVESTMENT_ACCOUNT / SUKUK_ACCOUNT / LOAN /
 *    OTHER_LIABILITY** are excluded from liquidity by default.
 *  - **OTHER_ASSET** is user-selectable (the user may have a receivable
 *    that is liquid, or a property that is not).
 *
 * The retail distinctions:
 *  - INVESTMENT_ACCOUNT and SUKUK_ACCOUNT are excluded from liquidity
 *    because they may take days to liquidate and may have early-
 *    withdrawal penalties.
 *  - CREDIT_CARD debt is not liquid (it's the available *headroom*, not
 *    money on hand).
 *  - LOAN is intentionally not in liquidity; it's a balance owed.
 */
object AccountLiquidityDefaults {

    fun defaultFor(type: AccountType): Boolean = when (type) {
        AccountType.BANK_ACCOUNT -> true
        AccountType.DIGITAL_WALLET -> true
        AccountType.WALLET -> true
        AccountType.CASH -> true
        AccountType.INVESTMENT_ACCOUNT -> false
        AccountType.SUKUK_ACCOUNT -> false
        AccountType.CREDIT_CARD -> false
        AccountType.LOAN -> false
        AccountType.OTHER_LIABILITY -> false
        AccountType.OTHER_ASSET -> false
        AccountType.OTHER -> false
    }
}

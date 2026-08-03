package com.baraa.masroof.transaction

/**
 * Whether an account is something the user **owns** (an asset) or
 * **owes** (a liability).
 *
 * The default mapping from [AccountType] is provided by
 * [defaultNatureFor]; users can override it through the account editor.
 */
enum class AccountNature {
    ASSET,
    LIABILITY,
    ;

    companion object {
        /**
         * Default mapping of account type → nature. The user can override
         * this per account (e.g. a credit card with a positive balance is
         * conceptually an asset, but the default stays LIABILITY for
         * consistency with typical use).
         */
        fun defaultNatureFor(type: AccountType): AccountNature = when (type) {
            AccountType.BANK_ACCOUNT -> ASSET
            AccountType.DIGITAL_WALLET -> ASSET
            AccountType.WALLET -> ASSET
            AccountType.CASH -> ASSET
            AccountType.INVESTMENT_ACCOUNT -> ASSET
            AccountType.SUKUK_ACCOUNT -> ASSET
            AccountType.OTHER_ASSET -> ASSET
            AccountType.CREDIT_CARD -> LIABILITY
            AccountType.LOAN -> LIABILITY
            AccountType.OTHER_LIABILITY -> LIABILITY
            AccountType.OTHER -> ASSET
        }
    }
}

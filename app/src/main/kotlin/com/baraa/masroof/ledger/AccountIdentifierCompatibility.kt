package com.baraa.masroof.ledger

import com.baraa.masroof.data.db.AccountIdentifierType
import com.baraa.masroof.transaction.AccountType
import com.baraa.masroof.transaction.TransactionType

/**
 * Single source of truth for account-type ↔ identifier-type compatibility
 * and for which identifier types a transaction may claim.
 *
 * Used by matching, repository validation, SMS binding analysis, and rules.
 */
object AccountIdentifierCompatibility {

    fun isCompatibleWithAccount(accountType: AccountType, identifierType: AccountIdentifierType): Boolean =
        when (accountType) {
            AccountType.BANK_ACCOUNT -> identifierType in setOf(
                AccountIdentifierType.ACCOUNT_LAST4,
                AccountIdentifierType.DEBIT_CARD_LAST4,
                AccountIdentifierType.IBAN_LAST4,
                AccountIdentifierType.SENDER_ALIAS,
            )
            AccountType.CREDIT_CARD -> identifierType in setOf(
                AccountIdentifierType.CREDIT_CARD_LAST4,
                AccountIdentifierType.SENDER_ALIAS,
            )
            AccountType.DIGITAL_WALLET, AccountType.WALLET -> identifierType in setOf(
                AccountIdentifierType.WALLET_LAST4,
                AccountIdentifierType.ACCOUNT_LAST4,
                AccountIdentifierType.SENDER_ALIAS,
            )
            AccountType.INVESTMENT_ACCOUNT, AccountType.SUKUK_ACCOUNT -> identifierType in setOf(
                AccountIdentifierType.ACCOUNT_LAST4,
                AccountIdentifierType.IBAN_LAST4,
                AccountIdentifierType.SENDER_ALIAS,
            )
            else -> identifierType == AccountIdentifierType.SENDER_ALIAS
        }

    /** Compatibility without allowing SENDER_ALIAS (for typed SMS evidence). */
    fun isCompatibleTyped(accountType: AccountType, identifierType: AccountIdentifierType): Boolean =
        identifierType != AccountIdentifierType.SENDER_ALIAS &&
            isCompatibleWithAccount(accountType, identifierType)

    fun identifierTypesFor(transactionType: TransactionType): List<AccountIdentifierType> = when (transactionType) {
        TransactionType.CARD_PAYMENT -> listOf(
            AccountIdentifierType.CREDIT_CARD_LAST4,
            AccountIdentifierType.DEBIT_CARD_LAST4,
        )
        TransactionType.TRANSFER_OUT, TransactionType.TRANSFER_IN, TransactionType.INTERNAL_TRANSFER,
        TransactionType.LOAN_INSTALLMENT, TransactionType.BILL_PAYMENT,
        -> listOf(
            AccountIdentifierType.ACCOUNT_LAST4,
            AccountIdentifierType.IBAN_LAST4,
        )
        else -> listOf(
            AccountIdentifierType.ACCOUNT_LAST4,
            AccountIdentifierType.DEBIT_CARD_LAST4,
            AccountIdentifierType.CREDIT_CARD_LAST4,
            AccountIdentifierType.WALLET_LAST4,
        )
    }

    fun identifierTypeFitsTransaction(
        identifierType: AccountIdentifierType,
        transactionType: TransactionType,
    ): Boolean =
        identifierType in identifierTypesFor(transactionType) ||
            transactionType == TransactionType.UNKNOWN

    fun defaultIdentifierTypeFor(accountType: AccountType): AccountIdentifierType? = when (accountType) {
        AccountType.BANK_ACCOUNT, AccountType.INVESTMENT_ACCOUNT, AccountType.SUKUK_ACCOUNT ->
            AccountIdentifierType.ACCOUNT_LAST4
        AccountType.CREDIT_CARD -> AccountIdentifierType.CREDIT_CARD_LAST4
        AccountType.DIGITAL_WALLET, AccountType.WALLET -> AccountIdentifierType.WALLET_LAST4
        else -> null
    }

    fun accountCompatibleWithoutIdentifier(accountType: AccountType, transactionType: TransactionType): Boolean =
        when (transactionType) {
            TransactionType.CARD_PAYMENT ->
                accountType in setOf(AccountType.CREDIT_CARD, AccountType.BANK_ACCOUNT)
            else -> accountType !in setOf(AccountType.CASH, AccountType.OTHER_ASSET, AccountType.OTHER_LIABILITY)
        }
}

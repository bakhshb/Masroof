package com.baraa.masroof.domain.ids

import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.CardReference
import java.security.MessageDigest

/**
 * Deterministic, bank-scoped FinancialContainer ids.
 *
 * Account and card namespaces are separate so account 7271 and card 7271 never collide.
 */
object FinancialContainerIdFactory {
    fun accountId(bank: Bank, maskedNumber: String): String =
        "account:${bank.id}:${maskedNumber.trim()}"

    fun accountId(reference: AccountReference): String? {
        val masked = reference.maskedNumber?.trim().orEmpty()
        if (masked.isEmpty()) return null
        return accountId(reference.bank, masked)
    }

    fun cardId(bank: Bank, last4: String): String =
        "card:${bank.id}:${last4.trim()}"

    fun cardId(reference: CardReference): String? {
        val last4 = reference.last4?.trim().orEmpty()
        if (last4.isEmpty()) return null
        return cardId(reference.bank, last4)
    }
}

/**
 * Deterministic FinancialTransaction ids derived from sorted RawSms evidence ids.
 */
object TransactionIdFactory {
    fun fromRawSmsIds(rawSmsIds: Collection<String>): String {
        require(rawSmsIds.isNotEmpty()) { "rawSmsIds must not be empty" }
        val canonical = rawSmsIds.map { it.trim() }.filter { it.isNotEmpty() }.sorted()
            .joinToString(separator = "\u001e")
        require(canonical.isNotEmpty()) { "rawSmsIds must contain at least one non-blank id" }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }
        return "transaction:$hex"
    }
}

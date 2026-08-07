package com.baraa.masroof.ai

import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.data.repository.AccountIdentifierRepository
import com.baraa.masroof.data.repository.TransactionSmsBodyRepository
import com.baraa.masroof.sms.SmsRepository

/**
 * Builds a [LinkAssistRequest] from a stored transaction + local SMS body.
 * Falls back to recovering the body from the device inbox, then to a
 * structured field summary so suggest still works for older imports.
 */
object LinkAssistRequestFactory {
    suspend fun fromTransaction(
        transaction: TransactionEntity,
        accounts: List<FinancialAccount>,
        smsBodyRepository: TransactionSmsBodyRepository,
        identifierRepository: AccountIdentifierRepository,
        smsBodyOverride: String? = null,
        smsRepository: SmsRepository? = null,
    ): LinkAssistRequest? {
        var body = smsBodyOverride?.trim()?.takeIf { it.isNotEmpty() }
            ?: smsBodyRepository.getBody(transaction.id)?.trim()?.takeIf { it.isNotEmpty() }

        if (body == null && smsRepository != null) {
            body = runCatching {
                smsRepository.findBodyBySenderAndTimestamp(
                    sender = transaction.originalSender,
                    timestampMillis = transaction.smsTimestamp,
                )
            }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
            if (body != null) {
                runCatching { smsBodyRepository.save(transaction.id, body) }
            }
        }

        if (body == null) {
            body = syntheticBodyFromFields(transaction)
        }

        val owned = accounts.filter { it.isActive && it.isOwnedByUser && it.systemAccountKey == null }
        if (owned.isEmpty()) return null
        val snapshots = identifierRepository.getActiveSnapshots()
        val byAccount = snapshots.groupBy { it.accountId }
        return LinkAssistRequest(
            smsBody = body,
            sender = transaction.originalSender,
            transactionType = transaction.transactionType.name,
            amount = transaction.amount?.toPlainString(),
            currency = transaction.currency.name,
            transactionDate = transaction.transactionDate?.toString(),
            lastFourEvidence = transaction.accountOrCardLastFourDigits?.takeLast(4),
            accounts = owned.map { acc ->
                LinkAssistAccount(
                    id = acc.id,
                    displayName = acc.displayName,
                    accountType = acc.accountType.name,
                    identifierLast4s = byAccount[acc.id]
                        ?.map { it.normalizedValue.takeLast(4) }
                        ?.distinct()
                        .orEmpty(),
                )
            },
        )
    }

    /** Minimal text when the inbox body is unavailable — never invents amounts. */
    fun syntheticBodyFromFields(tx: TransactionEntity): String = buildString {
        append("نوع=").append(tx.transactionType.name)
        tx.amount?.let { append(" مبلغ=").append(it.toPlainString()) }
        append(" عملة=").append(tx.currency.name)
        tx.accountOrCardLastFourDigits?.takeLast(4)?.let { append(" بطاقة=*").append(it) }
        tx.merchantOrBeneficiary?.takeIf { it.isNotBlank() }?.let { append(" تاجر=").append(it) }
        tx.originalSender?.takeIf { it.isNotBlank() }?.let { append(" مرسل=").append(it) }
    }
}

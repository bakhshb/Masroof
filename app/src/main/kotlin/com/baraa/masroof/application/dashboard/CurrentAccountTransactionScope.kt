package com.baraa.masroof.application.dashboard

import com.baraa.masroof.domain.ids.FinancialContainerIdFactory
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.RawSms
import com.baraa.masroof.parsing.repository.ParsedEventRecord

internal data class CurrentAccountTransactionScope(
    val ownedContainerIds: Set<String>,
    val ownedAccountLast4s: Set<String>,
) {
    fun involvesOwnedSource(
        tx: FinancialTransaction,
        parsedRecordsById: Map<String, ParsedEventRecord>,
    ): Boolean {
        if (ownedContainerIds.isEmpty()) return true
        val sourceId = resolveSourceContainerId(tx, parsedRecordsById) ?: return false
        return matchesOwnedContainer(sourceId)
    }

    fun involvesOwnedDestination(
        tx: FinancialTransaction,
        parsedRecordsById: Map<String, ParsedEventRecord>,
    ): Boolean {
        if (ownedContainerIds.isEmpty()) return true
        val destId = resolveDestinationContainerId(tx, parsedRecordsById) ?: return false
        return matchesOwnedContainer(destId)
    }

    fun isBillPayment(
        tx: FinancialTransaction,
        billPaymentTxIds: Set<String>,
        parsedRecordsById: Map<String, ParsedEventRecord>,
        rawSmsById: Map<String, RawSms>,
    ): Boolean {
        if (tx.id in billPaymentTxIds) return true
        return linkedRecords(tx, parsedRecordsById).any { record ->
            record.event.messageFamily == MessageFamily.BILL_PAYMENT ||
                smsBody(rawSmsById, record).containsBillPaymentWording()
        }
    }

    fun isCreditCardPayment(
        tx: FinancialTransaction,
        parsedRecordsById: Map<String, ParsedEventRecord>,
        rawSmsById: Map<String, RawSms>,
    ): Boolean {
        if (tx.type == FinancialTransactionType.CREDIT_CARD_PAYMENT) return true
        return linkedRecords(tx, parsedRecordsById).any { record ->
            record.event.messageFamily == MessageFamily.CARD_PAYMENT ||
                smsBody(rawSmsById, record).containsCreditCardPaymentWording()
        }
    }

    fun isCashWithdrawal(
        tx: FinancialTransaction,
        parsedRecordsById: Map<String, ParsedEventRecord>,
        rawSmsById: Map<String, RawSms>,
    ): Boolean {
        if (tx.type == FinancialTransactionType.CASH_WITHDRAWAL) return true
        return linkedRecords(tx, parsedRecordsById).any { record ->
            record.event.messageFamily == MessageFamily.WITHDRAWAL ||
                smsBody(rawSmsById, record).containsCashWithdrawalWording()
        }
    }

    private fun matchesOwnedContainer(containerId: String): Boolean {
        if (containerId in ownedContainerIds) return true
        if (!containerId.startsWith("account:")) return false
        val last4 = containerId.substringAfterLast(':')
        return last4 in ownedAccountLast4s
    }

    private fun resolveSourceContainerId(
        tx: FinancialTransaction,
        parsedRecordsById: Map<String, ParsedEventRecord>,
    ): String? {
        tx.sourceContainerId?.let { return it }
        return linkedRecords(tx, parsedRecordsById)
            .mapNotNull { record -> record.event.sourceAccountRef?.let(FinancialContainerIdFactory::accountId) }
            .firstOrNull()
    }

    private fun resolveDestinationContainerId(
        tx: FinancialTransaction,
        parsedRecordsById: Map<String, ParsedEventRecord>,
    ): String? {
        tx.destinationContainerId?.let { return it }
        return linkedRecords(tx, parsedRecordsById)
            .mapNotNull { record -> record.event.destinationAccountRef?.let(FinancialContainerIdFactory::accountId) }
            .firstOrNull()
    }

    private fun linkedRecords(
        tx: FinancialTransaction,
        parsedRecordsById: Map<String, ParsedEventRecord>,
    ): List<ParsedEventRecord> =
        tx.linkedParsedEventIds.mapNotNull { parsedRecordsById[it] }

    private fun smsBody(rawSmsById: Map<String, RawSms>, record: ParsedEventRecord): String =
        rawSmsById[record.event.rawSmsId]?.body.orEmpty()

    private fun String.containsBillPaymentWording(): Boolean =
        contains("سداد فاتورة") || contains("المفوتر:")

    private fun String.containsCreditCardPaymentWording(): Boolean =
        contains("سداد بطاقة") || contains("سداد بطاقه")

    private fun String.containsCashWithdrawalWording(): Boolean =
        contains("سحب نقدي") || contains("سحب نقدى")
}

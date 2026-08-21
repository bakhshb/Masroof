package com.baraa.masroof.application.dashboard

import com.baraa.masroof.domain.ids.FinancialContainerIdFactory
import com.baraa.masroof.domain.model.Bank
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
        rawSmsById: Map<String, RawSms>,
    ): Boolean {
        if (ownedContainerIds.isEmpty()) return true
        val sourceId = resolveSourceContainerId(tx, parsedRecordsById, rawSmsById)
        if (sourceId == null) {
            return ownedContainerIds.isEmpty() && tx.type in TRUSTED_OWNED_SOURCE_TYPES
        }
        return matchesOwnedContainer(sourceId)
    }

    fun involvesOwnedDestination(
        tx: FinancialTransaction,
        parsedRecordsById: Map<String, ParsedEventRecord>,
        rawSmsById: Map<String, RawSms>,
    ): Boolean {
        if (ownedContainerIds.isEmpty()) return true
        val destId = resolveDestinationContainerId(tx, parsedRecordsById, rawSmsById)
        if (destId == null) {
            return ownedContainerIds.isEmpty() && tx.type in TRUSTED_OWNED_DESTINATION_TYPES
        }
        return matchesOwnedContainer(destId)
    }

    fun isBillPayment(
        tx: FinancialTransaction,
        billPaymentTxIds: Set<String>,
        parsedRecordsById: Map<String, ParsedEventRecord>,
        rawSmsById: Map<String, RawSms>,
    ): Boolean {
        if (tx.type == FinancialTransactionType.BILL_PAYMENT) return true
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
        rawSmsById: Map<String, RawSms>,
    ): String? {
        tx.sourceContainerId?.let { return it }
        for (record in linkedRecords(tx, parsedRecordsById)) {
            record.event.sourceAccountRef
                ?.let(FinancialContainerIdFactory::accountId)
                ?.let { return it }
            accountIdFromSmsBody(record, rawSmsById)?.let { return it }
        }
        return null
    }

    private fun resolveDestinationContainerId(
        tx: FinancialTransaction,
        parsedRecordsById: Map<String, ParsedEventRecord>,
        rawSmsById: Map<String, RawSms>,
    ): String? {
        tx.destinationContainerId?.let { return it }
        for (record in linkedRecords(tx, parsedRecordsById)) {
            record.event.destinationAccountRef
                ?.let(FinancialContainerIdFactory::accountId)
                ?.let { return it }
            accountIdFromDestinationSmsBody(record, rawSmsById)?.let { return it }
        }
        return null
    }

    private fun accountIdFromSmsBody(
        record: ParsedEventRecord,
        rawSmsById: Map<String, RawSms>,
    ): String? {
        if (record.event.bank == Bank.UNKNOWN) return null
        val last4 = extractSourceAccountLast4(smsBody(rawSmsById, record)) ?: return null
        return FinancialContainerIdFactory.accountId(record.event.bank, last4)
    }

    private fun accountIdFromDestinationSmsBody(
        record: ParsedEventRecord,
        rawSmsById: Map<String, RawSms>,
    ): String? {
        if (record.event.bank == Bank.UNKNOWN) return null
        val last4 = extractDestinationAccountLast4(smsBody(rawSmsById, record)) ?: return null
        return FinancialContainerIdFactory.accountId(record.event.bank, last4)
    }

    private fun extractSourceAccountLast4(body: String): String? =
        SOURCE_ACCOUNT_PATTERNS.firstNotNullOfOrNull { pattern ->
            pattern.find(body)?.groupValues?.getOrNull(1)
        }

    private fun extractDestinationAccountLast4(body: String): String? =
        DESTINATION_ACCOUNT_PATTERNS.firstNotNullOfOrNull { pattern ->
            pattern.find(body)?.groupValues?.getOrNull(1)
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

    companion object {
        private val SOURCE_ACCOUNT_PATTERNS = listOf(
            Regex("""خصمت\s*من\s*حساب\s*:\s*(\d{4})"""),
            Regex("""من\s*حساب\s*:\s*(\d{4})"""),
            Regex("""رقم\s*حساب\s*المرسل\s*:\s*(\d{4})"""),
            Regex("""(?<![\p{L}])حساب\s*:\s*(\d{4})"""),
        )

        private val DESTINATION_ACCOUNT_PATTERNS = listOf(
            Regex("""أودعت\s*(?:إلى|الى)\s*حساب\s*:\s*(\d{4})"""),
            Regex("""إلى\s*حساب\s*:\s*(\d{4})"""),
            Regex("""الى\s*حساب\s*:\s*(\d{4})"""),
            Regex("""إلى\s*:\s*(\d{4})"""),
            Regex("""الى\s*:\s*(\d{4})"""),
        )

        private val TRUSTED_OWNED_SOURCE_TYPES = setOf(
            FinancialTransactionType.CASH_WITHDRAWAL,
            FinancialTransactionType.CREDIT_CARD_PAYMENT,
            FinancialTransactionType.BILL_PAYMENT,
            FinancialTransactionType.EXTERNAL_TRANSFER_OUT,
            FinancialTransactionType.EXPENSE,
            FinancialTransactionType.FEE,
        )

        private val TRUSTED_OWNED_DESTINATION_TYPES = setOf(
            FinancialTransactionType.INCOME,
            FinancialTransactionType.EXTERNAL_TRANSFER_IN,
            FinancialTransactionType.REFUND,
        )

        fun ownedAccountLast4sFromMaskedNumbers(maskedNumbers: Collection<String>): Set<String> =
            maskedNumbers
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .flatMap { masked ->
                    if (masked.length <= 4) {
                        listOf(masked)
                    } else {
                        listOf(masked, masked.takeLast(4))
                    }
                }
                .toSet()
    }
}

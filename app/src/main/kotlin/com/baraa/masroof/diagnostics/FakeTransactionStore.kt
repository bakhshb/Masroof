package com.baraa.masroof.diagnostics

import com.baraa.masroof.data.db.DateSource
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.transaction.Currency
import com.baraa.masroof.transaction.TransactionStatus
import com.baraa.masroof.transaction.TransactionType
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory store for test-data-mode transactions. Kept **completely
 * separate** from the real [com.baraa.masroof.data.db.MasroofDatabase]
 * — these rows are never inserted into `transactions` and are lost on
 * process death.
 *
 * The whole point of the test-data mode is to exercise the parser /
 * rule pipeline without ever mixing fake rows with real user data.
 */
object FakeTransactionStore {

    data class FakeRow(
        val id: Long,
        val sourceSampleId: String,
        val merchant: String,
        val amount: BigDecimal,
        val currency: Currency,
        val transactionType: TransactionType,
        val status: TransactionStatus,
        val rawSanitizedBody: String,
        val date: LocalDate?,
        val time: LocalTime?,
    )

    private val rows: MutableList<FakeRow> = mutableListOf()
    private val nextId = AtomicLong(1L)

    @Synchronized
    fun snapshot(): List<FakeRow> = rows.toList()

    @Synchronized
    fun count(): Int = rows.size

    @Synchronized
    fun clear() { rows.clear() }

    @Synchronized
    fun addFromParse(
        sampleId: String,
        sender: String?,
        rawBody: String,
        merchant: String?,
        amount: BigDecimal?,
        currency: Currency,
        type: TransactionType,
        status: TransactionStatus,
        date: LocalDate?,
        time: LocalTime?,
    ): Long {
        val id = nextId.getAndIncrement()
        rows.add(
            FakeRow(
                id = id,
                sourceSampleId = sampleId,
                merchant = merchant ?: sender.orEmpty(),
                amount = amount ?: BigDecimal.ZERO,
                currency = currency,
                transactionType = type,
                status = status,
                rawSanitizedBody = TextSanitizer.sanitize(rawBody),
                date = date,
                time = time,
            )
        )
        return id
    }
}
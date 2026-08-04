package com.baraa.masroof.ui.transactions

import androidx.compose.runtime.saveable.Saver
import com.baraa.masroof.ledger.TransactionPostingStatus
import java.time.LocalDate

class TransactionOpsState {
    var query by mutableStateOf("")
    var fromDate: LocalDate? = null
    var toDate: LocalDate? = null
    var needsReview by mutableStateOf(false)
    var unlinked by mutableStateOf(false)
    var unclassified by mutableStateOf(false)
    var expenses by mutableStateOf(false)
    var income by mutableStateOf(false)
    var internalTransfers by mutableStateOf(false)
    var investments by mutableStateOf(false)
    var cardPayments by mutableStateOf(false)
    var refunds by mutableStateOf(false)
    var bankFees by mutableStateOf(false)
    var accountId: Long? = null
    var categoryId: Long? = null
    var postingStatuses: Set<TransactionPostingStatus> = emptySet()

    val isEmpty: Boolean get() = query.isBlank() && fromDate == null && toDate == null && !needsReview && !unlinked && !unclassified && !expenses && !income && !internalTransfers && !investments && !cardPayments && !refunds && !bankFees && accountId == null && categoryId == null && postingStatuses.isEmpty()

    fun reset() { query = ""; fromDate = null; toDate = null; needsReview = false; unlinked = false; unclassified = false; expenses = false; income = false; internalTransfers = false; investments = false; cardPayments = false; refunds = false; bankFees = false; accountId = null; categoryId = null; postingStatuses = emptySet() }
    fun snapshot() { /* already tracked via mutableState */ }
    fun toFilter() = TransactionFilter(query = query, fromDate = fromDate, toDate = toDate, needsReview = needsReview, unlinked = unlinked, unclassified = unclassified, expenses = expenses, income = income, internalTransfers = internalTransfers, investments = investments, cardPayments = cardPayments, refunds = refunds, bankFees = bankFees, accountId = accountId, categoryId = categoryId, postingStatuses = postingStatuses)
}

private fun <T> mutableStateOf(value: T) = androidx.compose.runtime.mutableStateOf(value)
private operator fun <T> androidx.compose.runtime.MutableState<T>.setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: T) { this.value = value }
private operator fun <T> androidx.compose.runtime.MutableState<T>.getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): T = this.value

val TransactionOpsStateSaver: Saver<TransactionOpsState, Any> = Saver(
    save = { state ->
        mapOf(
            "query" to state.query, "fromDate" to state.fromDate?.toString(), "toDate" to state.toDate?.toString(),
            "needsReview" to state.needsReview, "unlinked" to state.unlinked, "unclassified" to state.unclassified,
            "expenses" to state.expenses, "income" to state.income, "internalTransfers" to state.internalTransfers,
            "investments" to state.investments, "cardPayments" to state.cardPayments, "refunds" to state.refunds,
            "bankFees" to state.bankFees, "accountId" to state.accountId, "categoryId" to state.categoryId,
            "postingStatuses" to state.postingStatuses.map { it.name },
        )
    },
    restore = { map ->
        @Suppress("UNCHECKED_CAST") val m = map as Map<String, Any?>
        TransactionOpsState().apply {
            query = m["query"] as? String ?: ""
            fromDate = (m["fromDate"] as? String)?.let { LocalDate.parse(it) }
            toDate = (m["toDate"] as? String)?.let { LocalDate.parse(it) }
            needsReview = m["needsReview"] as? Boolean ?: false
            unlinked = m["unlinked"] as? Boolean ?: false
            unclassified = m["unclassified"] as? Boolean ?: false
            expenses = m["expenses"] as? Boolean ?: false
            income = m["income"] as? Boolean ?: false
            internalTransfers = m["internalTransfers"] as? Boolean ?: false
            investments = m["investments"] as? Boolean ?: false
            cardPayments = m["cardPayments"] as? Boolean ?: false
            refunds = m["refunds"] as? Boolean ?: false
            bankFees = m["bankFees"] as? Boolean ?: false
            accountId = (m["accountId"] as? Long)
            categoryId = (m["categoryId"] as? Long)
            postingStatuses = ((m["postingStatuses"] as? List<String>) ?: emptyList()).mapNotNull { runCatching { TransactionPostingStatus.valueOf(it) }.getOrNull() }.toSet()
        }
    },
)
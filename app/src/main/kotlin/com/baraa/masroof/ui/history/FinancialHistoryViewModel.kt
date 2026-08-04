package com.baraa.masroof.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.baraa.masroof.MasroofApplication
import com.baraa.masroof.ledger.HistoricalFinancialService
import com.baraa.masroof.ledger.MonthlyFinancialHistory
import com.baraa.masroof.ledger.TransactionPostingStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

data class FinancialHistoryState(val month: YearMonth = YearMonth.now(), val selectedDate: LocalDate = LocalDate.now(), val history: MonthlyFinancialHistory? = null, val loading: Boolean = true, val error: Boolean = false, val endOfDay: Boolean = true)
class FinancialHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MasroofApplication
    private val cache = mutableMapOf<YearMonth, MonthlyFinancialHistory>()
    private val _state = MutableStateFlow(FinancialHistoryState())
    val state: StateFlow<FinancialHistoryState> = _state.asStateFlow()
    init {
        load(YearMonth.now())
        viewModelScope.launch {
            combine(
                app.financialAccountRepository.observeAll(),
                app.database.journalDao().observePosted(),
                app.transactionRepository.observeAll(),
            ) { _, _, _ -> Unit }.drop(1).collect {
                cache.clear()
                load(_state.value.month, _state.value.selectedDate)
            }
        }
    }
    fun load(month: YearMonth, date: LocalDate = month.atDay(minOf(_state.value.selectedDate.dayOfMonth, month.lengthOfMonth()))) {
        if (month > YearMonth.now()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(month = month, selectedDate = date, loading = cache[month] == null, error = false, history = cache[month] ?: _state.value.history)
            runCatching {
                cache[month] ?: withContext(Dispatchers.Default) {
                    val accounts = app.financialAccountRepository.observeAll().first()
                    val journals = app.database.journalDao().getPostedThrough(month.atEndOfMonth())
                    val unposted = app.transactionRepository.getAllNewestFirst().filter { it.postingStatus != TransactionPostingStatus.POSTED }.groupingBy { it.transactionDate }.eachCount().filterKeys { it != null }.mapKeys { it.key!! }
                    HistoricalFinancialService.calculateMonth(month, accounts, journals, unposted)
                }.also { cache[month] = it }
            }.onSuccess { _state.value = _state.value.copy(history = it, loading = false) }.onFailure { _state.value = _state.value.copy(loading = false, error = true) }
        }
    }
    fun select(date: LocalDate) { _state.value = _state.value.copy(selectedDate = date) }
    fun setEndOfDay(value: Boolean) { _state.value = _state.value.copy(endOfDay = value) }
    fun retry() = load(_state.value.month, _state.value.selectedDate)
}

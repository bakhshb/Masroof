package com.baraa.masroof.accounts

import com.baraa.masroof.data.db.AccountIdentifierType
import com.baraa.masroof.data.repository.AccountIdentifierRepository
import com.baraa.masroof.data.repository.IdentifierAddOutcome
import com.baraa.masroof.data.repository.IdentifierAddResult
import com.baraa.masroof.data.repository.IdentifierForm
import com.baraa.masroof.data.repository.SenderProfileRepository
import com.baraa.masroof.sms.BankSmsFilter
import com.baraa.masroof.sms.ExpectedSalaryDateService
import com.baraa.masroof.sms.SmsImportRange
import com.baraa.masroof.sms.SmsMessage
import com.baraa.masroof.sms.SmsRepository
import com.baraa.masroof.transaction.AccountType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** View-layer observable state and operations for the SMS-binding picker. */
class SmsBindingStateHolder(
    private val smsRepository: SmsRepository,
    private val identifierRepository: AccountIdentifierRepository,
    private val senderProfileRepository: SenderProfileRepository,
    private val afterBindRelink: (suspend () -> Unit)? = null,
    private val todayProvider: () -> LocalDate = { LocalDate.now() },
) {
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    enum class RangeMode {
        LAST_7,
        LAST_30,
        LAST_SALARY,
        CUSTOM_FROM,
    }

    data class State(
        val loading: Boolean = false,
        val messages: List<SmsMessage> = emptyList(),
        val rangeMode: RangeMode = RangeMode.LAST_30,
        val customFrom: LocalDate = LocalDate.now().minusDays(29),
        val senderQuery: String = "",
        val showAllMessages: Boolean = false,
        val selected: SmsMessage? = null,
        val analysis: AccountSmsAnalysis? = null,
        val error: String? = null,
        val committed: Boolean = false,
        val rangeLabel: String = "",
    ) {
        val visibleMessages get() = messages.filter { message ->
            message.sender.orEmpty().contains(senderQuery, ignoreCase = true) &&
                (showAllMessages || BankSmsFilter.classifyMessage(message.sender, message.body).isMatch)
        }
    }

    suspend fun refresh() = refreshFor(_state.value.rangeMode, _state.value.customFrom)

    suspend fun refreshFor(mode: RangeMode, customFrom: LocalDate = _state.value.customFrom) {
        val today = todayProvider()
        val from = customFrom.coerceAtMost(today)
        _state.update {
            it.copy(
                loading = true,
                rangeMode = mode,
                customFrom = from,
                rangeLabel = describeRange(mode, from, today),
            )
        }
        val range = resolveRange(mode, from, today)
        val loaded = runCatching {
            smsRepository.loadInbox(range, BINDING_INBOX_LIMIT)
        }.getOrDefault(emptyList())
            .sortedByDescending { BankSmsFilter.classifyMessage(it.sender, it.body).isMatch }
        _state.update { it.copy(loading = false, messages = loaded) }
    }

    fun setCustomFrom(date: LocalDate) {
        val today = todayProvider()
        val from = date.coerceAtMost(today)
        _state.update {
            it.copy(
                customFrom = from,
                rangeMode = RangeMode.CUSTOM_FROM,
                rangeLabel = describeRange(RangeMode.CUSTOM_FROM, from, today),
            )
        }
    }

    fun setSenderQuery(value: String) = _state.update { it.copy(senderQuery = value) }
    fun setShowAll(value: Boolean) = _state.update { it.copy(showAllMessages = value) }
    fun choose(message: SmsMessage, accountType: AccountType) {
        _state.update { it.copy(selected = message, analysis = AccountSmsAnalyzer.analyze(message, accountType), error = null) }
    }
    fun chooseAnother() = _state.update { it.copy(selected = null, analysis = null, error = null) }

    /** Persist the confirmed binding; returns true only if sender + identifier writes succeed. */
    suspend fun commit(accountId: Long): Boolean {
        val analysis = _state.value.analysis ?: run {
            _state.update { it.copy(error = "تعذر استخراج بيانات آمنة من الرسالة.") }
            return false
        }
        val senderOk = runCatching {
            val profile = senderProfileRepository.upsertFromSmsSender(analysis.senderDisplay)
            senderProfileRepository.associateAccount(accountId, profile.id)
            true
        }.getOrDefault(false)
        val identifierOutcome: IdentifierAddOutcome? = analysis.identifierType?.let { type ->
            identifierRepository.addOrUpdate(accountId, IdentifierForm(type, typeLabel(type), analysis.lastFour.orEmpty()))
        }
        val identifierOk = analysis.identifierType == null ||
            (identifierOutcome != null &&
                identifierOutcome.result != IdentifierAddResult.Rejected &&
                identifierOutcome.identifier != null)
        if (senderOk && identifierOk) {
            runCatching { afterBindRelink?.invoke() }
            _state.update { it.copy(committed = true, error = null) }
            return true
        }
        _state.update { it.copy(error = "تعذر حفظ الربط. راجع البيانات ثم حاول مرة أخرى.") }
        return false
    }

    private fun typeLabel(type: AccountIdentifierType) = when (type) {
        AccountIdentifierType.CREDIT_CARD_LAST4 -> "بطاقة ائتمانية"
        AccountIdentifierType.DEBIT_CARD_LAST4 -> "بطاقة مدى"
        AccountIdentifierType.IBAN_LAST4 -> "آيبان"
        AccountIdentifierType.WALLET_LAST4 -> "محفظة"
        AccountIdentifierType.ACCOUNT_LAST4 -> "حساب"
    }

    companion object {
        const val BINDING_INBOX_LIMIT: Int = 500

        fun resolveRange(mode: RangeMode, customFrom: LocalDate, today: LocalDate): SmsImportRange = when (mode) {
            RangeMode.LAST_7 -> SmsImportRange.lastDays(today, 7)
            RangeMode.LAST_30 -> SmsImportRange.lastDays(today, 30)
            RangeMode.LAST_SALARY -> SmsImportRange.sinceLastSalary(today)
            RangeMode.CUSTOM_FROM -> SmsImportRange.custom(customFrom.coerceAtMost(today), today, today)
        }

        fun describeRange(mode: RangeMode, customFrom: LocalDate, today: LocalDate): String {
            val range = resolveRange(mode, customFrom, today)
            val fmt = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ar"))
            val from = range.start.toLocalDate().format(fmt)
            val to = range.displayEndDate.format(fmt)
            return "$from → $to"
        }

        fun expectedSalaryDate(today: LocalDate): LocalDate =
            ExpectedSalaryDateService.mostRecentSalaryDate(today)
    }
}

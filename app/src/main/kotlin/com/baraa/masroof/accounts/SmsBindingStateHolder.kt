package com.baraa.masroof.accounts

import com.baraa.masroof.data.db.AccountIdentifierType
import com.baraa.masroof.data.repository.AccountIdentifierRepository
import com.baraa.masroof.data.repository.IdentifierAddOutcome
import com.baraa.masroof.data.repository.IdentifierAddResult
import com.baraa.masroof.data.repository.IdentifierForm
import com.baraa.masroof.sms.BankSmsFilter
import com.baraa.masroof.sms.SmsImportRange
import com.baraa.masroof.sms.SmsMessage
import com.baraa.masroof.sms.SmsRepository
import com.baraa.masroof.transaction.AccountType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/** View-layer observable state and operations for the SMS-binding picker. */
class SmsBindingStateHolder(
    private val smsRepository: SmsRepository,
    private val identifierRepository: AccountIdentifierRepository,
) {
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    data class State(
        val loading: Boolean = false,
        val messages: List<SmsMessage> = emptyList(),
        val days: Int = 30,
        val senderQuery: String = "",
        val showAllMessages: Boolean = false,
        val selected: SmsMessage? = null,
        val analysis: AccountSmsAnalysis? = null,
        val error: String? = null,
        val committed: Boolean = false,
    ) {
        val visibleMessages get() = messages.filter { message ->
            message.sender.orEmpty().contains(senderQuery, ignoreCase = true) &&
                (showAllMessages || BankSmsFilter.classifyMessage(message.sender, message.body).isMatch)
        }
    }

    suspend fun refresh() = refreshFor(days = _state.value.days)

    suspend fun refreshFor(days: Int) {
        _state.update { it.copy(loading = true, days = days) }
        val loaded = runCatching { smsRepository.loadInbox(SmsImportRange.lastDays(LocalDate.now(), days), 100) }.getOrDefault(emptyList())
            .sortedByDescending { BankSmsFilter.classifyMessage(it.sender, it.body).isMatch }
        _state.update { it.copy(loading = false, messages = loaded) }
    }

    fun setSenderQuery(value: String) = _state.update { it.copy(senderQuery = value) }
    fun setShowAll(value: Boolean) = _state.update { it.copy(showAllMessages = value) }
    fun choose(message: SmsMessage, accountType: AccountType) {
        _state.update { it.copy(selected = message, analysis = AccountSmsAnalyzer.analyze(message, accountType), error = null) }
    }
    fun chooseAnother() = _state.update { it.copy(selected = null, analysis = null, error = null) }

    /** Persist the confirmed binding; returns true only if both writes produced real rows. */
    suspend fun commit(accountId: Long): Boolean {
        val analysis = _state.value.analysis ?: run { _state.update { it.copy(error = "تعذر استخراج بيانات آمنة من الرسالة.") } ; return false }
        val sender = identifierRepository.addOrUpdate(accountId, IdentifierForm(AccountIdentifierType.SENDER_ALIAS, analysis.senderDisplay, analysis.senderDisplay))
        val senderOk = sender.result != IdentifierAddResult.Rejected && sender.identifier != null
        val identifierOutcome: IdentifierAddOutcome? = analysis.identifierType?.let { type ->
            identifierRepository.addOrUpdate(accountId, IdentifierForm(type, typeLabel(type), analysis.lastFour.orEmpty()))
        }
        val identifierOk = analysis.identifierType == null || (identifierOutcome != null && identifierOutcome.result != IdentifierAddResult.Rejected && identifierOutcome.identifier != null)
        if (senderOk && identifierOk) {
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
        AccountIdentifierType.SENDER_ALIAS -> "اسم المرسل"
    }
}
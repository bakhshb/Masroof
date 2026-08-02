package com.baraa.masroof.ui.sms

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.baraa.masroof.sms.MatchReason
import com.baraa.masroof.sms.SmsMessage
import com.baraa.masroof.sms.SmsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which subset of messages the UI is currently displaying. */
enum class FilterMode {
    /** Show only messages classified as financial. Default. */
    BANKS_ONLY,

    /** Show every loaded message regardless of classification. */
    ALL,
}

/** UI state for the SMS reader screen. */
data class SmsUiState(
    val hasPermission: Boolean = false,
    val isLoading: Boolean = false,
    val messages: List<SmsMessage> = emptyList(),
    val filterMode: FilterMode = FilterMode.BANKS_ONLY,
    /**
     * Whether to hide the original SMS body text in the list (privacy: only
     * the structured transaction card is shown). When the parser fails on a
     * message, the body is shown regardless — the user needs to see the raw
     * message if we couldn't extract structure from it.
     */
    val hideOriginalBody: Boolean = true,
    val error: String? = null,
) {
    /** Messages after applying the current [filterMode]. */
    val displayedMessages: List<SmsMessage>
        get() = when (filterMode) {
            FilterMode.BANKS_ONLY -> messages.filter { it.matchReason != MatchReason.NONE }
            FilterMode.ALL -> messages
        }
}

/**
 * Owns SMS screen state. Uses [AndroidViewModel] to access [Application] for the
 * [android.content.ContentResolver] without leaking an Activity.
 */
class SmsViewModel(application: Application) : AndroidViewModel(application) {

    private val tag = "SmsViewModel"
    private val repository = SmsRepository(application)

    private val _uiState = MutableStateFlow(SmsUiState())
    val uiState: StateFlow<SmsUiState> = _uiState.asStateFlow()

    /** Update the permission state in response to a system callback. */
    fun onPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(hasPermission = granted) }
        if (granted) {
            refresh()
        }
    }

    /** Switch between bank-only and all-messages display. */
    fun setFilterMode(mode: FilterMode) {
        if (_uiState.value.filterMode == mode) return
        _uiState.update { it.copy(filterMode = mode) }
        Log.d(tag, "filter mode -> $mode")
    }

    /** Toggle whether the original SMS body is hidden in the list. */
    fun setHideOriginalBody(hide: Boolean) {
        if (_uiState.value.hideOriginalBody == hide) return
        _uiState.update { it.copy(hideOriginalBody = hide) }
        Log.d(tag, "hide original body -> $hide")
    }

    /** Re-read messages; safe to call from UI events (refresh button, retry). */
    fun refresh() {
        if (!_uiState.value.hasPermission) {
            Log.d(tag, "refresh() ignored: permission not granted yet")
            return
        }
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val messages = repository.loadInbox()
            _uiState.update { it.copy(isLoading = false, messages = messages) }
        }
    }
}

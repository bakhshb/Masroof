package com.baraa.masroof.ui.sms

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.baraa.masroof.sms.SmsMessage
import com.baraa.masroof.sms.SmsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** UI state for the SMS reader screen. */
data class SmsUiState(
    val hasPermission: Boolean = false,
    val isLoading: Boolean = false,
    val messages: List<SmsMessage> = emptyList(),
    val error: String? = null,
)

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

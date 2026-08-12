package com.baraa.masroof.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.CardReference
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.ownership.OwnershipConfirmationService
import com.baraa.masroof.domain.repository.CardRegistryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val cardRegistryRepository: CardRegistryRepository,
    private val ownershipConfirmationService: OwnershipConfirmationService,
    private val refreshReviewQueue: suspend () -> Unit,
    private val appVersion: String,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState(appVersion = appVersion))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                applyRegistry(cardRegistryRepository.listAll())
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                _uiState.update { it.copy(loading = false, error = SettingsError.UPDATE_FAILED) }
            }
        }
    }

    fun requestStopTracking(card: ManagedCardUi) {
        _uiState.update { it.copy(stopConfirmTarget = card) }
    }

    fun dismissStopConfirm() {
        _uiState.update { it.copy(stopConfirmTarget = null) }
    }

    fun confirmStopTracking() {
        val target = _uiState.value.stopConfirmTarget ?: return
        dismissStopConfirm()
        updateOwnership(target, owned = false)
    }

    fun confirmCardOwned(card: ManagedCardUi) {
        updateOwnership(card, owned = true)
    }

    fun markCardExternal(card: ManagedCardUi) {
        updateOwnership(card, owned = false)
    }

    fun resumeTracking(card: ManagedCardUi) {
        updateOwnership(card, owned = true)
    }

    private fun updateOwnership(card: ManagedCardUi, owned: Boolean) {
        if (_uiState.value.updating) return
        viewModelScope.launch {
            _uiState.update { it.copy(updating = true, error = null) }
            try {
                val ref = CardReference(card.bank, card.last4)
                if (owned) {
                    ownershipConfirmationService.confirmCardOwned(ref)
                } else {
                    ownershipConfirmationService.markCardExternal(ref)
                }
                refreshReviewQueue()
                applyRegistry(cardRegistryRepository.listAll())
                _uiState.update { it.copy(updating = false) }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                _uiState.update { it.copy(updating = false, error = SettingsError.UPDATE_FAILED) }
            }
        }
    }

    private fun applyRegistry(entries: List<com.baraa.masroof.domain.model.CardRegistryEntry>) {
        val cards = entries
            .filter { it.bank != Bank.UNKNOWN }
            .map { ManagedCardUi(bank = it.bank, last4 = it.last4, ownership = it.ownership) }
            .sortedBy { it.last4 }
        _uiState.update {
            it.copy(
                loading = false,
                followedCards = cards.filter { card -> card.ownership == OwnershipStatus.OWNED },
                unregisteredCards = cards.filter { card -> card.ownership == OwnershipStatus.UNKNOWN },
                stoppedCards = cards.filter { card -> card.ownership == OwnershipStatus.EXTERNAL },
                error = null,
            )
        }
    }
}

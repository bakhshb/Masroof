package com.baraa.masroof.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baraa.masroof.application.locale.AppLocale
import com.baraa.masroof.application.locale.AppLocaleRepository
import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.CardReference
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.ownership.OwnershipConfirmationService
import com.baraa.masroof.domain.repository.AccountRegistryRepository
import com.baraa.masroof.domain.repository.CardRegistryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val cardRegistryRepository: CardRegistryRepository,
    private val accountRegistryRepository: AccountRegistryRepository,
    private val ownershipConfirmationService: OwnershipConfirmationService,
    private val appLocaleRepository: AppLocaleRepository,
    private val refreshReviewQueue: suspend () -> Unit,
    private val reparseStoredEvents: suspend () -> Int,
    private val appVersion: String,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        SettingsUiState(
            appVersion = appVersion,
            languageTag = appLocaleRepository.getLanguageTag(),
        ),
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(loading = true, error = null, languageTag = appLocaleRepository.getLanguageTag())
            }
            try {
                applyRegistries(
                    cards = cardRegistryRepository.listAll(),
                    accounts = accountRegistryRepository.listAll(),
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                _uiState.update { it.copy(loading = false, error = SettingsError.UPDATE_FAILED) }
            }
        }
    }

    fun requestStopTracking(card: ManagedCardUi) {
        _uiState.update { it.copy(stopConfirmCardTarget = card, stopConfirmAccountTarget = null) }
    }

    fun requestStopAccountTracking(account: ManagedAccountUi) {
        _uiState.update { it.copy(stopConfirmAccountTarget = account, stopConfirmCardTarget = null) }
    }

    fun dismissStopConfirm() {
        _uiState.update { it.copy(stopConfirmCardTarget = null, stopConfirmAccountTarget = null) }
    }

    fun confirmStopTracking() {
        val target = _uiState.value.stopConfirmCardTarget ?: return
        dismissStopConfirm()
        updateCardOwnership(target, owned = false)
    }

    fun confirmStopAccountTracking() {
        val target = _uiState.value.stopConfirmAccountTarget ?: return
        dismissStopConfirm()
        updateAccountOwnership(target, owned = false)
    }

    fun confirmCardOwned(card: ManagedCardUi) {
        updateCardOwnership(card, owned = true)
    }

    fun markCardExternal(card: ManagedCardUi) {
        updateCardOwnership(card, owned = false)
    }

    fun resumeTracking(card: ManagedCardUi) {
        updateCardOwnership(card, owned = true)
    }

    fun confirmAccountOwned(account: ManagedAccountUi) {
        updateAccountOwnership(account, owned = true)
    }

    fun markAccountExternal(account: ManagedAccountUi) {
        updateAccountOwnership(account, owned = false)
    }

    fun resumeAccountTracking(account: ManagedAccountUi) {
        updateAccountOwnership(account, owned = true)
    }

    fun reparseStoredMessages() {
        if (_uiState.value.reparsingStored || _uiState.value.updating) return
        viewModelScope.launch {
            _uiState.update { it.copy(reparsingStored = true, error = null) }
            try {
                reparseStoredEvents()
                refreshReviewQueue()
                applyRegistries(
                    cards = cardRegistryRepository.listAll(),
                    accounts = accountRegistryRepository.listAll(),
                )
                _uiState.update { it.copy(reparsingStored = false) }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                _uiState.update { it.copy(reparsingStored = false, error = SettingsError.UPDATE_FAILED) }
            }
        }
    }

    fun setLanguageTag(languageTag: String, onApplied: () -> Unit) {
        val normalized = when (languageTag) {
            AppLocale.TAG_EN -> AppLocale.TAG_EN
            else -> AppLocale.TAG_AR
        }
        if (normalized == appLocaleRepository.getLanguageTag()) return
        appLocaleRepository.setLanguageTag(normalized)
        onApplied()
    }

    private fun updateCardOwnership(card: ManagedCardUi, owned: Boolean) {
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
                applyRegistries(
                    cards = cardRegistryRepository.listAll(),
                    accounts = accountRegistryRepository.listAll(),
                )
                _uiState.update { it.copy(updating = false) }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                _uiState.update { it.copy(updating = false, error = SettingsError.UPDATE_FAILED) }
            }
        }
    }

    private fun updateAccountOwnership(account: ManagedAccountUi, owned: Boolean) {
        if (_uiState.value.updating) return
        viewModelScope.launch {
            _uiState.update { it.copy(updating = true, error = null) }
            try {
                val ref = AccountReference(account.bank, account.maskedNumber)
                if (owned) {
                    ownershipConfirmationService.confirmAccountOwned(ref)
                } else {
                    ownershipConfirmationService.markAccountExternal(ref)
                }
                refreshReviewQueue()
                applyRegistries(
                    cards = cardRegistryRepository.listAll(),
                    accounts = accountRegistryRepository.listAll(),
                )
                _uiState.update { it.copy(updating = false) }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                _uiState.update { it.copy(updating = false, error = SettingsError.UPDATE_FAILED) }
            }
        }
    }

    private fun applyRegistries(
        cards: List<com.baraa.masroof.domain.model.CardRegistryEntry>,
        accounts: List<com.baraa.masroof.domain.model.AccountRegistryEntry>,
    ) {
        val cardItems = cards
            .filter { it.bank != Bank.UNKNOWN }
            .map { ManagedCardUi(bank = it.bank, last4 = it.last4, ownership = it.ownership) }
            .sortedBy { it.last4 }
        val accountItems = accounts
            .filter { it.bank != Bank.UNKNOWN }
            .map { ManagedAccountUi(bank = it.bank, maskedNumber = it.maskedNumber, ownership = it.ownership) }
            .sortedBy { it.maskedNumber }
        _uiState.update {
            it.copy(
                loading = false,
                followedCards = cardItems.filter { card -> card.ownership == OwnershipStatus.OWNED },
                unregisteredCards = cardItems.filter { card -> card.ownership == OwnershipStatus.UNKNOWN },
                stoppedCards = cardItems.filter { card -> card.ownership == OwnershipStatus.EXTERNAL },
                followedAccounts = accountItems.filter { account -> account.ownership == OwnershipStatus.OWNED },
                unregisteredAccounts = accountItems.filter { account -> account.ownership == OwnershipStatus.UNKNOWN },
                stoppedAccounts = accountItems.filter { account -> account.ownership == OwnershipStatus.EXTERNAL },
                error = null,
            )
        }
    }
}

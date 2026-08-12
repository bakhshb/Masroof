package com.baraa.masroof.presentation.settings

import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.OwnershipStatus

data class ManagedCardUi(
    val bank: Bank,
    val last4: String,
    val ownership: OwnershipStatus,
)

data class SettingsUiState(
    val loading: Boolean = true,
    val followedCards: List<ManagedCardUi> = emptyList(),
    val unregisteredCards: List<ManagedCardUi> = emptyList(),
    val stoppedCards: List<ManagedCardUi> = emptyList(),
    val appVersion: String = "",
    val languageTag: String = "",
    val updating: Boolean = false,
    val stopConfirmTarget: ManagedCardUi? = null,
    val reparsingStored: Boolean = false,
    val error: SettingsError? = null,
)

enum class SettingsError {
    UPDATE_FAILED,
}

enum class SettingsDestination {
    Hub,
    MyCards,
    About,
}

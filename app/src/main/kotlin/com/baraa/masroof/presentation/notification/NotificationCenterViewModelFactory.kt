package com.baraa.masroof.presentation.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.baraa.masroof.application.AppContainer

class NotificationCenterViewModelFactory(
    private val container: AppContainer,
    private val permissionStateProvider: () -> Boolean,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(NotificationCenterViewModel::class.java))
        return NotificationCenterViewModel(
            notificationCenterMetricsWorkflow = container.notificationCenterMetricsWorkflow,
            notificationCenterService = container.notificationCenterService,
            permissionStateProvider = permissionStateProvider,
        ) as T
    }
}

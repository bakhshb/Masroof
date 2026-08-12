package com.baraa.masroof

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.baraa.masroof.presentation.MasroofRoot
import com.baraa.masroof.presentation.dashboard.DashboardViewModel
import com.baraa.masroof.presentation.dashboard.DashboardViewModelFactory
import com.baraa.masroof.presentation.onboarding.OnboardingPermissionPolicy
import com.baraa.masroof.presentation.onboarding.OnboardingViewModel
import com.baraa.masroof.presentation.onboarding.OnboardingViewModelFactory
import com.baraa.masroof.presentation.review.ReviewViewModel
import com.baraa.masroof.presentation.review.ReviewViewModelFactory
import com.baraa.masroof.presentation.settings.SettingsViewModel
import com.baraa.masroof.presentation.settings.SettingsViewModelFactory
import com.baraa.masroof.presentation.theme.MasroofTheme

/**
 * Launcher: P10 onboarding until complete, then P11 monthly dashboard.
 */
class MainActivity : ComponentActivity() {
    private val container by lazy { (application as MasroofApplication).container }

    private val onboardingViewModel: OnboardingViewModel by viewModels {
        OnboardingViewModelFactory(
            container = container,
            onboardingPreferencesRepository = container.onboardingPreferencesRepository,
            permissionStateProvider = { hasSmsPermissions() },
        )
    }

    private val dashboardViewModel: DashboardViewModel by viewModels {
        DashboardViewModelFactory(container = container)
    }

    private val reviewViewModel: ReviewViewModel by viewModels {
        ReviewViewModelFactory(container = container)
    }

    private val settingsViewModel: SettingsViewModel by viewModels {
        SettingsViewModelFactory(
            container = container,
            appVersion = BuildConfig.VERSION_NAME,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MasroofTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val permissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestMultiplePermissions(),
                    ) { _ ->
                        onboardingViewModel.onPermissionResult(hasSmsPermissions())
                    }

                    val openSettings = {
                        startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", packageName, null),
                            ),
                        )
                    }

                    MasroofRoot(
                        onboardingViewModel = onboardingViewModel,
                        dashboardViewModel = dashboardViewModel,
                        reviewViewModel = reviewViewModel,
                        settingsViewModel = settingsViewModel,
                        onRequestPermissions = {
                            permissionLauncher.launch(OnboardingPermissionPolicy.REQUIRED_SMS_PERMISSIONS)
                        },
                        onOpenAppSettings = openSettings,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        onboardingViewModel.reloadFromCurrentState()
        if (container.onboardingPreferencesRepository.isOnboardingCompleted()) {
            dashboardViewModel.refresh()
        }
    }

    private fun hasSmsPermissions(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
}

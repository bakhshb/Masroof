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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.baraa.masroof.presentation.onboarding.OnboardingPermissionPolicy
import com.baraa.masroof.presentation.onboarding.OnboardingRoute
import com.baraa.masroof.presentation.onboarding.OnboardingViewModel
import com.baraa.masroof.presentation.onboarding.OnboardingViewModelFactory

/**
 * P10 launcher: onboarding + setup completion placeholder.
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
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

                    LaunchedEffect(Unit) {
                        onboardingViewModel.onPermissionResult(hasSmsPermissions())
                    }

                    OnboardingRoute(
                        viewModel = onboardingViewModel,
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
        onboardingViewModel.onPermissionResult(hasSmsPermissions())
        onboardingViewModel.reloadFromCurrentState()
    }

    private fun hasSmsPermissions(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
}

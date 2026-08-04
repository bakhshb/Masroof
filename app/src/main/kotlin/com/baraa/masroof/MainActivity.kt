package com.baraa.masroof

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baraa.masroof.ui.PrimaryNavigation
import com.baraa.masroof.ui.onboarding.OnboardingScreen
import com.baraa.masroof.ui.theme.MasroofTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MasroofTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    val app = application as MasroofApplication
                    var setup by remember { mutableStateOf<com.baraa.masroof.data.repository.FinancialSetup?>(null) }
                    LaunchedEffect(Unit) { setup = app.financialSetupRepository.load() }
                    val current = setup
                    if (current?.setupCompleted != true) {
                        OnboardingScreen(onFinished = { setup = current?.copy(setupCompleted = true, setupCompletedAt = System.currentTimeMillis()) })
                    } else {
                        PrimaryNavigation()
                    }
                }
            }
        }
    }
}
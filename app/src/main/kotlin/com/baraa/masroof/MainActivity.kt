package com.baraa.masroof

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.baraa.masroof.ui.PrimaryNavigation
import com.baraa.masroof.ui.onboarding.OnboardingRepository
import com.baraa.masroof.ui.onboarding.OnboardingScreen
import com.baraa.masroof.ui.onboarding.OnboardingState
import com.baraa.masroof.ui.onboarding.SmsPermissionStore
import com.baraa.masroof.ui.theme.MasroofTheme
import kotlinx.coroutines.launch

/**
 * Single host for the app.
 *
 * The startup sequence is:
 *   1. Read persisted onboarding state from [OnboardingRepository].
 *   2. Show the splash [LoadingScreen] while the read is in flight.
 *   3. Switch the NavHost start destination to either [OnboardingRoute]
 *      or [MainRoute] once the persistent state is known.
 *
 * Crucially, the NavHost is **not** rendered with `OnboardingRoute` as
 * its temporary start destination — that produced the previous bug
 * where onboarding flashed on every launch.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as MasroofApplication
        // Refresh permission state on resume so users returning from
        // Android Settings immediately see the updated permission UI.
        // The SmsPermissionStore is the single source of truth for
        // OS-level READ_SMS state.
        app.smsPermissionStore.refresh()
        setContent {
            MasroofTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    val navController = rememberNavController()
                    val onboardingState by app.onboardingRepository.observe().collectAsState(initial = OnboardingState.Loading)

                    val startDestination = when (onboardingState) {
                        is OnboardingState.Loading -> LoadingRoute
                        is OnboardingState.Pending -> OnboardingRoute
                        is OnboardingState.Completed -> MainRoute
                    }

                    NavHost(navController = navController, startDestination = startDestination) {
                        composable(LoadingRoute) { LoadingScreen() }
                        composable(OnboardingRoute) {
                            val scope = androidx.compose.runtime.rememberCoroutineScope()
                            OnboardingScreen(
                                repository = app.onboardingRepository,
                                permissionStore = app.smsPermissionStore,
                                onStepCompleted = { step ->
                                    scope.launch { app.onboardingRepository.markStepCompleted(step) }
                                },
                                onFinished = {
                                    navController.navigate(MainRoute) {
                                        popUpTo(OnboardingRoute) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                },
                            )
                        }
                        composable(MainRoute) {
                            // Ensure the system back stack can never return to
                            // the onboarding route once we leave it.
                            disableOnboardingBackstackEntry(navController, OnboardingRoute, LoadingRoute)
                            PrimaryNavigation()
                        }
                    }
                }
            }
        }
    }

    private fun disableOnboardingBackstackEntry(navController: NavHostController, vararg routesToStrip: String) {
        navController.previousBackStackEntry?.destination?.route?.let { route ->
            if (routesToStrip.contains(route)) {
                navController.popBackStack(route, inclusive = true)
            }
        }
    }

    companion object {
        const val OnboardingRoute: String = "route/onboarding"
        const val MainRoute: String = "route/main"
        const val LoadingRoute: String = "route/loading"
    }
}

@Composable
private fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
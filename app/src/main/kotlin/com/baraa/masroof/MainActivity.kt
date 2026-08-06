package com.baraa.masroof

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import com.baraa.masroof.ui.MissingAccountRecoveryScreen
import com.baraa.masroof.ui.PrimaryNavigation
import com.baraa.masroof.ui.onboarding.OnboardingScreen
import com.baraa.masroof.ui.onboarding.OnboardingState
import com.baraa.masroof.ui.theme.MasroofTheme
import com.baraa.masroof.ui.theme.ThemePreference
import kotlinx.coroutines.launch

/**
 * Single host for the app.
 *
 * Startup sequence:
 *   1. Read persisted onboarding state from [OnboardingRepository].
 *   2. Show the splash [LoadingScreen] while the read is in flight.
 *   3. When onboarding is Completed, ALSO verify that at least one
 *      account exists. If onboardingCompleted=true but no account row
 *      exists, render the [MissingAccountRecoveryScreen] so the user
 *      is never silently dropped into an empty app.
 *   4. Otherwise switch the NavHost start destination to either
 *      [OnboardingRoute], [MainRoute], or [RecoveryRoute].
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as MasroofApplication
        // Refresh permission state on resume so users returning from
        // Android Settings immediately see the updated permission UI.
        app.smsPermissionStore.refresh()
        setContent {
            val themePreference by app.themePreferenceRepository.observe()
                .collectAsState(initial = app.themePreferenceRepository.snapshot())
            val systemDark = isSystemInDarkTheme()
            val useDarkTheme = when (themePreference) {
                ThemePreference.SYSTEM -> systemDark
                ThemePreference.LIGHT -> false
                ThemePreference.DARK -> true
            }
            MasroofTheme(darkTheme = useDarkTheme) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    val navController = rememberNavController()
                    val onboardingState by app.onboardingRepository.observe().collectAsState(initial = OnboardingState.Loading)

                    /**
                     * Whether the persisted state is inconsistent: onboarding
                     * is Completed but no account row exists. This is a
                     * recovery state that the user must resolve by either
                     * creating an account or wiping the onboarding flag.
                     */
                    val accounts by app.financialAccountRepository.observeAll().collectAsState(initial = emptyList())
                    val needsRecovery = onboardingState is OnboardingState.Completed && accounts.isEmpty()

                    val startDestination = when {
                        onboardingState is OnboardingState.Loading -> LoadingRoute
                        needsRecovery -> RecoveryRoute
                        onboardingState is OnboardingState.Pending -> OnboardingRoute
                        else -> MainRoute
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
                        composable(RecoveryRoute) {
                            val scope = androidx.compose.runtime.rememberCoroutineScope()
                            MissingAccountRecoveryScreen(
                                onCreateAccount = {
                                    // Mark onboarding as not completed so the flow
                                    // re-runs, allowing the user to create the account.
                                    scope.launch {
                                        app.onboardingRepository.resetOnboarding()
                                        navController.navigate(OnboardingRoute) {
                                            popUpTo(RecoveryRoute) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    }
                                },
                                onRestoreOnboarding = {
                                    scope.launch {
                                        app.onboardingRepository.resetOnboarding()
                                        navController.navigate(OnboardingRoute) {
                                            popUpTo(RecoveryRoute) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    }
                                },
                            )
                        }
                        composable(MainRoute) {
                            disableOnboardingBackstackEntry(navController, OnboardingRoute, LoadingRoute, RecoveryRoute)
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
        const val RecoveryRoute: String = "route/recovery"
    }
}

@Composable
private fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

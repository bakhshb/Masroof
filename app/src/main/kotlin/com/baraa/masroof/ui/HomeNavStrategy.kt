package com.baraa.masroof.ui

/**
 * Pure contract for how bottom-nav / top-bar Home must leave Import or Review.
 * Compose Navigation's navigate(HOME)+launchSingleTop+restoreState is a no-op
 * when HOME is already under the child route; callers must popBackStack instead.
 */
data class HomeNavStrategy(
    val targetRoute: String,
    val action: Action,
    val inclusive: Boolean,
) {
    enum class Action { POP_BACK_TO_HOME, NAVIGATE_HOME }

    companion object {
        val fromImportOrReview = HomeNavStrategy(
            targetRoute = AppRoutes.HOME,
            action = Action.POP_BACK_TO_HOME,
            inclusive = false,
        )
    }
}

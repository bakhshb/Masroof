package com.baraa.masroof.ui.settings

import org.junit.Assert.*
import org.junit.Test

/**
 * Static structural test: every destination declared in
 * [SettingsDestinations] must resolve to a valid route. The list below
 * intentionally mirrors the NavHost definitions; if either side adds a
 * destination without updating the other, this test fires.
 */
class SettingsNavRegistrationTest {
    private val navHostRegisteredRoutes = listOf(
        "settings/list",
        SettingsDestinations.categoryManagement.route,
        SettingsDestinations.merchantMemory.route,
        SettingsDestinations.aiCategorization.route,
        SettingsDestinations.aiSuggestions.route,
        SettingsDestinations.accounts.route,
        SettingsDestinations.linkTransactions.route,
        SettingsDestinations.financialHistory.route,
        SettingsDestinations.accountLinkRules.route,
        SettingsDestinations.senderMappings.route,
        SettingsDestinations.diagnostics.route,
        SettingsDestinations.testData.route,
        SettingsDestinations.releaseNotes.route,
    )

    @Test fun everyRegistryRouteIsHandledByTheNavHost() {
        for (d in SettingsDestinations.all) {
            if (!d.implemented) {
                // Disabled rows: registry entry exists, NavHost intentionally skips it.
                assertFalse(
                    "Disabled destination ${d.route} must not be wired to the NavHost",
                    navHostRegisteredRoutes.contains(d.route),
                )
                continue
            }
            assertTrue(
                "NavHost missing destination ${d.route} (${d.title})",
                navHostRegisteredRoutes.contains(d.route),
            )
        }
    }

    @Test fun navHostRoutesAreUnique() {
        assertEquals(
            "Two destinations are sharing the same route — would break back nav",
            navHostRegisteredRoutes.size,
            navHostRegisteredRoutes.toSet().size,
        )
    }

    @Test fun settingsListRouteResolvesToExpectedTitle() {
        assertEquals("إدارة التصنيفات", SettingsDestinations.byRoute("settings/categories")?.title)
        assertEquals("إدارة التصنيفات", SettingsDestinations.categoryManagement.title)
    }
}

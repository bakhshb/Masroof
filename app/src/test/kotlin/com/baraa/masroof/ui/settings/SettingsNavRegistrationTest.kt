package com.baraa.masroof.ui.settings

import org.junit.Assert.*
import org.junit.Test

/**
 * Wiring audit: every destination in [SettingsDestinations] that is
 * marked `implemented = true` must be reachable from the PrimaryNavHost.
 *
 * The PrimaryNavHost route table below mirrors the actual NavHost
 * definition in PrimaryNavigation.kt.
 */
class SettingsNavRegistrationTest {
    private val navHostRegisteredRoutes = listOf(
        SettingsDestinations.categoryManagement.route,
        SettingsDestinations.merchantMemory.route,
        SettingsDestinations.aiCategorization.route,
        SettingsDestinations.aiSuggestions.route,
        SettingsDestinations.accounts.route,
        SettingsDestinations.linkTransactions.route,
        SettingsDestinations.financialHistory.route,
        SettingsDestinations.accountLinkRules.route,
        SettingsDestinations.senderMappings.route,
        SettingsDestinations.bankMessages.route,
        SettingsDestinations.diagnostics.route,
        SettingsDestinations.testData.route,
        SettingsDestinations.releaseNotes.route,
        SettingsDestinations.autoSmsImport.route,
        SettingsDestinations.transactionNotifications.route
    )

    @Test fun everyImplementedRegistryRouteIsWiredInNavHost() {
        for (d in SettingsDestinations.all) {
            if (!d.implemented) continue
            assertTrue(
                "NavHost missing destination ${d.route} (${d.title})",
                navHostRegisteredRoutes.contains(d.route)
            )
        }
    }

    @Test fun navHostRoutesAreUnique() {
        assertEquals(
            "Two destinations are sharing the same route — would break back nav",
            navHostRegisteredRoutes.size,
            navHostRegisteredRoutes.toSet().size
        )
    }

    @Test fun settingsRowsByGroupAreWired() {
        // Each group must have at least one row in the NavHost.
        for (group in SettingsGroup.values()) {
            val groupRows = SettingsDestinations.all.filter { it.group == group && it.implemented }
            for (row in groupRows) {
                assertTrue(
                    "Group ${group.header}: row ${row.title} missing from NavHost",
                    navHostRegisteredRoutes.contains(row.route)
                )
            }
        }
    }

    @Test fun everySettingsRowHasNonEmptyTitle() {
        for (d in SettingsDestinations.all) {
            assertTrue("Empty title for ${d.route}", d.title.isNotBlank())
        }
    }
}
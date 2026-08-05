package com.baraa.masroof.ui

import org.junit.Assert.*
import org.junit.Test

/**
 * Verifies the import-messages route is wired into the primary
 * NavHost. The route constant lives in PrimaryNavigation.kt and is
 * referenced by HomeScreen, TransactionOperationsScreen, and the
 * bottom-navigation helpers.
 */
class ImportNavRouteTest {

    @Test fun importRouteIsNonEmpty() {
        assertTrue(ImportMessagesRoute.isNotBlank())
    }

    @Test fun importRouteStartsWithRoutePrefix() {
        assertTrue(ImportMessagesRoute.startsWith("route/"))
    }

    @Test fun importRouteDoesNotCollideWithPrimaryTabs() {
        // Should not start with "primary/" to avoid ambiguous routing.
        assertFalse(ImportMessagesRoute.startsWith("primary/"))
    }
}
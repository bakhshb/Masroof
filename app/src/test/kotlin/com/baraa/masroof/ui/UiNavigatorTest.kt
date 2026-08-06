package com.baraa.masroof.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class UiNavigatorTest {
    @Test fun homeFromImportResolvesToRootHome() = assertEquals(AppRoutes.HOME, NavigationCommand.OpenHome.destinationRoute())
    @Test fun homeFromResultsResolvesToRootHome() = assertEquals(AppRoutes.HOME, NavigationCommand.OpenHome.destinationRoute())
    @Test fun homeFromReviewResolvesToRootHome() = assertEquals(AppRoutes.HOME, NavigationCommand.OpenHome.destinationRoute())
    @Test fun reviewCarriesSessionIdInRoute() = assertEquals("operations/review?sessionId=42", NavigationCommand.OpenReviewQueue(42).destinationRoute())
    @Test fun importUsesOperationsChildRoute() = assertEquals(AppRoutes.IMPORT, NavigationCommand.OpenImport.destinationRoute())
    @Test fun backFromImportReturnsOperations() = assertEquals(AppRoutes.OPERATIONS, NavigationCommand.BackToOperations.destinationRoute())
}

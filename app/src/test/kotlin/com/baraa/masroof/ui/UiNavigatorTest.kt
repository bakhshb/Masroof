package com.baraa.masroof.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class UiNavigatorTest {
    @Test fun homeFromImportResolvesToRootHome() = assertEquals(AppRoutes.HOME, NavigationCommand.OpenHome.destinationRoute())
    @Test fun homeFromResultsResolvesToRootHome() = assertEquals(AppRoutes.HOME, NavigationCommand.OpenHome.destinationRoute())
    @Test fun homeFromReviewResolvesToRootHome() = assertEquals(AppRoutes.HOME, NavigationCommand.OpenHome.destinationRoute())
    @Test fun reviewCarriesSessionIdInRoute() = assertEquals(AppRoutes.REVIEW, NavigationCommand.OpenReviewQueue(42).destinationRoute())
    @Test fun reviewWithoutSessionUsesBaseRoute() = assertEquals(AppRoutes.REVIEW, NavigationCommand.OpenReviewQueue().destinationRoute())
    @Test fun importUsesOperationsChildRoute() = assertEquals(AppRoutes.IMPORT, NavigationCommand.OpenImport.destinationRoute())
    @Test fun backFromImportReturnsOperations() = assertEquals(AppRoutes.OPERATIONS, NavigationCommand.BackToOperations.destinationRoute())
    @Test fun bindAccountRouteProducesTypeSafePath() = assertEquals("operations/account-bind/9", NavigationCommand.BindAccountFromSms(9).destinationRoute())
}

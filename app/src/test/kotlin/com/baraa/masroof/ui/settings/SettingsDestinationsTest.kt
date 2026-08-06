package com.baraa.masroof.ui.settings

import org.junit.Assert.*
import org.junit.Test

/**
 * Pure-data tests for [SettingsDestinations]. These verify the registry
 * that backs the actual NavHost.
 */
class SettingsDestinationsTest {
    @Test fun registryCoversEveryRowPreviouslyListedInUi() {
        val expected = listOf(
            "إدارة التصنيفات",
            "التجار المحفوظون",
            "التصنيف الذكي",
            "اقتراحات التصنيف",
            "تصنيف العمليات غير المصنفة",
            "الحسابات",
            "ربط العمليات بالحسابات",
            "السجل المالي",
            "قواعد الربط المحفوظة",
            "مرسلو الرسائل والمؤسسات",
            "تشخيص التطبيق",
            "رسائل تجريبية",
            "ملاحظات الإصدار"
        )
        val titles = SettingsDestinations.all.map { it.title }
        for (e in expected) assertTrue("missing row: $e", titles.contains(e))
    }

    @Test fun everyRouteIsUnique() {
        val routes = SettingsDestinations.all.map { it.route }
        assertEquals(routes.size, routes.toSet().size)
    }

    @Test fun everyGroupHeaderMatchesSpec() {
        assertEquals("التصنيفات", SettingsGroup.Categories.header)
        assertEquals("الحسابات والربط", SettingsGroup.AccountsAndLinking.header)
        assertEquals("الدعم والتشخيص", SettingsGroup.Diagnostics.header)
    }

    @Test fun everyDestinationResolvesByRoute() {
        for (d in SettingsDestinations.all) {
            assertSame(d, SettingsDestinations.byRoute(d.route))
        }
    }

    @Test fun unknownRouteReturnsNull() {
        assertNull(SettingsDestinations.byRoute("nonsense"))
    }

    @Test fun currentImplementationFlagsAreCorrect() {
        // Categories group: implemented
        assertTrue(SettingsDestinations.categoryManagement.implemented)
        assertTrue(SettingsDestinations.merchantMemory.implemented)
        assertTrue(SettingsDestinations.accounts.implemented)
        assertTrue(SettingsDestinations.financialHistory.implemented)
    }
}

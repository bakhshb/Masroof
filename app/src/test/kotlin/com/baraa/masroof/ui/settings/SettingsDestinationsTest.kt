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

    @Test fun landingShowsOnlyAppPreferences() {
        val landingTitles = SettingsDestinations.landing.map { it.title }
        assertTrue(landingTitles.contains("استيراد رسائل البنك تلقائياً"))
        assertTrue(landingTitles.contains("إشعار عند تسجيل عملية جديدة"))
        assertTrue(landingTitles.contains("تشخيص التطبيق"))
        assertFalse("Accounts must stay on More, not Settings landing", landingTitles.contains("الحسابات"))
        assertFalse("Categories must stay on More, not Settings landing", landingTitles.contains("إدارة التصنيفات"))
        assertFalse("Financial history must stay on More", landingTitles.contains("السجل المالي"))
        assertEquals(
            setOf(SettingsGroup.Messages, SettingsGroup.Diagnostics),
            SettingsDestinations.landing.map { it.group }.toSet(),
        )
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

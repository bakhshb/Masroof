package com.baraa.masroof.presentation.dashboard

import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.R
import com.baraa.masroof.application.locale.AppLocale
import com.baraa.masroof.presentation.locale.AppLocaleContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class DashboardRedesignResourcesTest {
    @Test
    fun customizeStrings_englishAreTranslated() {
        val context = AppLocaleContext.wrap(
            ApplicationProvider.getApplicationContext(),
            AppLocale.TAG_EN,
        )
        assertEquals("Customize", context.getString(R.string.dashboard_customize))
        assertEquals("Customize home screen", context.getString(R.string.dashboard_customize_sheet_title))
        assertEquals("Save layout", context.getString(R.string.dashboard_customize_save))
        assertEquals("Hero card", context.getString(R.string.dashboard_customize_section_hero))
        assertEquals("Small", context.getString(R.string.dashboard_customize_size_small))
        assertEquals("Spending details", context.getString(R.string.dashboard_expense_details_title))
        assertEquals("Income details", context.getString(R.string.dashboard_income_details_title))
        assertEquals("View all ›", context.getString(R.string.dashboard_view_all))
        assertEquals("Accounts summary", context.getString(R.string.dashboard_accounts_summary_screen_title))
        assertEquals("Cards summary", context.getString(R.string.dashboard_cards_summary_screen_title))
        assertEquals("Manage accounts", context.getString(R.string.dashboard_manage_accounts))
        assertEquals("Manage cards", context.getString(R.string.dashboard_manage_cards))
        assertEquals("3 accounts", context.getString(R.string.dashboard_accounts_count_label, 3))
        assertEquals("2 cards", context.getString(R.string.dashboard_cards_count_label, 2))
        assertEquals(
            "Period transactions (4)",
            context.getString(R.string.dashboard_summary_transactions_title, 4),
        )
        assertEquals(
            "No period data for this account",
            context.getString(R.string.dashboard_account_detail_unavailable),
        )
    }

    @Config(qualifiers = "ar")
    @Test
    fun customizeStrings_arabicUseDefaultResourceBucket() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals("تخصيص", context.getString(R.string.dashboard_customize))
        assertEquals("تخصيص الشاشة الرئيسية", context.getString(R.string.dashboard_customize_sheet_title))
        assertEquals("تفاصيل المنصرف", context.getString(R.string.dashboard_expense_details_title))
    }

    @Test
    fun flowDetailStrings_englishTotalsUseCorrectWording() {
        val context = AppLocaleContext.wrap(
            ApplicationProvider.getApplicationContext(),
            AppLocale.TAG_EN,
        )
        assertEquals(
            "Total spent: 100.00 SAR",
            context.getString(R.string.dashboard_flow_detail_expense_total, "100.00 SAR"),
        )
        assertEquals(
            "Total income: 500.00 SAR",
            context.getString(R.string.dashboard_flow_detail_income_total, "500.00 SAR"),
        )
    }

    @Test
    fun englishLocale_isLtr() {
        assertFalse(AppLocale.isRtl(AppLocale.TAG_EN))
        assertTrue(AppLocale.isRtl(AppLocale.TAG_AR))
    }
}

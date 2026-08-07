package com.baraa.masroof.ui.onboarding

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Source contracts: custom start date must use the shared calendar picker. */
class OnboardingDatePickerContractTest {
    @Test
    fun startDateCustomUsesCalendarDateField() {
        val source = File("src/main/kotlin/com/baraa/masroof/ui/onboarding/OnboardingScreen.kt").readText()
        assertTrue(
            "StartDateStep CUSTOM must use CalendarDateField",
            source.contains("CalendarDateField"),
        )
        assertFalse(
            "CUSTOM date must not be a disabled typed YYYY-MM-DD field",
            source.contains("قابل للتعديل بعد الإعداد"),
        )
    }

    @Test
    fun accountEditUsesCalendarDateField() {
        val source = File("src/main/kotlin/com/baraa/masroof/ui/accounts/AccountEditDialog.kt").readText()
        assertTrue(source.contains("CalendarDateField"))
        assertFalse(source.contains("YYYY-MM-DD"))
    }

    @Test
    fun settingsScreenIsScrollable() {
        val source = File("src/main/kotlin/com/baraa/masroof/ui/settings/SettingsScreen.kt").readText()
        assertTrue(source.contains("verticalScroll"))
        assertTrue(source.contains("navigationBars"))
        assertTrue(source.contains("SettingsDestinations.landing"))
    }
}

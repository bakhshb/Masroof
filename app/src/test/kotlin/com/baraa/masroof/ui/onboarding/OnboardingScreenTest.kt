package com.baraa.masroof.ui.onboarding

import androidx.compose.runtime.saveable.SaverScope
import com.baraa.masroof.transaction.AccountType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OnboardingScreenTest {
    @Test
    fun onboardingContainsOnlyFourAccountFirstSteps() {
        assertEquals(
            listOf(
                OnboardingStep.WELCOME,
                OnboardingStep.ACCOUNT,
                OnboardingStep.SELECT_SENDER,
                OnboardingStep.COMPLETION,
            ),
            OnboardingStep.entries,
        )
    }

    @Test
    fun saverRestoresAccountAndSenderWithoutCreatingAnotherAccount() {
        val original = UiOnboardingState().apply {
            step = OnboardingStep.SELECT_SENDER
            displayName = "حساب يومي"
            accountType = AccountType.BANK_ACCOUNT
            openingBalance = "125.50"
            createdAccountId = 99L
            selectedSenderProfileId = 42L
            selectedSenderDisplay = "البنك"
        }
        val saved = with(OnboardingSaver) { SaverScope { true }.save(original) }
        val restored = requireNotNull(OnboardingSaver.restore(requireNotNull(saved)))

        assertEquals(OnboardingStep.SELECT_SENDER, restored.step)
        assertEquals(99L, restored.createdAccountId)
        assertEquals(42L, restored.selectedSenderProfileId)
        assertEquals("حساب يومي", restored.displayName)
    }

    @Test
    fun onboardingNavigationNeverCallsPatternEngineOrImportPipeline() {
        val source = File(
            "src/main/kotlin/com/baraa/masroof/ui/onboarding/OnboardingScreen.kt",
        ).readText()
        val sender = File(
            "src/main/kotlin/com/baraa/masroof/ui/onboarding/OnboardingSenderStep.kt",
        ).readText()
        assertFalse(source.contains("MessageTemplateEngine"))
        assertFalse(source.contains("PatternDiscoveryService"))
        assertFalse(source.contains("importOrchestrator"))
        assertFalse(sender.contains("MessageTemplateEngine"))
        assertFalse(sender.contains("PatternDiscoveryService"))
        assertTrue(sender.contains("upsertFromSmsSender"))
        assertTrue(sender.contains("associateAccount"))
    }

    @Test
    fun permissionDenialHasDeferredSenderPath() {
        val source = File(
            "src/main/kotlin/com/baraa/masroof/ui/onboarding/OnboardingSenderStep.kt",
        ).readText()
        assertTrue(source.contains("ربط المرسل لاحقاً"))
        assertTrue(source.contains("permissionGranted"))
    }

    @Test
    fun completionRequiresPersistedAccountBeforeMarkingCompleted() {
        val source = File(
            "src/main/kotlin/com/baraa/masroof/ui/onboarding/MinimalOnboardingPersistence.kt",
        ).readText()
        val accountCheck = source.indexOf("accountExists(accountId)")
        val setupSave = source.indexOf("saveFinancialSetup()")
        val completed = source.indexOf("markCompleted()")
        assertTrue(accountCheck >= 0)
        assertTrue(setupSave > accountCheck)
        assertTrue(completed > setupSave)
    }

    @Test
    fun otpBodiesAreNeverOfferedAsFirstRunPatternCandidates() {
        val onboardingDirectory = File("src/main/kotlin/com/baraa/masroof/ui/onboarding")
        val sources = onboardingDirectory.listFiles()
            .orEmpty()
            .filter { it.extension == "kt" }
            .joinToString("\n") { it.readText() }
        assertFalse(sources.contains("buildFromSms("))
        assertFalse(sources.contains("CreatePatternStep"))
    }

    @Test
    fun importWithoutApprovedPatternsShowsBankMessagesCallToAction() {
        val source = File(
            "src/main/kotlin/com/baraa/masroof/ui/senders/ImportMessagesScreen.kt",
        ).readText()
        assertTrue(source.contains("يجب مراجعة أنماط رسائل البنك قبل الاستيراد"))
        assertTrue(source.contains("\"مراجعة رسائل البنك\""))
    }
}

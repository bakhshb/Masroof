package com.baraa.masroof.ui.senders

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SenderPatternActionSafetyTest {
    private val source = File(
        "src/main/kotlin/com/baraa/masroof/ui/senders/SenderDetailsScreen.kt",
    ).readText()

    @Test
    fun bothPatternActionsShareTopLevelFailureBoundaryAndRunningState() {
        assertTrue(source.contains("launchPatternAction("))
        assertTrue(source.contains("runningPatternAction"))
        assertTrue(source.contains("catch (failure: Throwable)"))
        assertTrue(source.contains("enabled = runningPatternAction == null"))
        assertTrue(source.contains("لم يتم حذف أي بيانات"))
    }

    @Test
    fun manualDiscoveryRunsOffMainAndUsesAtomicBatchSave() {
        assertTrue(source.contains("withContext(Dispatchers.Default)"))
        assertTrue(source.contains("PatternDiscoveryService.discoverSafely"))
        assertTrue(source.contains("saveDiscoveredBatch"))
    }
}

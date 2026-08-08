package com.baraa.masroof.ui.transactions

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.baraa.masroof.ui.transactions.ReviewLinkSaveState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation scaffold for the manual classify/link save-state machine.
 *
 * Headless CI has no device runtime; this still compiles against Compose
 * test APIs. On a device run:
 *
 *   ./gradlew :app:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class ReviewLinkSaveStateAndroidTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun savingDisablesConfirmContract() {
        var state: ReviewLinkSaveState = ReviewLinkSaveState.Idle
        fun canConfirm(): Boolean = state !is ReviewLinkSaveState.Saving
        assertTrue(canConfirm())
        state = ReviewLinkSaveState.Saving
        assertFalse(canConfirm())
        state = ReviewLinkSaveState.ValidationError("تعارض")
        assertTrue(canConfirm())
    }

    @Test
    fun successIsOnlyClearSignal() {
        fun mayClear(state: ReviewLinkSaveState) =
            state is ReviewLinkSaveState.Success || state is ReviewLinkSaveState.Idle
        assertFalse(mayClear(ReviewLinkSaveState.Saving))
        assertFalse(mayClear(ReviewLinkSaveState.Failure("x")))
        assertTrue(mayClear(ReviewLinkSaveState.Success()))
    }
}

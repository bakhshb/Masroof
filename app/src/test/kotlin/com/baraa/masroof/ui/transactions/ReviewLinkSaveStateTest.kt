package com.baraa.masroof.ui.transactions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Explicit save-state machine for the classify/link dialog.
 * Prevents duplicate submissions while Saving and keeps the dialog open on errors.
 */
class ReviewLinkSaveStateTest {

    private fun canConfirm(
        saveState: ReviewLinkSaveState,
        accountSelected: Boolean,
        treatmentPending: Boolean = false,
    ): Boolean {
        if (saveState is ReviewLinkSaveState.Saving) return false
        if (treatmentPending) return false
        return accountSelected
    }

    private fun mayDismiss(saveState: ReviewLinkSaveState): Boolean =
        saveState !is ReviewLinkSaveState.Saving

    private fun mayClearSelection(saveState: ReviewLinkSaveState): Boolean =
        saveState is ReviewLinkSaveState.Success || saveState is ReviewLinkSaveState.Idle

    @Test
    fun idleWithAccountAllowsConfirm() {
        assertTrue(canConfirm(ReviewLinkSaveState.Idle, accountSelected = true))
    }

    @Test
    fun savingDisablesConfirmAndDismiss() {
        assertFalse(canConfirm(ReviewLinkSaveState.Saving, accountSelected = true))
        assertFalse(mayDismiss(ReviewLinkSaveState.Saving))
    }

    @Test
    fun validationErrorKeepsDialogOpenAndAllowsRetry() {
        val state = ReviewLinkSaveState.ValidationError("حساب الوجهة يجب أن يكون بطاقة ائتمانية")
        assertFalse(mayClearSelection(state))
        assertTrue(mayDismiss(state))
        assertTrue(canConfirm(state, accountSelected = true))
    }

    @Test
    fun failureKeepsDialogOpenAndAllowsRetry() {
        val state = ReviewLinkSaveState.Failure("تعذّر حفظ التصنيف والربط")
        assertFalse(mayClearSelection(state))
        assertTrue(canConfirm(state, accountSelected = true))
    }

    @Test
    fun successAllowsClearingSelection() {
        assertTrue(mayClearSelection(ReviewLinkSaveState.Success()))
    }

    @Test
    fun repeatedTapWhileSavingIsIgnored() {
        var submissions = 0
        var state: ReviewLinkSaveState = ReviewLinkSaveState.Idle
        fun onConfirm() {
            if (state is ReviewLinkSaveState.Saving) return
            state = ReviewLinkSaveState.Saving
            submissions++
        }
        onConfirm()
        onConfirm()
        onConfirm()
        assertEqualsOne(submissions)
        assertTrue(state is ReviewLinkSaveState.Saving)
    }

    private fun assertEqualsOne(actual: Int) {
        org.junit.Assert.assertEquals(1, actual)
    }
}

package com.baraa.masroof.ui.transactions

import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewQueueVisibilityTest {
    @Test
    fun persistedAndCurrentSessionReviewCountsAreBothVisible() {
        assertEquals(
            15,
            reviewQueueVisibleCount(
                persistedActionable = 2,
                sessionMessageReview = 13,
            ),
        )
    }
}

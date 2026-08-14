package com.baraa.masroof.presentation.dashboard

import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.domain.model.Bank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OwnedAccountsBadgeFormatterTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun empty_returnsNull() {
        assertNull(formatOwnedAccountsBadge(emptyList(), context))
    }

    @Test
    fun single_showsMaskedNumber() {
        val badge = formatOwnedAccountsBadge(
            listOf(OwnedAccountUi(Bank.BANK_ALJAZIRA, "3001")),
            context,
        )
        assertEquals("···3001", badge)
    }

    @Test
    fun two_showsBothMaskedNumbers() {
        val badge = formatOwnedAccountsBadge(
            listOf(
                OwnedAccountUi(Bank.BANK_ALJAZIRA, "3001"),
                OwnedAccountUi(Bank.BANK_ALJAZIRA, "6810"),
            ),
            context,
        )
        assertEquals("···3001 ···6810", badge)
    }
}

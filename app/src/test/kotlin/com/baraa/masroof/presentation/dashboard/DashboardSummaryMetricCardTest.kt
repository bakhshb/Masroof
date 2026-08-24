package com.baraa.masroof.presentation.dashboard

import com.baraa.masroof.application.dashboard.SignedMoneyAmount
import com.baraa.masroof.core.money.Currency
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class DashboardSummaryMetricCardTest {
    @Test
    fun spendingMetricTone_positiveIsOutflow() {
        val amount = SignedMoneyAmount(BigDecimal("100.00"), Currency.SAR)
        assertEquals(DashboardMetricTone.Outflow, spendingMetricTone(amount))
    }

    @Test
    fun spendingMetricTone_negativeIsInflow() {
        val amount = SignedMoneyAmount(BigDecimal("-50.00"), Currency.SAR)
        assertEquals(DashboardMetricTone.Inflow, spendingMetricTone(amount))
    }

    @Test
    fun signedAmountMetricTone_positiveIsInflow() {
        assertEquals(DashboardMetricTone.Inflow, signedAmountMetricTone(BigDecimal("1.00")))
    }

    @Test
    fun signedAmountMetricTone_negativeIsOutflow() {
        assertEquals(DashboardMetricTone.Outflow, signedAmountMetricTone(BigDecimal("-1.00")))
    }
}

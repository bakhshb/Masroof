package com.baraa.masroof.application.dashboard

import com.baraa.masroof.domain.period.FinancialPeriod
import com.baraa.masroof.domain.period.SalaryCycleStartAdjustment

/** Presentation-facing salary period type (domain [FinancialPeriod]). */
typealias DashboardSalaryPeriod = FinancialPeriod

/** Presentation-facing salary-cycle adjustment hint (domain enum). */
typealias DashboardSalaryCycleAdjustment = SalaryCycleStartAdjustment

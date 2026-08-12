package com.baraa.masroof.domain.period

/**
 * Why a salary-cycle start differs from the nominal day 27.
 */
enum class SalaryCycleStartAdjustment {
    /** Cycle starts on the 26th because the 27th falls on Friday. */
    EARLY_FOR_FRIDAY,

    /** Cycle starts on the 28th because the 27th falls on Saturday. */
    LATE_FOR_SATURDAY,
}

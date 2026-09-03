package com.baraa.masroof.application.commitment

import com.baraa.masroof.domain.model.Commitment
import com.baraa.masroof.domain.model.CommitmentPauseInterval
import com.baraa.masroof.domain.period.FinancialPeriod
import com.baraa.masroof.domain.period.FinancialPeriodPolicy
import java.time.Instant
import java.time.ZoneId

object CommitmentPauseTransitions {
    fun pause(commitment: Commitment, now: Instant): Commitment {
        if (!commitment.active) return commitment
        return commitment.copy(
            active = false,
            pauseIntervals = commitment.pauseIntervals + CommitmentPauseInterval(pausedAt = now, resumedAt = null),
            updatedAt = now,
        )
    }

    fun resume(commitment: Commitment, now: Instant): Commitment {
        if (commitment.active) return commitment
        val last = commitment.pauseIntervals.lastOrNull()
            ?: return commitment.copy(active = true, updatedAt = now)
        if (last.resumedAt != null) {
            return commitment.copy(active = true, updatedAt = now)
        }
        val closedIntervals = commitment.pauseIntervals.dropLast(1) +
            last.copy(resumedAt = now)
        return commitment.copy(
            active = true,
            pauseIntervals = closedIntervals,
            updatedAt = now,
        )
    }

    fun toggle(commitment: Commitment, now: Instant): Commitment =
        if (commitment.active) pause(commitment, now) else resume(commitment, now)

    fun effectivePauseIntervals(commitment: Commitment): List<CommitmentPauseInterval> {
        if (commitment.pauseIntervals.isNotEmpty()) return commitment.pauseIntervals
        if (!commitment.active) {
            return listOf(CommitmentPauseInterval(pausedAt = commitment.updatedAt, resumedAt = null))
        }
        return emptyList()
    }

    fun isVisibleInSalaryPeriod(
        commitment: Commitment,
        salaryPeriod: FinancialPeriod,
        zoneId: ZoneId,
    ): Boolean {
        val anchorPeriod = FinancialPeriodPolicy.periodContaining(commitment.transactionDate)
        if (!salaryPeriod.endDateExclusive.isAfter(anchorPeriod.startDate)) return false

        return effectivePauseIntervals(commitment).none { interval ->
            salaryPeriodOverlapsPause(salaryPeriod, interval, zoneId)
        }
    }

    private fun salaryPeriodOverlapsPause(
        salaryPeriod: FinancialPeriod,
        interval: CommitmentPauseInterval,
        zoneId: ZoneId,
    ): Boolean {
        val pauseStart = FinancialPeriodPolicy.periodContaining(
            interval.pausedAt.atZone(zoneId).toLocalDate(),
        ).startDate
        val resumeStart = interval.resumedAt?.let {
            FinancialPeriodPolicy.periodContaining(it.atZone(zoneId).toLocalDate()).startDate
        }
        return if (resumeStart != null) {
            !salaryPeriod.startDate.isBefore(pauseStart) && salaryPeriod.startDate.isBefore(resumeStart)
        } else {
            !salaryPeriod.startDate.isBefore(pauseStart)
        }
    }
}

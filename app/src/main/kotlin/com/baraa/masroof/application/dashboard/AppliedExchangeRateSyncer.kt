package com.baraa.masroof.application.dashboard

import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.repository.FinancialTransactionRepository

/**
 * Persists resolved exchange rates so dashboard totals stay stable across reloads.
 */
object AppliedExchangeRateSyncer {
    suspend fun sync(
        transactions: List<FinancialTransaction>,
        resolutions: Map<String, SarEquivalentResolution>,
        repository: FinancialTransactionRepository,
    ): List<FinancialTransaction> {
        if (resolutions.isEmpty()) return transactions
        val byId = transactions.associateBy { it.id }.toMutableMap()
        for ((txId, resolution) in resolutions) {
            val tx = byId[txId] ?: continue
            if (tx.appliedExchangeRate != null && tx.exchangeRateSource != null) continue
            val updated = tx.copy(
                appliedExchangeRate = resolution.exchangeRate,
                exchangeRateSource = resolution.source,
            )
            if (repository.updateAppliedExchangeRate(
                    id = txId,
                    exchangeRate = resolution.exchangeRate,
                    source = resolution.source,
                )
            ) {
                byId[txId] = updated
            }
        }
        return transactions.map { byId[it.id] ?: it }
    }
}

package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.CardRegistryEntry
import com.baraa.masroof.domain.model.CardRole
import com.baraa.masroof.domain.model.CardType
import java.time.Instant
import java.time.LocalDate

data class CreditFacilityOverview(
    val bank: Bank,
    val primary: CreditCardDashboardRow,
    val supplementaries: List<CreditCardDashboardRow>,
    val facilityDue: StatementDueSnapshot?,
    val facilitySalaryPeriodSpending: SignedMoneyAmount,
    val facilityStatementSpending: SignedMoneyAmount,
    val aggregateStatementPeriodLabel: String?,
    val salaryPeriodLabel: String?,
    val currency: Currency,
) {
    val allCards: List<CreditCardDashboardRow>
        get() = listOf(primary) + supplementaries

    val primaryLast4: String get() = primary.last4
}

data class CreditFacilitiesOverview(
    val facilities: List<CreditFacilityOverview>,
    val debitCards: List<DebitCardOverview>,
    val legacyFlat: CreditCardsOverview,
    val currency: Currency,
) {
    val hasContent: Boolean
        get() = facilities.isNotEmpty() || debitCards.isNotEmpty() || legacyFlat.hasContent
}

data class DebitCardOverview(
    val bank: Bank,
    val last4: String,
    val displayLabel: String,
    val linkedAccountLabel: String?,
    val network: com.baraa.masroof.domain.model.CardNetwork?,
)

object CreditFacilityOverviewBuilder {
    fun build(
        overview: CreditCardsOverview,
        registryCards: List<CardRegistryEntry>,
    ): CreditFacilitiesOverview {
        val ownedCredit = registryCards.filter {
            it.cardType != CardType.DEBIT && it.ownership.isOwned()
        }
        val debitCards = registryCards
            .filter { it.cardType == CardType.DEBIT && it.ownership.isOwned() }
            .map { entry ->
                DebitCardOverview(
                    bank = entry.bank,
                    last4 = entry.last4,
                    displayLabel = RegistryDisplayLabels.cardLabel(entry),
                    linkedAccountLabel = entry.linkedAccount?.maskedNumber?.let { masked ->
                        RegistryDisplayLabels.accountLabel(
                            com.baraa.masroof.domain.model.AccountRegistryEntry(
                                bank = entry.bank,
                                maskedNumber = masked,
                                ownership = entry.ownership,
                                firstSeenRawSmsId = null,
                                lastSeenRawSmsId = null,
                            ),
                        )
                    },
                    network = entry.cardNetwork,
                )
            }

        val rowByLast4 = overview.cards.associateBy { it.last4 }
        val primaryEntries = ownedCredit.filter { it.cardRole == CardRole.PRIMARY }
        val supplementaryEntries = ownedCredit.filter { it.cardRole == CardRole.SUPPLEMENTARY }
        val standaloneEntries = ownedCredit.filter {
            it.cardRole == CardRole.STANDALONE || it.cardRole == null
        }

        val facilities = buildList {
            for (primaryEntry in primaryEntries) {
                val primaryRow = rowByLast4[primaryEntry.last4] ?: placeholderRow(primaryEntry, overview)
                val supplements = supplementaryEntries
                    .filter { it.parentCardLast4 == primaryEntry.last4 }
                    .mapNotNull { rowByLast4[it.last4] ?: placeholderRow(it, overview) }
                add(buildFacility(overview, primaryRow, supplements))
            }
            for (standalone in standaloneEntries) {
                val row = rowByLast4[standalone.last4] ?: placeholderRow(standalone, overview)
                add(buildFacility(overview, row, emptyList()))
            }
            val groupedLast4s = (primaryEntries.map { it.last4 } + standaloneEntries.map { it.last4 }).toSet()
            val orphanSupplements = supplementaryEntries.filter { it.parentCardLast4 !in groupedLast4s }
            for (orphan in orphanSupplements) {
                val row = rowByLast4[orphan.last4] ?: placeholderRow(orphan, overview)
                add(buildFacility(overview, row, emptyList()))
            }
        }

        if (facilities.isEmpty() && overview.cards.isNotEmpty()) {
            return CreditFacilitiesOverview(
                facilities = emptyList(),
                debitCards = debitCards,
                legacyFlat = overview,
                currency = overview.currency,
            )
        }

        return CreditFacilitiesOverview(
            facilities = facilities,
            debitCards = debitCards,
            legacyFlat = overview,
            currency = overview.currency,
        )
    }

    private fun buildFacility(
        overview: CreditCardsOverview,
        primary: CreditCardDashboardRow,
        supplementaries: List<CreditCardDashboardRow>,
    ): CreditFacilityOverview {
        val all = listOf(primary) + supplementaries
        return CreditFacilityOverview(
            bank = primary.bank,
            primary = primary,
            supplementaries = supplementaries,
            facilityDue = resolveLatestStatementDue(all),
            facilitySalaryPeriodSpending = sumSpending(all) { it.salaryPeriodSpendingNet },
            facilityStatementSpending = sumSpending(all) { it.statementSpendingNet },
            aggregateStatementPeriodLabel = primary.statementPeriodLabel
                ?: overview.aggregateStatementPeriodLabel,
            salaryPeriodLabel = overview.salaryPeriodLabel,
            currency = overview.currency,
        )
    }

    private fun placeholderRow(
        entry: CardRegistryEntry,
        overview: CreditCardsOverview,
    ): CreditCardDashboardRow =
        CreditCardDashboardRow(
            bank = entry.bank,
            last4 = entry.last4,
            calendarMonthSpendingNet = SignedMoneyAmount.zero(overview.currency),
            statementSpendingNet = SignedMoneyAmount.zero(overview.currency),
            salaryPeriodSpendingNet = SignedMoneyAmount.zero(overview.currency),
            statementPeriodLabel = null,
            snapshot = null,
        )

    private fun sumSpending(
        rows: List<CreditCardDashboardRow>,
        selector: (CreditCardDashboardRow) -> SignedMoneyAmount,
    ): SignedMoneyAmount {
        if (rows.isEmpty()) return SignedMoneyAmount.zero(Currency.SAR)
        var sum = java.math.BigDecimal.ZERO
        val currency = rows.first().statementSpendingNet.currency
        for (row in rows) {
            sum = sum.add(selector(row).amount)
        }
        return SignedMoneyAmount(
            sum.setScale(Money.SCALE, java.math.RoundingMode.HALF_EVEN),
            currency,
        )
    }

    private fun com.baraa.masroof.domain.model.OwnershipStatus.isOwned(): Boolean =
        this == com.baraa.masroof.domain.model.OwnershipStatus.OWNED
}

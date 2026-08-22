package com.baraa.masroof.presentation.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.application.dashboard.CreditCardDashboardRow
import com.baraa.masroof.application.dashboard.CreditFacilitiesOverview
import com.baraa.masroof.application.dashboard.CreditFacilityOverview
import com.baraa.masroof.application.dashboard.DebitCardOverview
import com.baraa.masroof.domain.model.CardNetwork
import com.baraa.masroof.presentation.common.MasroofCard
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.common.SectionHeader
import com.baraa.masroof.presentation.common.formatCardLast4
import com.baraa.masroof.presentation.locale.formatLocalizedMoney
import com.baraa.masroof.presentation.theme.MasroofThemeExtras
import java.time.ZoneId

private val dashboardCarouselCardMinHeight = 196.dp

enum class DebitCardTilePresentation {
    /** Matches credit facility carousel tile height and structure on the home dashboard. */
    Carousel,
    /** Compact row for cards summary list. */
    List,
}

@Composable
fun CreditFacilitiesSection(
    overview: CreditFacilitiesOverview,
    cardNetworksByLast4: Map<String, CardNetwork?>,
    zoneId: ZoneId,
    ownedCards: List<OwnedCardUi> = emptyList(),
    modifier: Modifier = Modifier,
    onViewAll: (() -> Unit)? = null,
    onOpenDebit: ((DebitCardOverview) -> Unit)? = null,
    facilityModifier: Modifier = Modifier.width(288.dp),
) {
    if (!overview.hasContent) return

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(
            title = stringResource(R.string.dashboard_cards_summary_title),
            icon = MasroofIcons.cardPayment,
            onViewAll = onViewAll,
            viewAllLabel = stringResource(R.string.dashboard_view_all),
        )

        if (overview.facilities.isNotEmpty() || overview.debitCards.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 0.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(overview.facilities, key = { "facility-${it.bank.id}-${it.primaryLast4}" }) { facility ->
                    CreditFacilityCard(
                        facility = facility,
                        cardNetworksByLast4 = cardNetworksByLast4,
                        zoneId = zoneId,
                        ownedCards = ownedCards,
                        modifier = facilityModifier.heightIn(min = dashboardCarouselCardMinHeight),
                    )
                }
                items(overview.debitCards, key = { "debit-${it.bank.id}-${it.last4}" }) { debit ->
                    DebitCardSummaryTile(
                        debit = debit,
                        network = cardNetworksByLast4[CardOwnershipKey.of(debit)] ?: debit.network,
                        modifier = facilityModifier.heightIn(min = dashboardCarouselCardMinHeight),
                        presentation = DebitCardTilePresentation.Carousel,
                        onClick = onOpenDebit?.let { open -> { open(debit) } },
                    )
                }
            }
        }
    }
}

@Composable
fun DebitCardSummaryTile(
    debit: DebitCardOverview,
    network: CardNetwork?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    showNavigationIcon: Boolean = onClick != null,
    presentation: DebitCardTilePresentation = DebitCardTilePresentation.List,
) {
    val extended = MasroofThemeExtras.extendedColors
    val spendingLabel = if (debit.salaryPeriodLabel != null) {
        stringResource(R.string.dashboard_debit_card_salary_spending, debit.salaryPeriodLabel)
    } else {
        stringResource(R.string.dashboard_debit_card_salary_spending_fallback)
    }

    MasroofCard(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        ),
    ) {
        when (presentation) {
            DebitCardTilePresentation.Carousel -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CardNetworkBadge(network = network, last4 = debit.last4)
                        Column {
                            Text(
                                stringResource(R.string.card_network_mada),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                debit.displayLabel,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            )
                        }
                    }
                    if (showNavigationIcon) {
                        Icon(
                            imageVector = MasroofIcons.periodNext,
                            contentDescription = null,
                            tint = extended.account,
                        )
                    }
                }

                Text(
                    spendingLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Text(
                    formatLocalizedMoney(debit.salaryPeriodSpendingNet),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = extended.outflow,
                )

                Text(
                    stringResource(R.string.dashboard_debit_card_linked_account),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    debit.linkedAccountLabel
                        ?: debit.linkedAccountMaskedNumber?.let { formatCardLast4(it) }
                        ?: stringResource(R.string.dashboard_value_unavailable),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            DebitCardTilePresentation.List -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CardNetworkBadge(network = network, last4 = debit.last4)
                        Column {
                            Text(
                                debit.displayLabel,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            )
                            debit.linkedAccountLabel?.let { linked ->
                                Text(
                                    linked,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (showNavigationIcon) {
                        Icon(
                            imageVector = MasroofIcons.periodNext,
                            contentDescription = null,
                            tint = extended.account,
                        )
                    }
                }

                Text(
                    spendingLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Text(
                    formatLocalizedMoney(debit.salaryPeriodSpendingNet),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = extended.outflow,
                )
            }
        }
    }
}

@Composable
fun CreditFacilityCard(
    facility: CreditFacilityOverview,
    cardNetworksByLast4: Map<String, CardNetwork?>,
    zoneId: ZoneId,
    ownedCards: List<OwnedCardUi> = emptyList(),
    modifier: Modifier = Modifier,
    onOpenCard: ((CreditCardDashboardRow) -> Unit)? = null,
) {
    val primaryNetwork = cardNetworksByLast4[CardOwnershipKey.of(facility.bank, facility.primaryLast4)]
    var expanded by rememberSaveable(facility.primaryLast4) { mutableStateOf(false) }
    val extended = MasroofThemeExtras.extendedColors

    MasroofCard(
        modifier = modifier.then(
            if (onOpenCard != null) {
                Modifier.clickable { onOpenCard(facility.primary) }
            } else {
                Modifier.clickable { expanded = !expanded }
            },
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CardNetworkBadge(network = primaryNetwork, last4 = facility.primaryLast4)
                Column {
                    Text(
                        stringResource(R.string.dashboard_credit_facility_primary),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        facility.primary.displayLabel(ownedCards),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }
            if (facility.supplementaries.isNotEmpty()) {
                Icon(
                    imageVector = if (expanded) MasroofIcons.periodPrevious else MasroofIcons.periodNext,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { expanded = !expanded },
                )
            } else if (onOpenCard != null) {
                Icon(
                    imageVector = MasroofIcons.periodNext,
                    contentDescription = null,
                    tint = extended.account,
                )
            }
        }

        if (facility.salaryPeriodLabel != null) {
            Text(
                stringResource(
                    R.string.dashboard_credit_card_salary_purchases_total,
                    facility.salaryPeriodLabel,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(
                formatLocalizedMoney(facility.primary.salaryPeriodSpendingNet),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = extended.outflow,
            )
        }

        Text(
            stringResource(
                R.string.dashboard_credit_card_statement_purchases_total,
                facility.primary.statementPeriodLabel
                    ?: facility.aggregateStatementPeriodLabel
                    ?: stringResource(R.string.dashboard_credit_card_statement_purchases_total_fallback),
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            formatLocalizedMoney(facility.primary.statementSpendingNet),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = extended.outflow,
        )

        facility.facilityDue?.amount?.let { due ->
            Text(
                stringResource(R.string.dashboard_credit_card_card_due),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                formatLocalizedMoney(due),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = extended.liability,
            )
        }

        AnimatedVisibility(visible = expanded && facility.supplementaries.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.dashboard_credit_facility_additional),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                facility.supplementaries.forEach { supplementary ->
                    CreditCardSummaryTile(
                        row = supplementary,
                        salaryPeriodLabel = facility.salaryPeriodLabel,
                        zoneId = zoneId,
                        presentation = CreditCardMetricsPresentation.SummaryPurchases,
                        showBalanceAndDue = false,
                        cardNetwork = cardNetworksByLast4[CardOwnershipKey.of(supplementary)],
                        ownedCards = ownedCards,
                        modifier = if (onOpenCard != null) {
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpenCard(supplementary) }
                        } else {
                            Modifier.fillMaxWidth()
                        },
                    )
                }
            }
        }
    }
}

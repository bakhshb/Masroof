package com.baraa.masroof.presentation.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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

@Composable
fun CreditFacilitiesSection(
    overview: CreditFacilitiesOverview,
    cardNetworksByLast4: Map<String, CardNetwork?>,
    zoneId: ZoneId,
    modifier: Modifier = Modifier,
    onViewAll: (() -> Unit)? = null,
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

        if (overview.facilities.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 0.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(overview.facilities, key = { "${it.bank.id}-${it.primaryLast4}" }) { facility ->
                    CreditFacilityCard(
                        facility = facility,
                        cardNetworksByLast4 = cardNetworksByLast4,
                        zoneId = zoneId,
                        modifier = facilityModifier,
                    )
                }
            }
        }

        overview.debitCards.forEach { debit ->
            DebitCardOverviewRow(
                debit = debit,
                network = cardNetworksByLast4[debit.last4] ?: debit.network,
            )
        }
    }
}

@Composable
fun DebitCardOverviewRow(
    debit: DebitCardOverview,
    network: CardNetwork?,
    modifier: Modifier = Modifier,
) {
    MasroofCard(modifier = modifier.fillMaxWidth()) {
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
                        stringResource(R.string.settings_linked_account_suffix, linked.takeLast(4)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun CreditFacilityCard(
    facility: CreditFacilityOverview,
    cardNetworksByLast4: Map<String, CardNetwork?>,
    zoneId: ZoneId,
    modifier: Modifier = Modifier,
) {
    val primaryNetwork = cardNetworksByLast4[facility.primaryLast4]
    var expanded by rememberSaveable(facility.primaryLast4) { mutableStateOf(false) }
    val extended = MasroofThemeExtras.extendedColors

    MasroofCard(modifier = modifier.clickable { expanded = !expanded }) {
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
                        stringResource(
                            R.string.dashboard_credit_card_last4,
                            formatCardLast4(facility.primaryLast4),
                        ),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }
            Icon(
                imageVector = if (expanded) MasroofIcons.periodPrevious else MasroofIcons.periodNext,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            stringResource(
                R.string.dashboard_credit_facility_statement_total,
                facility.aggregateStatementPeriodLabel
                    ?: stringResource(R.string.dashboard_credit_card_statement_purchases_total_fallback),
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            formatLocalizedMoney(facility.facilityStatementSpending),
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
                        cardNetwork = cardNetworksByLast4[supplementary.last4],
                    )
                }
            }
        }
    }
}

package com.baraa.masroof.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.common.MasroofSecondaryScaffold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBankScreen(
    bank: Bank,
    summary: SettingsBankSummaryUi,
    onBack: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenCards: () -> Unit,
    onOpenLoans: () -> Unit,
) {
    val accountsSubtitle = registryCategorySubtitle(
        followed = summary.followedAccountCount,
        unregistered = summary.unregisteredAccountCount,
        stopped = summary.stoppedAccountCount,
        emptyLabel = stringResource(R.string.settings_accounts_empty),
        followedUnregisteredLabel = { followed, unregistered ->
            stringResource(R.string.settings_hub_accounts_subtitle, followed, unregistered)
        },
        followedOnlyLabel = { followed ->
            stringResource(R.string.settings_hub_accounts_subtitle_followed_only, followed)
        },
        stoppedOnlyLabel = { stopped ->
            stringResource(R.string.settings_hub_accounts_subtitle_stopped_only, stopped)
        },
        followedStoppedLabel = { followed, stopped ->
            stringResource(R.string.settings_hub_accounts_subtitle_followed_stopped, followed, stopped)
        },
        stoppedSuffix = { stopped ->
            stringResource(R.string.settings_bank_category_stopped_suffix, stopped)
        },
    )
    val cardsSubtitle = registryCategorySubtitle(
        followed = summary.followedCardCount,
        unregistered = summary.unregisteredCardCount,
        stopped = summary.stoppedCardCount,
        emptyLabel = stringResource(R.string.settings_cards_empty),
        followedUnregisteredLabel = { followed, unregistered ->
            stringResource(R.string.settings_hub_cards_subtitle, followed, unregistered)
        },
        followedOnlyLabel = { followed ->
            stringResource(R.string.settings_hub_cards_subtitle_followed_only, followed)
        },
        stoppedOnlyLabel = { stopped ->
            stringResource(R.string.settings_hub_cards_subtitle_stopped_only, stopped)
        },
        followedStoppedLabel = { followed, stopped ->
            stringResource(R.string.settings_hub_cards_subtitle_followed_stopped, followed, stopped)
        },
        stoppedSuffix = { stopped ->
            stringResource(R.string.settings_bank_category_stopped_suffix, stopped)
        },
    )
    val loansSubtitle = if (summary.loanCount > 0) {
        stringResource(R.string.settings_bank_summary_loans, summary.loanCount)
    } else {
        stringResource(R.string.settings_loans_empty)
    }

    MasroofSecondaryScaffold(
        title = settingsBankLabel(bank),
        onBack = onBack,
        backContentDescription = stringResource(R.string.settings_back),
    ) { contentModifier ->
        Column(
            modifier = contentModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsNavRow(
                icon = MasroofIcons.externalIn,
                title = stringResource(R.string.settings_bank_accounts_title),
                subtitle = accountsSubtitle,
                onClick = onOpenAccounts,
                enabled = summary.accountCount > 0,
            )

            SettingsNavRow(
                icon = MasroofIcons.cardPayment,
                title = stringResource(R.string.settings_bank_cards_title),
                subtitle = cardsSubtitle,
                onClick = onOpenCards,
                enabled = summary.cardCount > 0,
            )

            SettingsNavRow(
                icon = MasroofIcons.moneyMovement,
                title = stringResource(R.string.settings_bank_loans_title),
                subtitle = loansSubtitle,
                onClick = onOpenLoans,
                enabled = summary.loanCount > 0,
            )
        }
    }
}

internal fun registryCategorySubtitle(
    followed: Int,
    unregistered: Int,
    stopped: Int,
    emptyLabel: String,
    followedUnregisteredLabel: (Int, Int) -> String,
    followedOnlyLabel: (Int) -> String,
    stoppedOnlyLabel: (Int) -> String,
    followedStoppedLabel: (Int, Int) -> String,
    stoppedSuffix: (Int) -> String,
): String {
    val total = followed + unregistered + stopped
    if (total == 0) return emptyLabel
    if (unregistered > 0) {
        val base = followedUnregisteredLabel(followed, unregistered)
        return if (stopped > 0) base + stoppedSuffix(stopped) else base
    }
    if (followed > 0 && stopped > 0) return followedStoppedLabel(followed, stopped)
    if (stopped > 0) return stoppedOnlyLabel(stopped)
    return followedOnlyLabel(followed)
}

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
    val accountsSubtitle = formatRegistryCategorySubtitle(
        state = resolveRegistryCategorySubtitle(
            followed = summary.followedAccountCount,
            unregistered = summary.unregisteredAccountCount,
            stopped = summary.stoppedAccountCount,
        ),
        emptyLabel = stringResource(R.string.settings_accounts_empty),
        followedUnregisteredRes = R.string.settings_hub_accounts_subtitle,
        followedOnlyRes = R.string.settings_hub_accounts_subtitle_followed_only,
        stoppedOnlyRes = R.string.settings_hub_accounts_subtitle_stopped_only,
        followedStoppedRes = R.string.settings_hub_accounts_subtitle_followed_stopped,
    )
    val cardsSubtitle = formatRegistryCategorySubtitle(
        state = resolveRegistryCategorySubtitle(
            followed = summary.followedCardCount,
            unregistered = summary.unregisteredCardCount,
            stopped = summary.stoppedCardCount,
        ),
        emptyLabel = stringResource(R.string.settings_cards_empty),
        followedUnregisteredRes = R.string.settings_hub_cards_subtitle,
        followedOnlyRes = R.string.settings_hub_cards_subtitle_followed_only,
        stoppedOnlyRes = R.string.settings_hub_cards_subtitle_stopped_only,
        followedStoppedRes = R.string.settings_hub_cards_subtitle_followed_stopped,
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

internal sealed interface RegistryCategorySubtitleState {
    data object Empty : RegistryCategorySubtitleState

    data class FollowedUnregistered(
        val followed: Int,
        val unregistered: Int,
        val stopped: Int,
    ) : RegistryCategorySubtitleState

    data class FollowedStopped(
        val followed: Int,
        val stopped: Int,
    ) : RegistryCategorySubtitleState

    data class StoppedOnly(
        val stopped: Int,
    ) : RegistryCategorySubtitleState

    data class FollowedOnly(
        val followed: Int,
    ) : RegistryCategorySubtitleState
}

internal fun resolveRegistryCategorySubtitle(
    followed: Int,
    unregistered: Int,
    stopped: Int,
): RegistryCategorySubtitleState {
    val total = followed + unregistered + stopped
    if (total == 0) return RegistryCategorySubtitleState.Empty
    if (unregistered > 0) {
        return RegistryCategorySubtitleState.FollowedUnregistered(
            followed = followed,
            unregistered = unregistered,
            stopped = stopped,
        )
    }
    if (followed > 0 && stopped > 0) {
        return RegistryCategorySubtitleState.FollowedStopped(
            followed = followed,
            stopped = stopped,
        )
    }
    if (stopped > 0) return RegistryCategorySubtitleState.StoppedOnly(stopped)
    return RegistryCategorySubtitleState.FollowedOnly(followed)
}

@Composable
private fun formatRegistryCategorySubtitle(
    state: RegistryCategorySubtitleState,
    emptyLabel: String,
    followedUnregisteredRes: Int,
    followedOnlyRes: Int,
    stoppedOnlyRes: Int,
    followedStoppedRes: Int,
): String =
    when (state) {
        RegistryCategorySubtitleState.Empty -> emptyLabel
        is RegistryCategorySubtitleState.FollowedUnregistered -> {
            val base = stringResource(
                followedUnregisteredRes,
                state.followed,
                state.unregistered,
            )
            if (state.stopped > 0) {
                base + stringResource(R.string.settings_bank_category_stopped_suffix, state.stopped)
            } else {
                base
            }
        }
        is RegistryCategorySubtitleState.FollowedStopped -> stringResource(
            followedStoppedRes,
            state.followed,
            state.stopped,
        )
        is RegistryCategorySubtitleState.StoppedOnly -> stringResource(
            stoppedOnlyRes,
            state.stopped,
        )
        is RegistryCategorySubtitleState.FollowedOnly -> stringResource(
            followedOnlyRes,
            state.followed,
        )
    }

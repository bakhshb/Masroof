package com.baraa.masroof.presentation.dashboard

import android.content.Context
import com.baraa.masroof.R

fun formatOwnedAccountsBadge(
    accounts: List<OwnedAccountUi>,
    context: Context,
): String? =
    when (accounts.size) {
        0 -> null
        1 -> "···${accounts.first().maskedNumber}"
        2 -> accounts.joinToString(" ") { "···${it.maskedNumber}" }
        else -> context.getString(R.string.dashboard_owned_accounts_badge_count, accounts.size)
    }

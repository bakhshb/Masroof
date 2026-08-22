package com.baraa.masroof.presentation.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun TransactionListRow(
    row: TransactionPreviewUi,
    modifier: Modifier = Modifier,
    ownedCards: List<OwnedCardUi> = emptyList(),
    onClick: () -> Unit,
) {
    TransactionRow(
        row = row,
        modifier = modifier,
        ownedCards = ownedCards,
        onClick = onClick,
    )
}

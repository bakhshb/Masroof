package com.baraa.masroof.presentation.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.presentation.common.formatCardLast4
import com.baraa.masroof.presentation.locale.formatLocalizedMoney
import com.baraa.masroof.presentation.locale.formatLocalizedTransactionDate

@Composable
fun transactionDetailShareText(transaction: TransactionPreviewUi): String {
    val merchant = transaction.title
    return TransactionShareText.document(
        stringResource(R.string.transaction_share_detail_heading, stringResource(R.string.app_name)),
        merchant?.let {
            TransactionShareText.field(stringResource(R.string.transaction_detail_merchant), it)
        },
        TransactionShareText.field(
            stringResource(R.string.transaction_share_amount),
            formatLocalizedMoney(transaction.amount),
        ),
        TransactionShareText.field(
            stringResource(R.string.transaction_detail_type),
            transactionTypeLabel(transaction.type),
        ),
        TransactionShareText.field(
            stringResource(R.string.transaction_detail_date),
            formatLocalizedTransactionDate(transaction.localDate),
        ),
        TransactionShareText.field(
            stringResource(R.string.transaction_detail_direction),
            stringResource(TransactionDirectionPresentation.labelRes(transaction.direction)),
        ),
        transaction.cardLast4?.let { last4 ->
            TransactionShareText.field(
                stringResource(R.string.transaction_detail_card),
                stringResource(R.string.dashboard_credit_card_last4, formatCardLast4(last4)),
            )
        },
    )
}

@Composable
fun transactionListShareText(
    periodLabel: String,
    filter: TransactionListFilterState,
    transactions: List<TransactionPreviewUi>,
    totalAmount: Money?,
): String {
    val typeLabels = filter.types.map { transactionTypeLabel(it) }.sorted()
    val cardLabels = filter.cardLast4s.map { last4 ->
        stringResource(R.string.dashboard_credit_card_last4, formatCardLast4(last4))
    }.sorted()
    val rows = transactions.map { tx ->
        TransactionShareText.listRow(
            date = formatLocalizedTransactionDate(tx.localDate),
            title = tx.title ?: transactionTypeLabel(tx.type),
            amount = formatLocalizedMoney(tx.amount),
            type = transactionTypeLabel(tx.type),
        )
    }
    return TransactionShareText.listDocument(
        title = stringResource(
            R.string.transaction_share_list_heading,
            stringResource(R.string.app_name),
            periodLabel,
        ),
        metaLines = listOfNotNull(
            filter.searchQuery.trim().takeIf { it.isNotEmpty() }?.let { query ->
                TransactionShareText.field(stringResource(R.string.transaction_share_search), query)
            },
            typeLabels.takeIf { it.isNotEmpty() }?.let { labels ->
                TransactionShareText.field(
                    stringResource(R.string.transaction_share_types),
                    labels.joinToString(" • "),
                )
            },
            cardLabels.takeIf { it.isNotEmpty() }?.let { labels ->
                TransactionShareText.field(
                    stringResource(R.string.transaction_share_cards),
                    labels.joinToString(" • "),
                )
            },
            TransactionShareText.field(
                stringResource(R.string.transaction_share_count),
                transactions.size.toString(),
            ),
            totalAmount?.let { amount ->
                TransactionShareText.field(
                    stringResource(R.string.transaction_share_total),
                    formatLocalizedMoney(amount),
                )
            },
        ),
        rows = rows,
    )
}


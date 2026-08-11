package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.presentation.common.BackNavigationIcon
import com.baraa.masroof.presentation.common.SectionHeader
import com.baraa.masroof.presentation.common.MasroofIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionListScreen(
    periodLabel: String,
    transactions: List<TransactionPreviewUi>,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.transaction_list_title)) },
                navigationIcon = {
                    BackNavigationIcon(
                        onClick = onBack,
                        contentDescription = stringResource(R.string.review_back),
                    )
                },
            )
        },
    ) { padding ->
        if (transactions.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(stringResource(R.string.dashboard_empty_period))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    SectionHeader(
                        title = stringResource(R.string.transaction_list_period, periodLabel),
                        icon = MasroofIcons.calendar,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    Text(
                        stringResource(R.string.transaction_list_count, transactions.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                items(transactions, key = { it.id }) { row ->
                    TransactionRow(row)
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

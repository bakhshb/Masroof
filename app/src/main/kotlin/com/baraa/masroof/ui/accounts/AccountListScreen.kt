package com.baraa.masroof.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baraa.masroof.MasroofApplication
import com.baraa.masroof.R
import com.baraa.masroof.data.db.FinancialAccount
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Account management screen. Lists all owned financial accounts (cards,
 * wallets, bank accounts, investment accounts) and lets the user add /
 * edit / delete them. The InternalTransferRule and the InvestmentTransferRule
 * both consult this list to classify transfers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountListScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as MasroofApplication
    val repo = app.financialAccountRepository
    val scope = rememberCoroutineScope()

    var accounts by remember { mutableStateOf<List<FinancialAccount>>(emptyList()) }
    var editing by remember { mutableStateOf<FinancialAccount?>(null) }
    var isAdding by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        repo.observeAll().collectLatest { list -> accounts = list }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(id = R.string.accounts_title)) },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { isAdding = true }) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(id = R.string.account_add_title),
                )
            }
        },
    ) { inner ->
        Box(modifier = Modifier.fillMaxSize().padding(inner)) {
            if (accounts.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items = accounts, key = { it.id }) { acc ->
                        AccountRow(
                            account = acc,
                            onClick = { editing = acc },
                        )
                    }
                    item { Spacer(modifier = Modifier.height(72.dp)) }
                }
            }
        }
    }

    if (isAdding) {
        AccountEditDialog(
            existing = null,
            onDismiss = { isAdding = false },
            onSave = { displayName, institution, type, lastFour, aliases ->
                scope.launch {
                    repo.add(displayName, type, institution, lastFour, aliases)
                    isAdding = false
                }
            },
        )
    }

    editing?.let { acc ->
        AccountEditDialog(
            existing = acc,
            onDismiss = { editing = null },
            onSave = { displayName, institution, type, lastFour, aliases ->
                scope.launch {
                    repo.update(acc.copy(
                        displayName = displayName,
                        institutionName = institution,
                        accountType = type,
                        lastFourDigits = lastFour,
                        senderAliases = aliases,
                        isActive = true,
                    ))
                    editing = null
                }
            },
            onDelete = {
                scope.launch {
                    repo.delete(acc)
                    editing = null
                }
            },
        )
    }
}

@Composable
private fun AccountRow(account: FinancialAccount, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = account.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                account.lastFourDigits?.let { last4 ->
                    Text(
                        text = "**** $last4",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            account.institutionName?.let { inst ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = inst,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (account.senderAliases.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = account.senderAliases.joinToString(", "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(id = R.string.accounts_empty),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(id = R.string.accounts_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

package com.baraa.masroof.ui.accounts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.baraa.masroof.MasroofApplication

/** Standalone route that opens the selected-SMS binding dialog for a persisted account. */
@Composable
fun AccountBindRoute(
    accountId: Long,
    onBack: () -> Unit,
    onImportNow: () -> Unit = onBack,
) {
    val context = LocalContext.current
    val app = context.applicationContext as MasroofApplication
    var account by remember(accountId) { mutableStateOf<com.baraa.masroof.data.db.FinancialAccount?>(null) }
    LaunchedEffect(accountId) { account = app.financialAccountRepository.getById(accountId) }
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            account?.let {
                AccountSmsBindingDialog(
                    accountId = it.id,
                    accountType = it.accountType,
                    onDismiss = onBack,
                    onImportNow = onImportNow,
                )
            }
        }
    }
}

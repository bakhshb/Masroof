package com.baraa.masroof.ui.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baraa.masroof.MasroofApplication
import com.baraa.masroof.R
import com.baraa.masroof.diagnostics.FakeSmsSamples
import com.baraa.masroof.diagnostics.FakeTransactionStore
import com.baraa.masroof.diagnostics.TextSanitizer
import com.baraa.masroof.sms.TemplateResolutionResult
import com.baraa.masroof.sms.TemplateResolutionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * وضع البيانات التجريبية — test data mode screen. Lists the bundled
 * fake SMS samples, lets the user load one through the parser pipeline,
 * and provides a "delete all fake data" button.
 *
 * **The fake rows are kept in [FakeTransactionStore] (in-memory). They
 * are NEVER inserted into the real Room database.** This is the hard
 * guarantee: enabling test data mode cannot pollute the user's real
 * transactions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestDataModeScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as MasroofApplication
    val scope = rememberCoroutineScope()

    var count by remember { mutableStateOf(0) }
    var toast by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        count = withContext(Dispatchers.IO) { FakeTransactionStore.count() }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(id = R.string.test_data_label)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier.fillMaxSize().padding(inner).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Text(
                    text = stringResource(R.string.test_data_disabled),
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                text = stringResource(R.string.test_data_count, count),
                style = MaterialTheme.typography.titleMedium,
            )
            HorizontalDivider()
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(FakeSmsSamples.samples, key = { it.id }) { sample ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = sample.label, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "المرسل: ${sample.sender} — القناة: ${sample.channel}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                text = sample.body,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                Button(onClick = {
                                    scope.launch {
                                        val result = withContext(Dispatchers.Default) {
                                            val profile = app.senderProfileRepository.findByRawSender(sample.sender)
                                            val patterns = profile?.let {
                                                app.messagePatternRepository.getForSender(it.id)
                                            }.orEmpty()
                                            val outcome = TemplateResolutionService.resolve(
                                                sender = sample.sender,
                                                body = sample.body,
                                                smsTimestampMillis = null,
                                                patterns = patterns,
                                            )
                                            val parsed = (outcome as? TemplateResolutionResult.Matched)?.parsed
                                                ?: return@withContext false
                                            FakeTransactionStore.addFromParse(
                                                sampleId = sample.id,
                                                sender = sample.sender,
                                                rawBody = sample.body,
                                                merchant = parsed.merchant,
                                                amount = parsed.amount,
                                                currency = parsed.currency,
                                                type = parsed.transactionType,
                                                status = parsed.status,
                                                date = parsed.transactionDate,
                                                time = parsed.transactionTime,
                                            )
                                            true
                                        }
                                        count = FakeTransactionStore.count()
                                        toast = if (result) {
                                            context.getString(R.string.test_data_loaded, 1)
                                        } else {
                                            "لا يوجد قالب معتمد وفعال مطابق للعينة"
                                        }
                                    }
                                }) { Text(stringResource(R.string.test_data_load)) }
                            }
                        }
                    }
                }
            }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { FakeTransactionStore.clear() }
                        count = 0
                        toast = context.getString(R.string.test_data_cleared)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.test_data_clear)) }
        }
    }
}

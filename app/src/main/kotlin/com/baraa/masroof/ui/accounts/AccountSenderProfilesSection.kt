package com.baraa.masroof.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baraa.masroof.MasroofApplication
import com.baraa.masroof.data.repository.SenderProfile
import com.baraa.masroof.ui.theme.FinancialTypography
import com.baraa.masroof.ui.theme.SecondaryButton
import com.baraa.masroof.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * Account ↔ SenderProfile association (many-to-many).
 * Does not use raw SMS; user picks trained senders.
 */
@Composable
fun AccountSenderProfilesSection(
    accountId: Long,
    onTrainSender: (() -> Unit)? = null,
) {
    val app = LocalContext.current.applicationContext as MasroofApplication
    val scope = rememberCoroutineScope()
    val allProfiles by app.senderProfileRepository.observeActive()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var linkedIds by remember(accountId) { mutableStateOf<Set<Long>>(emptySet()) }

    LaunchedEffect(accountId) {
        linkedIds = app.senderProfileRepository.profilesForAccount(accountId).map { it.id }.toSet()
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
        Text("مرسل الرسائل", style = FinancialTypography.merchant)
        Text(
            "اختر مرسلاً تم تعليمه مسبقاً. المعرفات الرقمية تُدخل يدوياً أدناه.",
            style = FinancialTypography.metadata,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (allProfiles.isEmpty()) {
            Text("لم يتم تعليم أي مرسل بعد", color = MaterialTheme.colorScheme.error)
            onTrainSender?.let {
                SecondaryButton("تعليم مرسل جديد", onClick = it, modifier = Modifier.fillMaxWidth())
            }
        } else {
            allProfiles.forEach { entity ->
                val selected = entity.id in linkedIds
                FilterChip(
                    selected = selected,
                    onClick = {
                        scope.launch {
                            if (selected) {
                                app.senderProfileRepository.dissociateAccount(accountId, entity.id)
                            } else {
                                app.senderProfileRepository.associateAccount(accountId, entity.id)
                            }
                            linkedIds = app.senderProfileRepository.profilesForAccount(accountId)
                                .map { it.id }
                                .toSet()
                        }
                    },
                    label = {
                        Text(
                            entity.displayInstitutionName?.let { "$it — ${entity.displaySender}" }
                                ?: entity.displaySender,
                        )
                    },
                )
            }
            onTrainSender?.let {
                SecondaryButton("تعليم مرسل جديد", onClick = it, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

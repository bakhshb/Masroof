package com.baraa.masroof.ui.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.baraa.masroof.MasroofApplication
import com.baraa.masroof.sms.SmsImportRange
import com.baraa.masroof.ui.theme.FinancialShapes
import com.baraa.masroof.ui.theme.FinancialTypography
import com.baraa.masroof.ui.theme.PrimaryButton
import com.baraa.masroof.ui.theme.SecondaryButton
import com.baraa.masroof.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun SelectSenderStep(
    app: MasroofApplication,
    state: UiOnboardingState,
    permissionGranted: Boolean,
    permanentlyDenied: Boolean,
    resumeGeneration: Int,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onContinue: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var senders by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(permissionGranted, resumeGeneration) {
        if (!permissionGranted) {
            loading = false
            senders = emptyList()
            return@LaunchedEffect
        }
        loading = true
        loadError = null
        val range = SmsImportRange.lastDays(LocalDate.now(), 30)
        val messages = when (val result = app.smsRepository.loadInboxResult(range)) {
            is com.baraa.masroof.sms.SmsInboxLoadResult.Success -> result.messages
            is com.baraa.masroof.sms.SmsInboxLoadResult.PermissionDenied -> {
                loadError = result.messageAr
                emptyList()
            }
            is com.baraa.masroof.sms.SmsInboxLoadResult.Failed -> {
                loadError = "تعذر قراءة الرسائل: ${result.errorMessage}"
                emptyList()
            }
        }
        senders = messages
            .mapNotNull { it.sender?.trim()?.takeIf(String::isNotBlank) }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key to it.value }
        loading = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
        Text("ربط مرسل الرسائل", style = MaterialTheme.typography.titleLarge)
        Text(
            "اختر اسم المرسل الذي تصل منه رسائل هذا الحساب. لن تُحلل الرسائل ولن تُنشأ أنماط في هذه الخطوة.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!permissionGranted) {
            Text(
                if (permanentlyDenied) {
                    "إذن قراءة الرسائل مرفوض. يمكنك فتح الإعدادات أو ربط المرسل لاحقاً."
                } else {
                    "اسمح بقراءة الرسائل لعرض أسماء المرسلين وعدد رسائل كل مرسل."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PrimaryButton(
                "السماح بقراءة الرسائل",
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth(),
            )
            if (permanentlyDenied) {
                SecondaryButton(
                    "فتح إعدادات التطبيق",
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            if (loading) Text("جارٍ تحميل المرسلين…", style = FinancialTypography.metadata)
            loadError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            senders.take(60).forEach { (sender, count) ->
                val selected = state.selectedSenderDisplay == sender
                Surface(
                    Modifier.fillMaxWidth().clickable {
                        scope.launch {
                            runCatching {
                                associateSelectedSender(
                                    state = state,
                                    rawSender = sender,
                                    upsertSender = { raw ->
                                        val profile =
                                            app.senderProfileRepository.upsertFromSmsSender(raw)
                                        SelectedSender(
                                            id = profile.id,
                                            normalizedKey = profile.normalizedSenderKey,
                                            displayName = profile.displaySender,
                                        )
                                    },
                                    associateAccount = { accountId, senderProfileId ->
                                        app.senderProfileRepository.associateAccount(
                                            accountId,
                                            senderProfileId,
                                        )
                                    },
                                )
                            }.onFailure {
                                loadError = "تعذر ربط المرسل بالحساب"
                            }
                        }
                    },
                    shape = FinancialShapes.medium,
                    tonalElevation = if (selected) 3.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ) {
                    Column(Modifier.padding(Spacing.x3)) {
                        Text(sender, style = FinancialTypography.merchant)
                        Text("$count رسالة", style = FinancialTypography.metadata)
                    }
                }
            }
            if (!loading && senders.isEmpty() && loadError == null) {
                Text("لم يُعثر على مرسلين في آخر 30 يوماً.")
            }
            PrimaryButton(
                "متابعة",
                enabled = state.selectedSenderProfileId > 0L,
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SecondaryButton(
            "ربط المرسل لاحقاً",
            onClick = {
                state.selectedSenderProfileId = 0L
                state.selectedSenderKey = ""
                state.selectedSenderDisplay = ""
                onContinue()
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

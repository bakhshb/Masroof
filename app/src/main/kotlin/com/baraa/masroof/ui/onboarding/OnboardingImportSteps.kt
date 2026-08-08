package com.baraa.masroof.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.baraa.masroof.MasroofApplication
import com.baraa.masroof.data.db.MessagePatternStatus
import com.baraa.masroof.onboarding.OnboardingImportPlanner
import com.baraa.masroof.data.repository.SmsImportMode
import com.baraa.masroof.sms.SmsImportRange
import com.baraa.masroof.ui.theme.PrimaryButton
import com.baraa.masroof.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun ImportPreviewStep(
    app: MasroofApplication,
    state: UiOnboardingState,
    resumeGeneration: Int,
    onContinue: () -> Unit,
) {
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.selectedSenderProfileId, state.senderInbox, resumeGeneration) {
        loading = true
        val patterns = app.messagePatternRepository.getForSender(state.selectedSenderProfileId)
            .filter {
                it.definition.status == MessagePatternStatus.APPROVED ||
                    it.definition.status == MessagePatternStatus.DEPRECATED
            }
        var inbox = state.senderInbox
        if (inbox.isEmpty() && state.selectedSenderKey.isNotBlank()) {
            val range = SmsImportRange.lastDays(LocalDate.now(), 30)
            val all = when (val result = app.smsRepository.loadInboxResult(range)) {
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
            inbox = OnboardingImportPlanner.filterMessagesForSender(all, state.selectedSenderKey)
            state.senderInbox = inbox
        }
        state.importPreview = OnboardingImportPlanner.importPreview(
            inbox,
            patterns,
        )
        loading = false
    }
    val preview = state.importPreview
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
        Text("معاينة الاستيراد", style = MaterialTheme.typography.titleLarge)
        loadError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (loading || preview == null) {
            Text("جارٍ الحساب…")
        } else {
            Text("الرسائل الموجودة: ${preview.totalMessages}")
            Text("مطابقة للأنماط: ${preview.matchedPatterns}")
            Text("غير معروفة: ${preview.unknown}")
            Text("سيتم استيراد: ${preview.willImport}", style = MaterialTheme.typography.titleMedium)
            Text(
                "الرسائل غير المعروفة تبقى بلا استيراد ويمكن إنشاء أنماط لها لاحقاً.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        PrimaryButton(
            "متابعة لمعاينة الربط",
            enabled = preview != null,
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun LinkPreviewStep(
    app: MasroofApplication,
    state: UiOnboardingState,
    resumeGeneration: Int,
    onContinue: () -> Unit,
) {
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(state.createdAccountId, state.importPreview, resumeGeneration) {
        loading = true
        val patterns = app.messagePatternRepository.getForSender(state.selectedSenderProfileId)
        val accounts = app.financialAccountRepository.getOwnedActive()
        state.linkPreview = OnboardingImportPlanner.linkPreview(
            messages = state.senderInbox,
            patterns = patterns,
            accounts = accounts,
            identifierRepository = app.accountIdentifierRepository,
        )
        loading = false
    }
    val preview = state.linkPreview
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
        Text("معاينة ربط الحسابات", style = MaterialTheme.typography.titleLarge)
        Text(
            "الربط يعتمد على المعرفات (آخر 4 أرقام)، وليس على اسم المرسل وحده.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (loading || preview == null) {
            Text("جارٍ الحساب…")
        } else {
            preview.byAccount.forEach { bucket ->
                val last = bucket.lastFourHint?.let { "****$it" } ?: "بدون معرف"
                Text("${bucket.accountName} $last — ${bucket.matchedCount} معاملة")
            }
            Text("تحتاج مراجعة: ${preview.needsReview}")
        }
        PrimaryButton(
            "متابعة للاستيراد",
            enabled = preview != null,
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun ImportCommitStep(
    app: MasroofApplication,
    state: UiOnboardingState,
    repository: OnboardingRepository,
    resumeGeneration: Int,
    onFinished: () -> Unit,
    onStepCompleted: (OnboardingStep) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    LaunchedEffect(resumeGeneration) {
        running = false
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
        Text("استيراد الرسائل المطابقة", style = MaterialTheme.typography.titleLarge)
        Text(
            "سيُستورد فقط ما يطابق الأنماط المعتمدة. الأنماط تحدد نوع الرسالة؛ المعرفات تحدد الحساب.",
            style = MaterialTheme.typography.bodyMedium,
        )
        state.importStatus?.let { Text(it) }
        PrimaryButton(
            if (running) "جارٍ الاستيراد…" else "استيراد الآن",
            enabled = !running,
            onClick = {
                running = true
                scope.launch {
                    runCatching {
                        val range = SmsImportRange.lastDays(LocalDate.now(), 30)
                        val messages = if (state.senderInbox.isNotEmpty()) {
                            state.senderInbox
                        } else {
                            when (val result = app.smsRepository.loadInboxResult(range)) {
                                is com.baraa.masroof.sms.SmsInboxLoadResult.Success ->
                                    OnboardingImportPlanner.filterMessagesForSender(
                                        result.messages,
                                        state.selectedSenderKey,
                                    )
                                is com.baraa.masroof.sms.SmsInboxLoadResult.PermissionDenied -> {
                                    state.importStatus = result.messageAr
                                    return@runCatching
                                }
                                is com.baraa.masroof.sms.SmsInboxLoadResult.Failed -> {
                                    state.importStatus = "تعذر قراءة الرسائل: ${result.errorMessage}"
                                    return@runCatching
                                }
                            }
                        }
                        val preview = app.importOrchestrator.scan(
                            messages = messages,
                            trackingStartDate = state.trackingDate,
                            mode = SmsImportMode.REGISTERED_ACCOUNTS_ONLY,
                        )
                        val result = app.importOrchestrator.commit(
                            scanPreview = preview,
                            trackingStartDate = state.trackingDate,
                            importedSms = messages,
                        )
                        state.importStatus =
                            "تم استيراد ${result.importedTransactions} — مراجعة ${result.needsReviewTransactions} — مكرر ${result.duplicateTransactions}"
                        app.financialSetupRepository.save(setupFrom(state, completed = true))
                        onStepCompleted(OnboardingStep.IMPORT)
                        repository.markCompleted()
                        state.step = OnboardingStep.COMPLETION
                        onFinished()
                    }.onFailure {
                        state.importStatus = "تعذر إكمال الاستيراد. حاول مرة أخرى."
                    }
                    running = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        PrimaryButton(
            "تخطي الاستيراد الآن",
            enabled = !running,
            onClick = {
                scope.launch {
                    app.financialSetupRepository.save(setupFrom(state, completed = true))
                    onStepCompleted(OnboardingStep.IMPORT)
                    repository.markCompleted()
                    state.step = OnboardingStep.COMPLETION
                    onFinished()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

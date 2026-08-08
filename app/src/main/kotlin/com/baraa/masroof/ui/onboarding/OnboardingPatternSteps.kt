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
import com.baraa.masroof.data.db.MessagePatternStatus
import com.baraa.masroof.data.repository.SenderProfile
import com.baraa.masroof.onboarding.OnboardingImportPlanner
import com.baraa.masroof.sms.DiscoveredMessagePattern
import com.baraa.masroof.sms.MessageTemplateEngine
import com.baraa.masroof.sms.PatternDiscoveryService
import com.baraa.masroof.sms.SmsImportRange
import com.baraa.masroof.sms.SmsMessage
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
    resumeGeneration: Int,
    onContinue: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var senders by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(resumeGeneration) {
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
            .mapNotNull { it.sender?.trim()?.takeIf { s -> s.isNotBlank() } }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key to it.value }
        loading = false
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
        Text("اختر مرسل الرسائل", style = MaterialTheme.typography.titleLarge)
        Text(
            "الغرض إنشاء أنماط لهذا المرسل — لن يُنشأ حساب ولن تُستورد عمليات الآن.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (loading) Text("جارٍ قراءة الرسائل…", style = FinancialTypography.metadata)
        loadError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        senders.take(40).forEach { (sender, count) ->
            val selected = state.selectedSenderDisplay == sender
            Surface(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        scope.launch {
                            val profile = app.senderProfileRepository.upsertFromSmsSender(sender)
                            state.selectedSenderProfileId = profile.id
                            state.selectedSenderKey = profile.normalizedSenderKey
                            state.selectedSenderDisplay = profile.displaySender
                            state.patternSourceProfileId = profile.id
                            state.patternSourceLabel =
                                profile.displayInstitutionName ?: profile.displaySender
                            val range = SmsImportRange.lastDays(LocalDate.now(), 30)
                            when (val result = app.smsRepository.loadInboxResult(range)) {
                                is com.baraa.masroof.sms.SmsInboxLoadResult.Success -> {
                                    loadError = null
                                    state.senderInbox = OnboardingImportPlanner.filterMessagesForSender(
                                        result.messages,
                                        profile.normalizedSenderKey,
                                    )
                                }
                                is com.baraa.masroof.sms.SmsInboxLoadResult.PermissionDenied ->
                                    loadError = result.messageAr
                                is com.baraa.masroof.sms.SmsInboxLoadResult.Failed ->
                                    loadError = "تعذر قراءة الرسائل: ${result.errorMessage}"
                            }
                        }
                    },
                shape = FinancialShapes.medium,
                tonalElevation = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            ) {
                Column(Modifier.padding(Spacing.x3)) {
                    Text(sender, style = FinancialTypography.merchant)
                    Text("$count رسالة", style = FinancialTypography.metadata)
                }
            }
        }
        PrimaryButton(
            "متابعة",
            enabled = state.selectedSenderProfileId > 0L,
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun CreatePatternStep(
    app: MasroofApplication,
    state: UiOnboardingState,
    resumeGeneration: Int,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(resumeGeneration, state.selectedSenderKey) {
        saving = false
        if (state.senderInbox.isEmpty() && state.selectedSenderKey.isNotBlank()) {
            val range = SmsImportRange.lastDays(LocalDate.now(), 30)
            val messages = (app.smsRepository.loadInboxResult(range) as?
                com.baraa.masroof.sms.SmsInboxLoadResult.Success)?.messages.orEmpty()
            state.senderInbox = OnboardingImportPlanner.filterMessagesForSender(
                messages,
                state.selectedSenderKey,
            )
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
        Text("إنشاء نمط من رسالة", style = MaterialTheme.typography.titleLarge)
        Text(
            "اختر رسالة حقيقية. يُشتق القالب منها مباشرة — وليس من رسالة مشابهة.",
            style = MaterialTheme.typography.bodyMedium,
        )
        saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.senderInbox.take(25).forEach { sms ->
            val body = sms.body.orEmpty()
            if (body.isBlank()) return@forEach
            val selected = state.selectedSmsBody == body
            Surface(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        val built = MessageTemplateEngine.buildFromSms(body)
                        state.selectedSmsBody = body
                        state.draftTemplate = built.templateText
                        state.draftPlaceholders = built.placeholders
                        state.draftSignature = built.signature
                        state.draftFriendlyName = built.displayName
                    },
                shape = FinancialShapes.medium,
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    body.take(220),
                    Modifier.padding(Spacing.x3),
                    style = FinancialTypography.metadata,
                )
            }
        }
        if (!state.selectedSmsBody.isNullOrBlank()) {
            Text("الرسالة الأصلية", style = FinancialTypography.merchant)
            Surface(Modifier.fillMaxWidth(), shape = FinancialShapes.medium, tonalElevation = 1.dp) {
                Text(state.selectedSmsBody!!, Modifier.padding(Spacing.x3), style = FinancialTypography.metadata)
            }
            Text("النمط (القالب)", style = FinancialTypography.merchant)
            Surface(Modifier.fillMaxWidth(), shape = FinancialShapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(state.draftTemplate, Modifier.padding(Spacing.x3), style = FinancialTypography.metadata)
            }
            if (state.draftPlaceholders.isNotEmpty()) {
                Text(
                    "الحقول: ${state.draftPlaceholders.joinToString("، ") { "{$it}" }}",
                    style = FinancialTypography.metadata,
                )
            }
            PrimaryButton(
                if (saving) "جارٍ الحفظ…" else "حفظ النمط",
                enabled = !saving && state.draftTemplate.isNotBlank(),
                onClick = {
                    saving = true
                    scope.launch {
                        runCatching {
                            val built = MessageTemplateEngine.buildFromSms(state.selectedSmsBody)
                            val discovered = DiscoveredMessagePattern(
                                signature = built.signature,
                                friendlyNameHint = built.displayName,
                                messageCount = 1,
                                latestTimestamp = System.currentTimeMillis(),
                                sanitizedSamples = emptyList(),
                                suggestedFields = PatternDiscoveryService.suggestFields(
                                    com.baraa.masroof.transaction.LineBasedFieldParser
                                        .splitLines(state.selectedSmsBody.orEmpty())
                                        .map { it.label },
                                ),
                                looksLikeOtpOrMarketing = false,
                                typeKey = com.baraa.masroof.sms.MessageTypeCueCatalog.detect(state.selectedSmsBody).typeToken,
                                transactionTypeName = built.transactionType?.name,
                                direction = built.direction,
                                channel = built.channel,
                                templateText = built.templateText,
                                placeholders = built.placeholders,
                            )
                            app.messagePatternRepository.saveDiscovered(
                                senderProfileId = state.selectedSenderProfileId,
                                discovered = discovered,
                                status = MessagePatternStatus.APPROVED,
                            )
                            state.lastPatternCounts = OnboardingImportPlanner.countForTemplate(
                                built.templateText,
                                state.senderInbox,
                            )
                        }.onSuccess {
                            saveError = null
                            onSaved()
                        }.onFailure {
                            saveError = "تعذر حفظ النمط. حاول مرة أخرى."
                        }
                        saving = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun PatternSummaryStep(
    state: UiOnboardingState,
    onAddAnother: () -> Unit,
    onContinue: () -> Unit,
) {
    val counts = state.lastPatternCounts
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
        Text("تم حفظ النمط", style = MaterialTheme.typography.titleLarge)
        if (counts != null) {
            Text("الرسائل المطابقة: ${counts.matched}", style = MaterialTheme.typography.bodyLarge)
            Text("الرسائل التي لم يتم التعرف عليها: ${counts.unmatched}", style = MaterialTheme.typography.bodyLarge)
        }
        Text(
            "لا يلزم تعريف كل أنواع الرسائل الآن. الرسائل غير المعروفة تبقى بلا استيراد.",
            style = MaterialTheme.typography.bodyMedium,
        )
        PrimaryButton("متابعة", onClick = onContinue, modifier = Modifier.fillMaxWidth())
        SecondaryButton("إضافة نمط آخر", onClick = onAddAnother, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun SenderPatternSummaryStep(
    app: MasroofApplication,
    state: UiOnboardingState,
    onContinue: () -> Unit,
    onAddPattern: () -> Unit,
) {
    var patterns by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(state.selectedSenderProfileId) {
        patterns = app.messagePatternRepository.getForSender(state.selectedSenderProfileId)
            .filter { it.definition.status == MessagePatternStatus.APPROVED }
            .map { it.definition.userFriendlyName }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
        Text(state.selectedSenderDisplay.ifBlank { "المرسل" }, style = MaterialTheme.typography.titleLarge)
        Text("الأنماط المحفوظة:", style = MaterialTheme.typography.titleMedium)
        if (patterns.isEmpty()) {
            Text("لا توجد أنماط معتمدة بعد.", color = MaterialTheme.colorScheme.error)
        } else {
            patterns.forEach { name ->
                Text("✓ $name", style = MaterialTheme.typography.bodyLarge)
            }
        }
        Text(
            "الرسائل المطابقة للأنماط المعتمدة فقط يمكن استيرادها لاحقاً.",
            style = MaterialTheme.typography.bodyMedium,
        )
        PrimaryButton(
            "متابعة لإنشاء حساب",
            enabled = patterns.isNotEmpty(),
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
        )
        SecondaryButton("إضافة نمط", onClick = onAddPattern, modifier = Modifier.fillMaxWidth())
    }
}

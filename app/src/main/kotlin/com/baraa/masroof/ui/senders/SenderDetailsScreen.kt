package com.baraa.masroof.ui.senders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baraa.masroof.MasroofApplication
import com.baraa.masroof.data.db.MessagePatternStatus
import com.baraa.masroof.data.repository.MessagePattern
import com.baraa.masroof.data.repository.SenderProfile
import com.baraa.masroof.data.repository.TemplateStatusLabels
import com.baraa.masroof.sms.PatternDiscoveryService
import com.baraa.masroof.sms.SenderNormalizer
import com.baraa.masroof.sms.SmsImportRange
import com.baraa.masroof.transaction.TransactionType
import java.time.LocalDate
import com.baraa.masroof.transaction.TransactionTypeTaxonomy
import com.baraa.masroof.ui.TransactionTypeVisuals
import com.baraa.masroof.ui.theme.FinancialShapes
import com.baraa.masroof.ui.theme.FinancialTypography
import com.baraa.masroof.ui.theme.MasroofTopAppBar
import com.baraa.masroof.ui.theme.PrimaryButton
import com.baraa.masroof.ui.theme.SecondaryButton
import com.baraa.masroof.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tabs:
 * - القوالب → APPROVED templates only
 * - تحتاج اعتماد → UNKNOWN candidates only (never approved)
 * - الرسائل → SMS-linked pattern history
 *
 */
private enum class SenderDetailsTab {
    TEMPLATES,
    CANDIDATES,
    MESSAGES,
}

@Composable
fun SenderDetailsScreen(
    senderProfileId: Long,
    onBack: () -> Unit,
    onTemplateClick: (Long) -> Unit,
    onReturnToImport: (() -> Unit)? = null,
) {
    val app = LocalContext.current.applicationContext as MasroofApplication
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val returnToImport by app.importSessionStore.returnToImportAfterTemplates
        .collectAsStateWithLifecycle(initialValue = false)
    var profile by remember(senderProfileId) { mutableStateOf<SenderProfile?>(null) }
    var patterns by remember(senderProfileId) { mutableStateOf<List<MessagePattern>>(emptyList()) }
    var tab by remember { mutableStateOf(SenderDetailsTab.TEMPLATES) }
    var status by remember { mutableStateOf<String?>(null) }
    var expandedFamilyIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var ignoreTarget by remember { mutableStateOf<MessagePattern?>(null) }

    fun reload() {
        scope.launch {
            profile = withContext(Dispatchers.IO) {
                app.senderProfileRepository.getById(senderProfileId)
            }
            patterns = withContext(Dispatchers.IO) {
                app.messagePatternRepository.getForSender(senderProfileId)
            }.filter { it.definition.deprecatedAt == null }
        }
    }

    DisposableEffect(lifecycleOwner, senderProfileId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) reload()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        reload()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val title = profile?.displayInstitutionName ?: profile?.displaySender ?: "تفاصيل المرسل"
    val approvedTemplates = patterns.filter {
        TemplateStatusLabels.isApprovedTemplate(it.definition.status)
    }
    val candidatePatterns = patterns.filter {
        TemplateStatusLabels.isCandidate(it.definition.status) &&
            !it.definition.templateText.isNullOrBlank()
    }
    val messageCount = patterns.sumOf { it.definition.exampleCount.coerceAtLeast(0) }

    LaunchedEffect(candidatePatterns.size, returnToImport) {
        if (returnToImport && candidatePatterns.isNotEmpty() && tab == SenderDetailsTab.TEMPLATES) {
            tab = SenderDetailsTab.CANDIDATES
        }
    }

    Scaffold(topBar = { MasroofTopAppBar(title, onBack = onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(Spacing.x4)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.x3),
        ) {
            if (returnToImport && onReturnToImport != null) {
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = FinancialShapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Column(
                        Modifier.padding(Spacing.x3),
                        verticalArrangement = Arrangement.spacedBy(Spacing.x2),
                    ) {
                        Text(
                            "جلسة استيراد نشطة — اعتمد الأنماط ثم عد لمتابعة الاستيراد.",
                            style = FinancialTypography.merchant,
                        )
                        PrimaryButton(
                            "العودة إلى الاستيراد",
                            onClick = {
                                app.importSessionStore.markTemplatesChanged()
                                onReturnToImport()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SummaryValue(label = "قالب معتمد", value = approvedTemplates.size)
                SummaryValue(label = "رسالة", value = messageCount)
                SummaryValue(label = "نمط يحتاج اعتماد", value = candidatePatterns.size)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                FilterChip(
                    selected = tab == SenderDetailsTab.TEMPLATES,
                    onClick = { tab = SenderDetailsTab.TEMPLATES },
                    label = { Text("القوالب (${approvedTemplates.size})") },
                )
                FilterChip(
                    selected = tab == SenderDetailsTab.CANDIDATES,
                    onClick = { tab = SenderDetailsTab.CANDIDATES },
                    label = { Text("تحتاج اعتماد (${candidatePatterns.size})") },
                )
                FilterChip(
                    selected = tab == SenderDetailsTab.MESSAGES,
                    onClick = { tab = SenderDetailsTab.MESSAGES },
                    label = { Text("الرسائل ($messageCount)") },
                )
            }

            when (tab) {
                SenderDetailsTab.TEMPLATES -> {
                    if (approvedTemplates.isEmpty()) {
                        Text("لا توجد قوالب معتمدة بعد.", style = FinancialTypography.metadata)
                    } else {
                        approvedTemplates.groupBy { it.family?.id ?: -it.definition.id }
                            .toSortedMap()
                            .forEach { (familyId, variants) ->
                                val familyName = variants.first().family?.displayName
                                    ?: variants.first().definition.userFriendlyName
                                val messages = variants.sumOf { it.definition.exampleCount }
                                Surface(
                                    Modifier.fillMaxWidth().clickable {
                                        expandedFamilyIds = if (familyId in expandedFamilyIds) {
                                            expandedFamilyIds - familyId
                                        } else expandedFamilyIds + familyId
                                    },
                                    shape = FinancialShapes.medium,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                ) {
                                    Column(Modifier.padding(Spacing.x3)) {
                                        Text(familyName, style = FinancialTypography.merchant)
                                        Text(
                                            "$messages رسالة · ${variants.size} صيغة · معتمد",
                                            style = FinancialTypography.metadata,
                                        )
                                        if (familyId in expandedFamilyIds) {
                                            variants.sortedByDescending { it.definition.exampleCount }.forEachIndexed { index, pattern ->
                                                Text(
                                                    "الصيغة ${index + 1} — ${pattern.definition.exampleCount} رسالة",
                                                    style = FinancialTypography.metadata,
                                                    modifier = Modifier.padding(top = Spacing.x2),
                                                )
                                                ApprovedTemplateCard(
                                                    pattern = pattern,
                                                    onEdit = { onTemplateClick(pattern.definition.id) },
                                                    onDisable = {
                                                        scope.launch {
                                                            app.messagePatternRepository.setStatus(
                                                                pattern.definition.id,
                                                                MessagePatternStatus.IGNORED,
                                                            )
                                                            app.importSessionStore.markTemplatesChanged()
                                                            status = "تم تعطيل الصيغة"
                                                            reload()
                                                        }
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                    }
                }
                SenderDetailsTab.CANDIDATES -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                        SecondaryButton(
                            "إعادة بناء الأنماط",
                            onClick = {
                                scope.launch {
                                    val result = app.smsRepository.loadInboxResult(
                                        SmsImportRange.lastDays(LocalDate.now(), 30),
                                    )
                                    val inbox = (result as? com.baraa.masroof.sms.SmsInboxLoadResult.Success)
                                        ?.messages.orEmpty()
                                    val senderMessages = inbox.filter {
                                        SenderNormalizer.normalize(it.sender) == profile?.normalizedSenderKey
                                    }
                                    val summary = app.messagePatternRepository.rebuildForSender(
                                        senderProfileId,
                                        senderMessages,
                                    )
                                    app.importSessionStore.markTemplatesChanged()
                                    status = "أعيد بناء ${summary.rebuiltVariants} صيغة — ${summary.staleDeprecated} قديم تم تعطيله"
                                    reload()
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(Spacing.x2))
                    SecondaryButton(
                        "اكتشاف أنماط جديدة من آخر 30 يومًا",
                        onClick = {
                            scope.launch {
                                val result = app.smsRepository.loadInboxResult(
                                    SmsImportRange.lastDays(LocalDate.now(), 30),
                                )
                                val inbox = (result as? com.baraa.masroof.sms.SmsInboxLoadResult.Success)
                                    ?.messages.orEmpty()
                                val senderMessages = inbox.filter {
                                    SenderNormalizer.normalize(it.sender) == profile?.normalizedSenderKey
                                }
                                val discovered = PatternDiscoveryService.discover(
                                    senderMessages,
                                    app.messagePatternRepository.getForSender(senderProfileId)
                                        .map { it.definition },
                                )
                                discovered.filterNot { it.looksLikeOtpOrMarketing }.forEach { cluster ->
                                    app.messagePatternRepository.saveDiscovered(
                                        senderProfileId,
                                        cluster,
                                        MessagePatternStatus.UNKNOWN,
                                    )
                                }
                                status = "تم اكتشاف ${discovered.size} صيغة للمراجعة"
                                reload()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (candidatePatterns.isEmpty()) {
                        Text(
                            "لا توجد أنماط بانتظار الاعتماد. الأنماط المعتمدة تظهر في تبويب القوالب.",
                            style = FinancialTypography.metadata,
                        )
                    } else {
                        Text(
                            "${candidatePatterns.size} نمطاً مرشحاً — لم تُعتمد بعد ولا تُستخدم في الاستيراد.",
                            style = FinancialTypography.metadata,
                        )
                        candidatePatterns
                            .sortedByDescending { it.definition.exampleCount }
                            .forEach { pattern ->
                                CandidatePatternCard(
                                    pattern = pattern,
                                    senderLabel = profile?.displaySender.orEmpty(),
                                    onApprove = {
                                        scope.launch {
                                            val approved = app.messagePatternRepository.approveCandidate(
                                                pattern.definition.id,
                                            )
                                            android.util.Log.i(
                                                "TemplateLifecycle",
                                                "approved id=${approved?.definition?.id} status=${approved?.definition?.status} active=${approved?.definition?.isActive} key=${approved?.definition?.canonicalKey}",
                                            )
                                            app.importSessionStore.markTemplatesChanged()
                                            status = "تم اعتماد النمط"
                                            reload()
                                        }
                                    },
                                    onEdit = { onTemplateClick(pattern.definition.id) },
                                    onIgnore = { ignoreTarget = pattern },
                                    onUseOnce = {
                                        app.importSessionStore.markUseOncePattern(pattern.definition.id)
                                        status = "استخدام لمرة واحدة — ارجع للاستيراد لإعادة المطابقة دون حفظ قالب دائم."
                                        if (returnToImport && onReturnToImport != null) {
                                            onReturnToImport()
                                        }
                                    },
                                )
                            }
                    }
                }
                SenderDetailsTab.MESSAGES -> {
                    Text(
                        "$messageCount رسالة مرتبطة بأنماط هذا المرسل",
                        style = FinancialTypography.merchant,
                    )
                    patterns.sortedByDescending { it.definition.exampleCount }.forEach { pattern ->
                        val type = TransactionTypeTaxonomy.parse(pattern.definition.transactionType)
                            ?: TransactionType.OTHER_FINANCIAL
                        Surface(
                            Modifier.fillMaxWidth().clickable {
                                onTemplateClick(pattern.definition.id)
                            },
                            shape = FinancialShapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Row(
                                Modifier.padding(Spacing.x3),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.x3),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(TransactionTypeVisuals.icon(type), contentDescription = null)
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        pattern.definition.userFriendlyName.ifBlank {
                                            TransactionTypeVisuals.label(type)
                                        },
                                        style = FinancialTypography.merchant,
                                    )
                                    Text(
                                        "${pattern.definition.exampleCount} رسالة · ${TemplateStatusLabels.statusAr(pattern.definition.status)}",
                                        style = FinancialTypography.metadata,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            status?.let {
                Text(it, color = MaterialTheme.colorScheme.primary, style = FinancialTypography.metadata)
            }
        }
    }

    ignoreTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { ignoreTarget = null },
            title = { Text("تجاهل هذا النوع مستقبلاً؟") },
            text = {
                Text(
                    "اختر نطاق التجاهل. لن تُحذف الرسائل التاريخية.",
                    style = FinancialTypography.metadata,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            app.messagePatternRepository.setStatus(
                                target.definition.id,
                                MessagePatternStatus.IGNORED,
                            )
                            app.importSessionStore.markTemplatesChanged()
                            status = "تم تجاهل النمط مستقبلاً"
                            ignoreTarget = null
                            reload()
                        }
                    },
                ) { Text("تجاهل النمط مستقبلاً") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { ignoreTarget = null }) { Text("إلغاء") }
                    TextButton(
                        onClick = {
                            status = "تم تخطي هذا المرشح الآن — ما زال يحتاج اعتماد لاحقاً"
                            ignoreTarget = null
                        },
                    ) { Text("هذه الرسائل فقط") }
                }
            },
        )
    }
}

@Composable
private fun SummaryValue(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), style = FinancialTypography.financialTotal)
        Text(label, style = FinancialTypography.metadata)
    }
}

@Composable
private fun ApprovedTemplateCard(
    pattern: MessagePattern,
    onEdit: () -> Unit,
    onDisable: () -> Unit,
) {
    val type = TransactionTypeTaxonomy.parse(pattern.definition.transactionType)
        ?: TransactionType.OTHER_FINANCIAL
    Surface(
        Modifier.fillMaxWidth(),
        shape = FinancialShapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            Modifier.padding(Spacing.x3),
            verticalArrangement = Arrangement.spacedBy(Spacing.x2),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.x3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(TransactionTypeVisuals.icon(type), contentDescription = null)
                Column(Modifier.weight(1f)) {
                    Text(
                        pattern.definition.userFriendlyName.ifBlank {
                            TransactionTypeVisuals.label(type)
                        },
                        style = FinancialTypography.merchant,
                    )
                    Text(
                        "${pattern.definition.exampleCount} رسالة مطابقة",
                        style = FinancialTypography.metadata,
                    )
                }
                Text("معتمد", color = MaterialTheme.colorScheme.primary, style = FinancialTypography.metadata)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                SecondaryButton("تعديل", onClick = onEdit)
                SecondaryButton("تعطيل", onClick = onDisable)
            }
        }
    }
}

@Composable
private fun CandidatePatternCard(
    pattern: MessagePattern,
    senderLabel: String,
    onApprove: () -> Unit,
    onEdit: () -> Unit,
    onIgnore: () -> Unit,
    onUseOnce: () -> Unit,
) {
    val def = pattern.definition
    val type = TransactionTypeTaxonomy.parse(def.transactionType) ?: TransactionType.OTHER_FINANCIAL
    val sample = def.templateText.orEmpty().lineSequence().take(3).joinToString("\n").ifBlank { "—" }
    val fields = pattern.fields.take(4).joinToString(" · ") { field ->
        val token = field.placeholderToken.ifBlank { field.canonicalField.name }
        "{${token.trim('{', '}')}}"
    }
    Surface(
        Modifier.fillMaxWidth(),
        shape = FinancialShapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            Modifier.padding(Spacing.x3),
            verticalArrangement = Arrangement.spacedBy(Spacing.x2),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.x3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(TransactionTypeVisuals.icon(type), contentDescription = null)
                Column(Modifier.weight(1f)) {
                    Text(
                        def.userFriendlyName.ifBlank { TransactionTypeVisuals.label(type) },
                        style = FinancialTypography.merchant,
                    )
                    Text(
                        "${def.exampleCount} رسالة مطابقة · يحتاج اعتماد",
                        style = FinancialTypography.metadata,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    if (senderLabel.isNotBlank()) {
                        Text(senderLabel, style = FinancialTypography.metadata)
                    }
                }
            }
            Text(sample, style = FinancialTypography.metadata)
            if (fields.isNotBlank()) {
                Text(fields, style = FinancialTypography.metadata, color = MaterialTheme.colorScheme.primary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                PrimaryButton("اعتماد", onClick = onApprove)
                SecondaryButton("تعديل", onClick = onEdit)
                SecondaryButton("تجاهل", onClick = onIgnore)
            }
            TextButton(onClick = onUseOnce) {
                Text("استخدام مرة واحدة", style = FinancialTypography.metadata)
            }
        }
    }
}

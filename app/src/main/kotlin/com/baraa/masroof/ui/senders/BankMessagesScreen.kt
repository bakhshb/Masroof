package com.baraa.masroof.ui.senders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baraa.masroof.MasroofApplication
import com.baraa.masroof.data.db.MessagePatternStatus
import com.baraa.masroof.data.repository.MessagePattern
import com.baraa.masroof.data.repository.SenderProfile
import com.baraa.masroof.sms.DiscoveredMessagePattern
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

/**
 * Central hub: مرسلو الرسائل → اكتشاف/اعتماد الأنماط.
 * Separate from account creation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankMessagesScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as MasroofApplication
    val scope = rememberCoroutineScope()
    val profiles by app.senderProfileRepository.observeActive()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var unknownCount by remember { mutableStateOf(0) }
    var selectedProfile by remember { mutableStateOf<SenderProfile?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        unknownCount = app.messagePatternRepository.countUnknown()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("رسائل البنوك") },
                navigationIcon = {
                    SecondaryButton("رجوع", onClick = onBack)
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(Spacing.x4)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.x3),
        ) {
            Text(
                "علّم مرسل الرسائل وأنماط الرسائل هنا. إنشاء الحسابات وربط المعرفات يتم من شاشة الحسابات.",
                style = FinancialTypography.metadata,
            )
            if (unknownCount > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = FinancialShapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "تم اكتشاف $unknownCount نمطاً جديداً يحتاج مراجعة",
                        Modifier.padding(Spacing.x3),
                        style = FinancialTypography.merchant,
                    )
                }
            }
            Text("مرسلو الرسائل", style = FinancialTypography.merchant)
            if (profiles.isEmpty()) {
                Text("لم يتم تعليم أي مرسل بعد.", style = FinancialTypography.metadata)
            }
            profiles.forEach { entity ->
                val profile = SenderProfile(
                    id = entity.id,
                    displaySender = entity.displaySender,
                    normalizedSenderKey = entity.normalizedSenderKey,
                    displayInstitutionName = entity.displayInstitutionName,
                    active = entity.active,
                )
                Surface(
                    Modifier
                        .fillMaxWidth()
                        .clickable { selectedProfile = profile; status = null },
                    shape = FinancialShapes.medium,
                    tonalElevation = 1.dp,
                ) {
                    Column(Modifier.padding(Spacing.x3)) {
                        Text(
                            profile.displayInstitutionName?.let { "$it — ${profile.displaySender}" }
                                ?: profile.displaySender,
                            style = FinancialTypography.merchant,
                        )
                        Text(profile.normalizedSenderKey, style = FinancialTypography.metadata)
                    }
                }
            }
            Text("تعليم مرسل جديد", style = FinancialTypography.merchant)
            TrainNewSenderSection(
                onSaved = { profile ->
                    selectedProfile = profile
                    status = "تم حفظ المرسل ${profile.displaySender}"
                    scope.launch { unknownCount = app.messagePatternRepository.countUnknown() }
                },
            )
            selectedProfile?.let { profile ->
                SenderPatternManageSection(
                    profile = profile,
                    onStatus = { status = it },
                    onUnknownChanged = { scope.launch { unknownCount = app.messagePatternRepository.countUnknown() } },
                )
            }
            status?.let {
                Text(it, color = MaterialTheme.colorScheme.primary, style = FinancialTypography.metadata)
            }
        }
    }
}

@Composable
private fun TrainNewSenderSection(onSaved: (SenderProfile) -> Unit) {
    val app = LocalContext.current.applicationContext as MasroofApplication
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var senders by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var rangeDays by remember { mutableStateOf(30) }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7 to "آخر 7 أيام", 30 to "آخر 30 يومًا", 0 to "هذا الشهر").forEach { (d, label) ->
                FilterChip(
                    selected = rangeDays == d,
                    onClick = { rangeDays = d },
                    label = { Text(label) },
                )
            }
        }
        SecondaryButton(
            if (loading) "جارٍ التحميل…" else "عرض المرسلين من الرسائل",
            enabled = !loading,
            onClick = {
                loading = true
                scope.launch {
                    val today = LocalDate.now()
                    val range = when (rangeDays) {
                        0 -> SmsImportRange.default(today)
                        else -> SmsImportRange.lastDays(today, rangeDays)
                    }
                    val messages = runCatching { app.smsRepository.loadInbox(range) }.getOrDefault(emptyList())
                    senders = messages
                        .mapNotNull { it.sender?.trim()?.takeIf { s -> s.isNotBlank() } }
                        .groupingBy { it }
                        .eachCount()
                        .entries
                        .sortedByDescending { it.value }
                        .map { it.key to it.value }
                    loading = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        senders.take(40).forEach { (sender, count) ->
            SecondaryButton(
                "$sender • $count",
                onClick = {
                    scope.launch {
                        val profile = app.senderProfileRepository.upsertFromSmsSender(sender)
                        onSaved(profile)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SenderPatternManageSection(
    profile: SenderProfile,
    onStatus: (String) -> Unit,
    onUnknownChanged: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as MasroofApplication
    val scope = rememberCoroutineScope()
    var patterns by remember(profile.id) { mutableStateOf<List<MessagePattern>>(emptyList()) }
    var discovered by remember { mutableStateOf<List<DiscoveredMessagePattern>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<DiscoveredMessagePattern?>(null) }

    androidx.compose.runtime.LaunchedEffect(profile.id) {
        patterns = app.messagePatternRepository.getForSender(profile.id)
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
        Text(
            profile.displayInstitutionName?.let { "$it — ${profile.displaySender}" } ?: profile.displaySender,
            style = FinancialTypography.merchant,
        )
        SecondaryButton(
            if (loading) "جارٍ التحليل…" else "إعادة تحليل الرسائل (آخر 30 يومًا)",
            enabled = !loading,
            onClick = {
                loading = true
                scope.launch {
                    val today = LocalDate.now()
                    val range = SmsImportRange.lastDays(today, 30)
                    val all = runCatching { app.smsRepository.loadInbox(range) }.getOrDefault(emptyList())
                    val forSender = all.filter {
                        com.baraa.masroof.sms.SenderNormalizer.normalize(it.sender) == profile.normalizedSenderKey
                    }
                    discovered = PatternDiscoveryService.discover(forSender)
                    selected = discovered.filterNot { it.looksLikeOtpOrMarketing }.map { it.signature }.toSet()
                    loading = false
                    onStatus("تم العثور على ${discovered.size} نمطاً من ${forSender.size} رسالة")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        if (patterns.isNotEmpty()) {
            Text("الأنماط المحفوظة", style = FinancialTypography.merchant)
            patterns.forEach { p ->
                PatternStatusRow(p) { status ->
                    scope.launch {
                        app.messagePatternRepository.setStatus(p.definition.id, status)
                        patterns = app.messagePatternRepository.getForSender(profile.id)
                        onUnknownChanged()
                        onStatus("تم تحديث حالة النمط")
                    }
                }
            }
        }

        if (discovered.isNotEmpty()) {
            Text("تم العثور على ${discovered.size} أنماط", style = FinancialTypography.merchant)
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.x2),
            ) {
                discovered.forEach { cluster ->
                    val checked = cluster.signature in selected
                    Surface(
                        Modifier
                            .fillMaxWidth()
                            .clickable { detail = cluster },
                        shape = FinancialShapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Row(
                            Modifier.padding(Spacing.x3),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { on ->
                                    selected = if (on) selected + cluster.signature else selected - cluster.signature
                                },
                            )
                            Column {
                                Text(
                                    markPrefix(cluster) + cluster.friendlyNameHint,
                                    style = FinancialTypography.merchant,
                                )
                                Text("${cluster.messageCount} رسالة مشابهة", style = FinancialTypography.metadata)
                            }
                        }
                    }
                }
            }
            PrimaryButton(
                "اعتماد الأنماط المحددة (${selected.size})",
                enabled = selected.isNotEmpty(),
                onClick = {
                    scope.launch {
                        for (cluster in discovered.filter { it.signature in selected }) {
                            val status = if (cluster.looksLikeOtpOrMarketing) {
                                MessagePatternStatus.IGNORED
                            } else {
                                MessagePatternStatus.APPROVED
                            }
                            app.messagePatternRepository.saveDiscovered(
                                senderProfileId = profile.id,
                                discovered = cluster,
                                status = status,
                            )
                        }
                        // Unselected discovered styles that look financial → leave as UNKNOWN if saved later.
                        patterns = app.messagePatternRepository.getForSender(profile.id)
                        onUnknownChanged()
                        onStatus("تم حفظ الأنماط. يمكن استيراد الرسائل المطابقة من شاشة الاستيراد.")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            SecondaryButton(
                "تجاهل غير المحدد كنمط غير مالي",
                onClick = {
                    scope.launch {
                        for (cluster in discovered.filter { it.signature !in selected }) {
                            app.messagePatternRepository.saveDiscovered(
                                senderProfileId = profile.id,
                                discovered = cluster,
                                status = MessagePatternStatus.IGNORED,
                            )
                        }
                        patterns = app.messagePatternRepository.getForSender(profile.id)
                        onStatus("تم تجاهل الأنماط غير المحددة")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        detail?.let { cluster ->
            Surface(Modifier.fillMaxWidth(), shape = FinancialShapes.medium, tonalElevation = 2.dp) {
                Column(Modifier.padding(Spacing.x3), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(cluster.friendlyNameHint, style = FinancialTypography.merchant)
                    cluster.sanitizedSamples.forEach { sample ->
                        Text(sample, style = FinancialTypography.metadata)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton("اعتماد", onClick = {
                            scope.launch {
                                app.messagePatternRepository.saveDiscovered(
                                    profile.id,
                                    cluster,
                                    MessagePatternStatus.APPROVED,
                                )
                                patterns = app.messagePatternRepository.getForSender(profile.id)
                                detail = null
                                onUnknownChanged()
                            }
                        })
                        SecondaryButton("تجاهل", onClick = {
                            scope.launch {
                                app.messagePatternRepository.saveDiscovered(
                                    profile.id,
                                    cluster,
                                    MessagePatternStatus.IGNORED,
                                )
                                patterns = app.messagePatternRepository.getForSender(profile.id)
                                detail = null
                            }
                        })
                        SecondaryButton("إغلاق", onClick = { detail = null })
                    }
                }
            }
        }
    }
}

@Composable
private fun PatternStatusRow(pattern: MessagePattern, onSet: (MessagePatternStatus) -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(
            "${pattern.definition.userFriendlyName} — ${statusAr(pattern.definition.status)}",
            style = FinancialTypography.metadata,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (pattern.definition.status != MessagePatternStatus.APPROVED) {
                FilterChip(false, { onSet(MessagePatternStatus.APPROVED) }, label = { Text("اعتماد") })
            }
            if (pattern.definition.status != MessagePatternStatus.IGNORED) {
                FilterChip(false, { onSet(MessagePatternStatus.IGNORED) }, label = { Text("تجاهل") })
            }
            if (pattern.definition.status == MessagePatternStatus.UNKNOWN) {
                FilterChip(false, { onSet(MessagePatternStatus.DEPRECATED) }, label = { Text("قديم") })
            }
        }
    }
}

private fun markPrefix(cluster: DiscoveredMessagePattern): String = when {
    cluster.looksLikeOtpOrMarketing -> "○ "
    else -> "✓ "
}

private fun statusAr(status: MessagePatternStatus): String = when (status) {
    MessagePatternStatus.APPROVED -> "معتمد"
    MessagePatternStatus.IGNORED -> "متجاهل"
    MessagePatternStatus.UNKNOWN -> "جديد"
    MessagePatternStatus.DEPRECATED -> "قديم"
}

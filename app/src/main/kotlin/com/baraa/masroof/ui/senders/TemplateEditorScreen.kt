package com.baraa.masroof.ui.senders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.baraa.masroof.MasroofApplication
import com.baraa.masroof.data.db.MessagePatternStatus
import com.baraa.masroof.data.db.PatternCanonicalField
import com.baraa.masroof.data.db.PatternFieldRole
import com.baraa.masroof.data.db.PatternValueType
import com.baraa.masroof.data.repository.MessagePattern
import com.baraa.masroof.data.repository.MessagePatternRepository
import com.baraa.masroof.sms.TemplateEditDraft
import com.baraa.masroof.sms.TemplateEditValidation
import com.baraa.masroof.sms.TemplateEditValidator
import com.baraa.masroof.sms.TemplateFieldDraft
import com.baraa.masroof.transaction.MoneyFlowDirection
import com.baraa.masroof.transaction.TransactionType
import com.baraa.masroof.transaction.TransactionTypeTaxonomy
import com.baraa.masroof.ui.TransactionTypeVisuals
import com.baraa.masroof.ui.theme.FinancialShapes
import com.baraa.masroof.ui.theme.FinancialTypography
import com.baraa.masroof.ui.theme.MasroofTopAppBar
import com.baraa.masroof.ui.theme.PrimaryButton
import com.baraa.masroof.ui.theme.SecondaryButton
import com.baraa.masroof.ui.theme.Spacing
import kotlinx.coroutines.launch

@Composable
fun TemplateEditorScreen(
    patternId: Long,
    onBack: () -> Unit,
    onReturnToImport: (() -> Unit)? = null,
    draft: com.baraa.masroof.sms.PatternDraft? = null,
) {
    // Draft path (manual creation): no Room row exists yet. Skip the lookup and
    // present the in-memory draft directly; nothing is persisted until the user
    // confirms in the editor.
    if (patternId == 0L && draft != null) {
        ManualTemplateEditor(
            draft = draft,
            onBack = onBack,
            onReturnToImport = onReturnToImport,
        )
        return
    }
    val app = LocalContext.current.applicationContext as MasroofApplication
    var pattern by remember(patternId) { mutableStateOf<MessagePattern?>(null) }
    var missing by remember(patternId) { mutableStateOf(false) }
    LaunchedEffect(patternId) {
        pattern = app.messagePatternRepository.getById(patternId)
        missing = pattern == null
    }
    pattern?.let {
        LoadedTemplateEditor(
            pattern = it,
            onBack = onBack,
            onReturnToImport = onReturnToImport,
        )
    } ?: Scaffold(topBar = { MasroofTopAppBar("تعديل النمط", onBack = onBack) }) { padding ->
        Text(
            if (missing) "النمط غير موجود" else "جارٍ تحميل النمط…",
            Modifier.padding(padding).padding(Spacing.x4),
        )
    }
}

/**
 * Manual-creation editor entry: presents an UNPERSISTED [com.baraa.masroof.sms.PatternDraft]
 * built by [com.baraa.masroof.sms.PatternDraftFactory]. The user reviews the
 * template/fields/type and confirms. Only then is exactly one pattern row
 * written via [MessagePatternRepository.createPatternFromDraft] — never an
 * UNKNOWN-then-revision double write.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualTemplateEditor(
    draft: com.baraa.masroof.sms.PatternDraft,
    onBack: () -> Unit,
    onReturnToImport: (() -> Unit)? = null,
) {
    val app = LocalContext.current.applicationContext as MasroofApplication
    val returnToImport = app.importSessionStore.isReturnToImportActive()
    TemplateEditorContent(
        editorKey = "draft-${draft.bodyHash}",
        initialDraft = draft.templateEditDraft,
        sanitizedExample = draft.sanitizedExample,
        senderLabel = null,
        isNewDraft = true,
        returnToImport = returnToImport,
        onBack = onBack,
        onReturnToImport = onReturnToImport,
        onSave = { currentDraft, approve ->
            app.messagePatternRepository.createPatternFromDraft(currentDraft, approve)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoadedTemplateEditor(
    pattern: MessagePattern,
    onBack: () -> Unit,
    onReturnToImport: (() -> Unit)? = null,
) {
    val app = LocalContext.current.applicationContext as MasroofApplication
    val returnToImport = app.importSessionStore.isReturnToImportActive()
    val def = pattern.definition
    val initialType = TransactionTypeTaxonomy.parse(def.transactionType) ?: TransactionType.OTHER_FINANCIAL
    val initialDraft = TemplateEditDraft(
        patternId = def.id,
        senderProfileId = def.senderProfileId,
        displayName = def.userFriendlyName,
        transactionType = initialType,
        direction = TransactionTypeTaxonomy.parseDirection(def.direction, initialType),
        templateText = def.templateText.orEmpty(),
        status = def.status,
        active = def.isActive,
        fields = pattern.fields.map {
            TemplateFieldDraft(
                placeholderToken = it.placeholderToken.ifBlank {
                    com.baraa.masroof.sms.TemplateResolutionService.defaultPlaceholder(it.canonicalField)
                },
                canonicalField = it.canonicalField,
                sourceLabel = it.sourceLabel,
                role = it.role,
                valueType = it.valueType,
                required = it.required,
            )
        },
    )
    TemplateEditorContent(
        editorKey = def.id,
        initialDraft = initialDraft,
        sanitizedExample = null,
        senderLabel = null,
        isNewDraft = false,
        returnToImport = returnToImport,
        onBack = onBack,
        onReturnToImport = onReturnToImport,
        onSave = { currentDraft, approve ->
            if (approve) {
                app.messagePatternRepository.saveAndApproveEditedCandidate(currentDraft)
            } else {
                app.messagePatternRepository.updateTemplate(currentDraft)
            }
        },
    )
}

/**
 * Shared editor form for both the existing-pattern edit path and the manual
 * draft-creation path. Holds the editable state, validates, and delegates
 * persistence to [onSave] so the two paths share ONE confirmation screen
 * without duplicating the form.
 *
 * For a new manual draft ([isNewDraft] = true) nothing is persisted until the
 * user presses a save button; [onSave] then writes exactly one row via
 * [MessagePatternRepository.createPatternFromDraft].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplateEditorContent(
    editorKey: Any,
    initialDraft: TemplateEditDraft,
    sanitizedExample: String?,
    senderLabel: String?,
    isNewDraft: Boolean,
    returnToImport: Boolean,
    onBack: () -> Unit,
    onReturnToImport: (() -> Unit)?,
    onSave: suspend (
        currentDraft: TemplateEditDraft,
        approve: Boolean,
    ) -> MessagePatternRepository.TemplateUpdateResult,
) {
    val app = LocalContext.current.applicationContext as MasroofApplication
    val scope = rememberCoroutineScope()
    val isUnknownCandidate = isNewDraft || initialDraft.status == MessagePatternStatus.UNKNOWN
    var displayName by remember(editorKey) { mutableStateOf(initialDraft.displayName) }
    var selectedType by remember(editorKey) { mutableStateOf(initialDraft.transactionType) }
    var direction by remember(editorKey) { mutableStateOf(initialDraft.direction) }
    var templateText by remember(editorKey) { mutableStateOf(initialDraft.templateText) }
    var status by remember(editorKey) { mutableStateOf(initialDraft.status) }
    var active by remember(editorKey) { mutableStateOf(initialDraft.active) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showFullTemplate by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    var showTypePicker by remember { mutableStateOf(false) }
    var editingFieldIndex by remember { mutableStateOf<Int?>(null) }
    var fields by remember(editorKey, initialDraft.fields) {
        mutableStateOf(initialDraft.fields)
    }

    fun draft() = TemplateEditDraft(
        patternId = initialDraft.patternId,
        senderProfileId = initialDraft.senderProfileId,
        displayName = displayName,
        transactionType = selectedType,
        direction = direction,
        templateText = templateText,
        status = status,
        active = active,
        fields = fields,
    )

    fun finishAfterSave(savedPatternId: Long?, approve: Boolean) {
        try {
            app.importSessionStore.markTemplatesChanged()
            if (!returnToImport) {
                val message = when {
                    isNewDraft && approve -> "تم اعتماد النمط وسيُستخدم في الاستيراد"
                    isNewDraft && !approve -> "تم إنشاء النمط كمرشح — يحتاج اعتماد"
                    approve -> "تم اعتماد النمط وسيُستخدم في الاستيراد"
                    else -> "تم حفظ التغييرات"
                }
                PatternActionResultHolder.set(message, reviewPatternId = savedPatternId)
            }
            if (returnToImport && onReturnToImport != null) {
                onReturnToImport()
            } else {
                onBack()
            }
        } catch (t: Throwable) {
            android.util.Log.e("TemplateEditor", "navigation after save failed id=$savedPatternId", t)
            error = "تم الحفظ لكن تعذر الرجوع: ${t.message ?: t.javaClass.simpleName}"
        }
    }

    fun save(approve: Boolean) {
        val value = draft()
        when (val validation = TemplateEditValidator.validate(value)) {
            is TemplateEditValidation.Error -> error = validation.messageAr
            TemplateEditValidation.Ok -> scope.launch {
                saving = true
                error = null
                try {
                    val result = onSave(value, approve)
                    when (result) {
                        is MessagePatternRepository.TemplateUpdateResult.Success -> {
                            val newId = result.pattern.definition.id
                            saving = false
                            finishAfterSave(newId, approve)
                            return@launch
                        }
                        is MessagePatternRepository.TemplateUpdateResult.ValidationError ->
                            error = result.messageAr
                        MessagePatternRepository.TemplateUpdateResult.NotFound ->
                            error = "النمط غير موجود"
                        MessagePatternRepository.TemplateUpdateResult.SenderNotFound ->
                            error = "المرسل غير موجود"
                        MessagePatternRepository.TemplateUpdateResult.SenderInactive ->
                            error = "المرسل غير نشط"
                        is MessagePatternRepository.TemplateUpdateResult.CanonicalCollision ->
                            error = "يوجد نمط آخر بنفس البنية"
                        is MessagePatternRepository.TemplateUpdateResult.Failure ->
                            error = result.messageAr
                    }
                } catch (t: Throwable) {
                    android.util.Log.e("TemplateEditor", "save crashed", t)
                    error = "تعذر حفظ النمط: ${t.message ?: t.javaClass.simpleName}"
                }
                saving = false
            }
        }
    }

    Scaffold(
        topBar = {
            MasroofTopAppBar(
                title = if (isNewDraft) "إنشاء نمط" else "تعديل النمط",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { save(approve = false) }, enabled = !saving) {
                        Icon(Icons.Filled.Save, contentDescription = "حفظ")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(Spacing.x4)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.x4),
        ) {
            if (returnToImport) {
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = FinancialShapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        "بعد الحفظ/الاعتماد ستُعاد مطابقة رسائل جلسة الاستيراد النشطة.",
                        Modifier.padding(Spacing.x3),
                        style = FinancialTypography.metadata,
                    )
                }
            }
            senderLabel?.takeIf { it.isNotBlank() }?.let {
                EditorSection("المرسل") {
                    Text(it, style = FinancialTypography.metadata)
                }
            }
            EditorSection("المعلومات الأساسية") {
                OutlinedTextField(
                    displayName,
                    onValueChange = { displayName = it },
                    label = { Text("اسم النمط") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Text("نوع العملية", style = FinancialTypography.metadata)
                Surface(
                    Modifier.fillMaxWidth().clickable { showTypePicker = true },
                    shape = FinancialShapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Row(
                        Modifier.padding(Spacing.x3),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.x2),
                    ) {
                        Icon(TransactionTypeVisuals.icon(selectedType), contentDescription = null)
                        Text(TransactionTypeVisuals.label(selectedType), Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
                    }
                }
                Text("اتجاه الأموال", style = FinancialTypography.metadata)
                Text(TransactionTypeVisuals.directionLabel(direction), style = FinancialTypography.merchant)
                if (selectedType == TransactionType.OTHER_FINANCIAL) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                        MoneyFlowDirection.entries.forEach { item ->
                            FilterChip(
                                selected = direction == item,
                                onClick = { direction = item },
                                label = { Text(TransactionTypeVisuals.directionLabel(item)) },
                            )
                        }
                    }
                }
            }

            EditorSection("بنية الرسالة") {
                val preview = templateText.lineSequence().take(4).joinToString("\n").ifBlank { "—" }
                Text(preview, style = FinancialTypography.metadata, maxLines = 4)
                TextButton(onClick = { showFullTemplate = !showFullTemplate }) {
                    Text(if (showFullTemplate) "إخفاء النص الكامل" else "عرض النص الكامل")
                }
                if (showFullTemplate) {
                    OutlinedTextField(
                        templateText,
                        onValueChange = { templateText = it },
                        label = { Text("نص بنية النمط") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                    )
                }
            }

            sanitizedExample?.takeIf { it.isNotBlank() }?.let { sample ->
                EditorSection("مثال منقّح") {
                    Text(sample, style = FinancialTypography.metadata, maxLines = 6)
                }
            }

            EditorSection("الحقول المكتشفة") {
                fields.forEachIndexed { index, field ->
                    FieldSummaryRow(
                        field = field,
                        onClick = { editingFieldIndex = index },
                    )
                }
                TextButton(onClick = {
                    val token = generateSequence(1) { it + 1 }.map { "FIELD_$it" }
                        .first { candidate -> fields.none { it.placeholderToken == candidate } }
                    fields = fields + TemplateFieldDraft(
                        token, PatternCanonicalField.MERCHANT, "حقل",
                        PatternFieldRole.PRIMARY, PatternValueType.TEXT, false,
                    )
                    editingFieldIndex = fields.lastIndex
                }) { Text("إضافة حقل") }
            }

            if (!isNewDraft) {
                TextButton(onClick = { showAdvanced = !showAdvanced }) {
                    Text(if (showAdvanced) "إخفاء الخيارات المتقدمة" else "خيارات متقدمة")
                }
                if (showAdvanced) {
                    EditorSection("خيارات المطابقة") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(active, onCheckedChange = { active = it })
                            Text("نشط ويُستخدم عند استيراد الرسائل")
                        }
                    }
                    EditorSection("الحالة") {
                        MessagePatternStatus.entries.forEach { item ->
                            FilterChip(
                                selected = status == item,
                                onClick = {
                                    status = item
                                    if (item != MessagePatternStatus.APPROVED) active = false
                                },
                                label = { Text(TransactionTypeVisuals.statusLabel(item)) },
                            )
                        }
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (isUnknownCandidate) {
                PrimaryButton(
                    label = if (saving) "جارٍ الحفظ…" else "حفظ واعتماد",
                    enabled = !saving,
                    onClick = { save(approve = true) },
                    modifier = Modifier.fillMaxWidth(),
                )
                SecondaryButton(
                    label = "حفظ كمرشح",
                    enabled = !saving,
                    onClick = { save(approve = false) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                PrimaryButton(
                    label = if (saving) "جارٍ الحفظ…" else "حفظ التغييرات",
                    enabled = !saving,
                    onClick = { save(approve = false) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (showTypePicker) {
        ModalBottomSheet(
            onDismissRequest = { showTypePicker = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                Modifier.padding(Spacing.x4).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.x2),
            ) {
                Text("نوع العملية", style = FinancialTypography.merchant)
                TransactionTypeTaxonomy.choosableTypes.forEach { type ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            selectedType = type
                            direction = TemplateEditValidator.derivedDirection(type)
                            showTypePicker = false
                        }.padding(vertical = Spacing.x2),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.x2),
                    ) {
                        Icon(TransactionTypeVisuals.icon(type), contentDescription = null)
                        Text(TransactionTypeVisuals.label(type))
                    }
                }
            }
        }
    }

    val editIndex = editingFieldIndex
    if (editIndex != null && editIndex in fields.indices) {
        ModalBottomSheet(
            onDismissRequest = { editingFieldIndex = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            FieldEditorSheet(
                field = fields[editIndex],
                onChange = { updated ->
                    fields = fields.toMutableList().also { it[editIndex] = updated }
                },
                onRemove = {
                    fields = fields.filterIndexed { i, _ -> i != editIndex }
                    editingFieldIndex = null
                },
                onDone = { editingFieldIndex = null },
            )
        }
    }
}
@Composable
private fun EditorSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
        Text(title, style = FinancialTypography.merchant)
        content()
    }
}

@Composable
private fun FieldSummaryRow(field: TemplateFieldDraft, onClick: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = FinancialShapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            Modifier.padding(Spacing.x3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.x3),
        ) {
            Icon(fieldIcon(field.canonicalField), contentDescription = null)
            Column(Modifier.weight(1f)) {
                Text(canonicalFieldLabel(field.canonicalField), style = FinancialTypography.merchant)
                Text(field.placeholderToken.ifBlank { field.canonicalField.name }, style = FinancialTypography.metadata)
                Text(if (field.required) "مطلوب" else "اختياري", style = FinancialTypography.metadata)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "تعديل")
        }
    }
}

@Composable
private fun FieldEditorSheet(
    field: TemplateFieldDraft,
    onChange: (TemplateFieldDraft) -> Unit,
    onRemove: () -> Unit,
    onDone: () -> Unit,
) {
    Column(
        Modifier.padding(Spacing.x4).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.x3),
    ) {
        Text("تعديل الحقل", style = FinancialTypography.merchant)
        OutlinedTextField(
            field.sourceLabel,
            onValueChange = { onChange(field.copy(sourceLabel = it)) },
            label = { Text("التسمية في الرسالة") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Text("المعنى الدلالي", style = FinancialTypography.metadata)
        PatternCanonicalField.entries.forEach { item ->
            FilterChip(
                selected = field.canonicalField == item,
                onClick = {
                    onChange(
                        field.copy(
                            canonicalField = item,
                            valueType = expectedValueType(item),
                            placeholderToken = field.placeholderToken.ifBlank {
                                com.baraa.masroof.sms.TemplateResolutionService.defaultPlaceholder(item)
                            },
                        ),
                    )
                },
                label = { Text(canonicalFieldLabel(item)) },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(field.required, onCheckedChange = { onChange(field.copy(required = it)) })
            Text(if (field.required) "مطلوب" else "اختياري")
        }
        TextButton(onClick = onRemove) { Text("إزالة الحقل") }
        TextButton(onClick = onDone) { Text("تم") }
    }
}

private fun fieldIcon(field: PatternCanonicalField): ImageVector = when (field) {
    PatternCanonicalField.CREDIT_CARD_LAST4,
    PatternCanonicalField.DEBIT_CARD_LAST4,
    -> Icons.Filled.CreditCard
    PatternCanonicalField.MERCHANT, PatternCanonicalField.BENEFICIARY -> Icons.Filled.Store
    PatternCanonicalField.TRANSACTION_AMOUNT -> Icons.Filled.Payments
    PatternCanonicalField.AVAILABLE_BALANCE -> Icons.Filled.AccountBalanceWallet
    PatternCanonicalField.CARD_AMOUNT_DUE -> Icons.Filled.Receipt
    else -> Icons.Filled.Receipt
}

private fun canonicalFieldLabel(field: PatternCanonicalField): String = when (field) {
    PatternCanonicalField.TRANSACTION_AMOUNT -> "مبلغ العملية"
    PatternCanonicalField.CURRENCY -> "العملة"
    PatternCanonicalField.MERCHANT -> "التاجر"
    PatternCanonicalField.BENEFICIARY -> "المستفيد"
    PatternCanonicalField.TRANSACTION_DATE -> "تاريخ العملية"
    PatternCanonicalField.TRANSACTION_TIME -> "وقت العملية"
    PatternCanonicalField.AVAILABLE_BALANCE -> "الرصيد المتاح"
    PatternCanonicalField.CARD_AMOUNT_DUE -> "المبلغ المستحق"
    PatternCanonicalField.TRANSACTION_REFERENCE -> "مرجع العملية"
    PatternCanonicalField.SOURCE_INSTITUTION -> "جهة المصدر"
    PatternCanonicalField.DESTINATION_INSTITUTION -> "جهة الوجهة"
    PatternCanonicalField.CHANNEL -> "قناة العملية"
    PatternCanonicalField.ACCOUNT_LAST4 -> "آخر 4 من الحساب"
    PatternCanonicalField.SOURCE_ACCOUNT_LAST4 -> "آخر 4 من حساب المصدر"
    PatternCanonicalField.DESTINATION_ACCOUNT_LAST4 -> "آخر 4 من حساب الوجهة"
    PatternCanonicalField.CREDIT_CARD_LAST4 -> "بطاقة ائتمانية"
    PatternCanonicalField.DEBIT_CARD_LAST4 -> "بطاقة خصم"
    PatternCanonicalField.IBAN_LAST4 -> "آخر 4 من الآيبان"
    PatternCanonicalField.SOURCE_IBAN_LAST4 -> "آخر 4 من آيبان المصدر"
    PatternCanonicalField.DESTINATION_IBAN_LAST4 -> "آخر 4 من آيبان الوجهة"
    PatternCanonicalField.WALLET_LAST4 -> "آخر 4 من المحفظة"
}

private fun expectedValueType(field: PatternCanonicalField): PatternValueType = when (field) {
    PatternCanonicalField.TRANSACTION_AMOUNT,
    PatternCanonicalField.AVAILABLE_BALANCE,
    PatternCanonicalField.CARD_AMOUNT_DUE,
    -> PatternValueType.MONEY
    PatternCanonicalField.ACCOUNT_LAST4,
    PatternCanonicalField.SOURCE_ACCOUNT_LAST4,
    PatternCanonicalField.DESTINATION_ACCOUNT_LAST4,
    PatternCanonicalField.CREDIT_CARD_LAST4,
    PatternCanonicalField.DEBIT_CARD_LAST4,
    PatternCanonicalField.IBAN_LAST4,
    PatternCanonicalField.SOURCE_IBAN_LAST4,
    PatternCanonicalField.DESTINATION_IBAN_LAST4,
    PatternCanonicalField.WALLET_LAST4,
    -> PatternValueType.LAST4
    PatternCanonicalField.TRANSACTION_DATE -> PatternValueType.DATE
    PatternCanonicalField.TRANSACTION_TIME -> PatternValueType.TIME
    else -> PatternValueType.TEXT
}

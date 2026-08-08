package com.baraa.masroof.data.repository

import com.baraa.masroof.sms.SmsMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.util.UUID

/**
 * Authoritative in-process import session shared across Scan → Review → Templates → Import.
 *
 * Scan results must not live only inside a screen-local `remember` block:
 * navigating to Review or pattern approval previously lost that state while
 * other screens queried a different source, producing empty queues / zero imports.
 */
data class ImportSession(
    val id: String = UUID.randomUUID().toString(),
    val preview: ScanPreview,
    val messages: List<SmsMessage>,
    val trackingStartDate: LocalDate?,
    val mode: SmsImportMode,
    val createdAtMillis: Long = System.currentTimeMillis(),
) {
    val readyToImport: Int get() = preview.readyToImport
    val needsMessageReview: Int get() = preview.messageReviewCount
    /** Distinct candidate patterns — not the unresolved SMS message count. */
    val needsPatternApproval: Int get() = preview.patternsNeedingApproval
    /** SMS messages blocked on pattern approval. */
    val patternGateMessages: Int get() = preview.patternApprovalCount
    val duplicate: Int get() = preview.duplicate
    val totalSms: Int get() = preview.totalSms

    fun withPreview(preview: ScanPreview): ImportSession = copy(preview = preview)
}

/**
 * Process-scoped session store. Survives Compose navigation; cleared on
 * successful full import or explicit cancel. Not a Room table — SMS bodies
 * stay in memory only for the active import session.
 *
 * Navigation context:
 * - [beginTemplateApprovalFromImport] marks that Templates was opened from Import.
 * - After approve/edit, [markTemplatesChanged] flags the session dirty.
 * - Import screen reprocesses unresolved candidates and clears the dirty flag.
 * - Normal Bank Messages browsing does not set return-to-import.
 */
class ImportSessionStore {
    private val _session = MutableStateFlow<ImportSession?>(null)
    val session: StateFlow<ImportSession?> = _session.asStateFlow()

    private val _returnToImportAfterTemplates = MutableStateFlow(false)
    val returnToImportAfterTemplates: StateFlow<Boolean> = _returnToImportAfterTemplates.asStateFlow()

    private val _templatesDirty = MutableStateFlow(false)
    val templatesDirty: StateFlow<Boolean> = _templatesDirty.asStateFlow()

    private val _reprocessing = MutableStateFlow(false)
    val reprocessing: StateFlow<Boolean> = _reprocessing.asStateFlow()

    /** Pattern IDs approved for one-shot matching without becoming permanent templates. */
    private val _useOncePatternIds = MutableStateFlow<Set<Long>>(emptySet())
    val useOncePatternIds: StateFlow<Set<Long>> = _useOncePatternIds.asStateFlow()

    fun current(): ImportSession? = _session.value

    fun replace(session: ImportSession) {
        _session.value = session
    }

    fun updatePreview(preview: ScanPreview) {
        val current = _session.value ?: return
        _session.value = current.withPreview(preview)
    }

    /** Call when user opens pattern review from an active Import scan. */
    fun beginTemplateApprovalFromImport() {
        if (_session.value != null) {
            _returnToImportAfterTemplates.value = true
        }
    }

    fun isReturnToImportActive(): Boolean =
        _returnToImportAfterTemplates.value && _session.value != null

    fun clearReturnToImport() {
        _returnToImportAfterTemplates.value = false
    }

    /** Call after any template approve/edit/ignore that may affect matching. */
    fun markTemplatesChanged() {
        if (_session.value != null) {
            _templatesDirty.value = true
        }
    }

    fun consumeTemplatesDirty(): Boolean {
        val dirty = _templatesDirty.value
        if (dirty) _templatesDirty.value = false
        return dirty
    }

    fun setReprocessing(value: Boolean) {
        _reprocessing.value = value
    }

    fun markUseOncePattern(patternId: Long) {
        _useOncePatternIds.value = _useOncePatternIds.value + patternId
        markTemplatesChanged()
    }

    fun clearUseOncePatterns() {
        _useOncePatternIds.value = emptySet()
    }

    fun clear() {
        _session.value = null
        _returnToImportAfterTemplates.value = false
        _templatesDirty.value = false
        _reprocessing.value = false
        _useOncePatternIds.value = emptySet()
    }
}

/** Human-readable labels for import / message dispositions (never template status). */
object ImportMessageLabels {
    fun dispositionAr(disposition: ImportDisposition): String = when (disposition) {
        ImportDisposition.READY -> "جاهزة للاستيراد"
        ImportDisposition.NEEDS_ACCOUNT,
        ImportDisposition.NEEDS_CONFIRMATION,
        ImportDisposition.NEEDS_INSTITUTION,
        -> "تحتاج مراجعة"
        ImportDisposition.UNMATCHED_TEMPLATE -> "غير مطابقة لنمط"
        ImportDisposition.AMBIGUOUS_TEMPLATE -> "مطابقة غامضة"
        ImportDisposition.TEMPLATE_EXTRACTION_FAILED -> "فشل استخراج البيانات"
        ImportDisposition.EXACT_DUPLICATE,
        ImportDisposition.POSSIBLE_DUPLICATE,
        -> "مستوردة / مكررة"
        ImportDisposition.BEFORE_TRACKING_START -> "أقدم من بداية المتابعة"
        ImportDisposition.UNREGISTERED_SENDER -> "مرسل غير مسجّل"
        ImportDisposition.IGNORED -> "مستبعدة"
        ImportDisposition.UNPARSED -> "فشل استخراج البيانات"
    }
}

/** Template approval wording — never reuse message-review phrasing. */
object TemplateStatusLabels {
    fun statusAr(status: com.baraa.masroof.data.db.MessagePatternStatus): String = when (status) {
        com.baraa.masroof.data.db.MessagePatternStatus.APPROVED -> "معتمد"
        // UNKNOWN is the persisted CandidatePattern state (needs user decision).
        com.baraa.masroof.data.db.MessagePatternStatus.UNKNOWN -> "يحتاج اعتماد"
        com.baraa.masroof.data.db.MessagePatternStatus.IGNORED -> "غير نشط"
        com.baraa.masroof.data.db.MessagePatternStatus.DEPRECATED -> "مسودة"
    }

    /** CandidatePattern == UNKNOWN (not yet an ApprovedTemplate). */
    fun isCandidate(status: com.baraa.masroof.data.db.MessagePatternStatus): Boolean =
        status == com.baraa.masroof.data.db.MessagePatternStatus.UNKNOWN

    fun isApprovedTemplate(status: com.baraa.masroof.data.db.MessagePatternStatus): Boolean =
        status == com.baraa.masroof.data.db.MessagePatternStatus.APPROVED
}

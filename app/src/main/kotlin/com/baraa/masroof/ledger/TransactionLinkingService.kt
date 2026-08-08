package com.baraa.masroof.ledger

import com.baraa.masroof.data.db.AccountIdentifierType
import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.data.repository.AccountIdentifierRepository
import com.baraa.masroof.data.repository.IdentifierAddOutcome
import com.baraa.masroof.data.repository.IdentifierAddResult
import com.baraa.masroof.data.repository.TransactionRepository
import com.baraa.masroof.transaction.FinancialTreatment

/**
 * Outcome of [TransactionLinkingService.applyUserLink].
 * Never throws for recoverable validation / DB issues — callers must handle every branch.
 */
sealed class LinkApplyResult {
    data class Success(
        val transaction: TransactionEntity,
        val identifierOutcome: IdentifierAddOutcome? = null,
    ) : LinkApplyResult()

    data class ValidationError(
        val code: String,
        val messageAr: String,
    ) : LinkApplyResult()

    data class Failure(
        val messageAr: String,
        val cause: Throwable? = null,
    ) : LinkApplyResult()
}

/** Persistence boundary for draft replace + post used by manual linking. */
interface JournalLinkWriter {
    suspend fun replaceDraft(transactionId: Long, draft: JournalDraft): Long
    suspend fun post(journalId: Long): LedgerValidation
    /** Remove non-posted drafts for a transaction (rollback after a failed link). */
    suspend fun discardUnpostedDrafts(transactionId: Long)
}

class RoomJournalLinkWriter(
    private val ledger: LedgerRepository,
) : JournalLinkWriter {
    override suspend fun replaceDraft(transactionId: Long, draft: JournalDraft): Long =
        ledger.regenerateDraft(transactionId, draft.copy(sourceTransactionId = transactionId))

    override suspend fun post(journalId: Long): LedgerValidation = ledger.post(journalId)

    override suspend fun discardUnpostedDrafts(transactionId: Long) {
        ledger.discardUnpostedDrafts(transactionId)
    }
}

/**
 * Coordinates account linking and review-only journal creation.
 * Uses [AccountIdentifierRepository] as the source of truth for typed
 * identifiers and falls back to learned rules only when no typed identifier
 * matches.
 */
class TransactionLinkingService(
    private val transactions: TransactionRepository,
    private val journals: JournalLinkWriter,
    private val generator: JournalGenerationService,
    private val identifierRepository: AccountIdentifierRepository,
    private val rules: AccountLinkRuleRepository? = null,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val onError: (String, Throwable) -> Unit = { _, _ -> },
) {
    /** Prevents overlapping saves for the same transaction (repeated taps / races). */
    private val inFlight = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()

    constructor(
        transactions: TransactionRepository,
        ledger: LedgerRepository,
        generator: JournalGenerationService,
        identifierRepository: AccountIdentifierRepository,
        rules: AccountLinkRuleRepository? = null,
        now: () -> Long = { System.currentTimeMillis() },
    ) : this(
        transactions = transactions,
        journals = RoomJournalLinkWriter(ledger),
        generator = generator,
        identifierRepository = identifierRepository,
        rules = rules,
        now = now,
        onError = { msg, t -> android.util.Log.e("TransactionLink", msg, t) },
    )

    suspend fun applyUserLink(
        transaction: TransactionEntity,
        sourceAccountId: Long?,
        destinationAccountId: Long?,
        accounts: List<FinancialAccount>,
        rememberForFuture: Boolean = false,
        proposedAccountId: Long? = null,
        identifierToAdd: IdentifierCandidate? = null,
        financialTreatment: FinancialTreatment? = null,
        transactionType: com.baraa.masroof.transaction.TransactionType? = null,
    ): LinkApplyResult {
        if (transaction.id <= 0L) {
            return LinkApplyResult.ValidationError(
                code = "missing_transaction_id",
                messageAr = "معرّف العملية غير صالح — أعد فتح شاشة المراجعة",
            )
        }
        val fresh = transactions.getById(transaction.id)
            ?: return LinkApplyResult.ValidationError(
                code = "transaction_deleted",
                messageAr = "هذه العملية لم تعد موجودة — ربما حُذفت أو أُعيد استيرادها",
            )
        if (fresh.postingStatus == TransactionPostingStatus.POSTED) {
            return LinkApplyResult.ValidationError(
                code = "posted_immutable",
                messageAr = "هذه العملية مُرحّلة بالفعل. القيود المرحّلة غير قابلة للتعديل المباشر — استخدم «تصحيح» من شاشة العمليات إن لزم.",
            )
        }

        val treatment = financialTreatment ?: fresh.financialTreatment
        if (treatment == FinancialTreatment.PENDING_REVIEW || treatment == FinancialTreatment.IGNORED) {
            return LinkApplyResult.ValidationError(
                code = "treatment_required",
                messageAr = "اختر نوع العملية قبل الاعتماد",
            )
        }
        if (treatment.requiresTwoAccounts) {
            if (sourceAccountId == null || destinationAccountId == null) {
                return LinkApplyResult.ValidationError(
                    code = "two_sided_required",
                    messageAr = "هذا النوع يحتاج حساب المصدر وحساب الوجهة معًا",
                )
            }
            if (sourceAccountId == destinationAccountId) {
                return LinkApplyResult.ValidationError(
                    code = "same_accounts",
                    messageAr = "حساب الخصم وحساب الإضافة يجب أن يختلفا",
                )
            }
        }

        val source = sourceAccountId?.let { id -> accounts.firstOrNull { it.id == id } }
        val destination = destinationAccountId?.let { id -> accounts.firstOrNull { it.id == id } }
        if (sourceAccountId != null && source == null) {
            return LinkApplyResult.ValidationError(
                code = "source_account_missing",
                messageAr = "حساب الخصم لم يعد متاحًا — أعد اختيار الحساب",
            )
        }
        if (destinationAccountId != null && destination == null) {
            return LinkApplyResult.ValidationError(
                code = "destination_account_missing",
                messageAr = "حساب الإضافة لم يعد متاحًا — أعد اختيار الحساب",
            )
        }
        if (!treatment.requiresTwoAccounts) {
            val single = source ?: destination
            if (single == null) {
                return LinkApplyResult.ValidationError(
                    code = "account_required",
                    messageAr = "اختر حسابًا قبل الاعتماد",
                )
            }
        }

        // Compatibility: CREDIT_CARD_PAYMENT destination must be a credit card.
        if (treatment == FinancialTreatment.CREDIT_CARD_PAYMENT) {
            val dest = destination
                ?: return LinkApplyResult.ValidationError(
                    code = "card_destination_required",
                    messageAr = "سداد البطاقة يحتاج بطاقة ائتمانية كوجهة",
                )
            if (dest.accountType != com.baraa.masroof.transaction.AccountType.CREDIT_CARD) {
                return LinkApplyResult.ValidationError(
                    code = "incompatible_account_type",
                    messageAr = "حساب الوجهة يجب أن يكون بطاقة ائتمانية",
                )
            }
        }

        val linked = fresh.copy(
            transactionType = transactionType ?: fresh.transactionType,
            financialTreatment = treatment,
            sourceAccountId = sourceAccountId,
            destinationAccountId = destinationAccountId,
            accountLinkSource = AccountLinkSource.USER,
            accountLinkConfidence = 100,
            accountLinkNeedsReview = false,
            postingStatus = TransactionPostingStatus.NEEDS_REVIEW,
            exclusionReason = if (fresh.exclusionReason?.contains("محتمل تكرار") == true) {
                null
            } else {
                fresh.exclusionReason
            },
            updatedAt = now(),
        )

        // Pure generation before any writes — fail closed without mutating the row.
        val draft = try {
            generator.generate(linked, source, destination)
        } catch (t: Throwable) {
            onError("journal generate failed for tx=${transaction.id}", t)
            return LinkApplyResult.Failure(
                messageAr = "تعذّر إنشاء القيد: ${t.message ?: t.javaClass.simpleName}",
                cause = t,
            )
        }
        if (draft == null) {
            return LinkApplyResult.ValidationError(
                code = "journal_not_generated",
                messageAr = "تعذّر إنشاء القيد بهذه الحسابات — أكمل الحسابين أو غيّر التصنيف ثم أعد المحاولة",
            )
        }

        if (!inFlight.add(linked.id)) {
            return LinkApplyResult.ValidationError(
                code = "save_in_progress",
                messageAr = "جارٍ حفظ هذه العملية بالفعل — انتظر اكتمال الحفظ",
            )
        }

        return try {
            var identifierOutcome: IdentifierAddOutcome? = null
            if (identifierToAdd != null) {
                val accountId = source?.id ?: destination?.id ?: proposedAccountId
                    ?: return LinkApplyResult.ValidationError(
                        code = "identifier_account_missing",
                        messageAr = "تعذّر حفظ المعرف لأن الحساب غير محدد",
                    )
                identifierOutcome = identifierRepository.addOrUpdate(
                    accountId = accountId,
                    form = com.baraa.masroof.data.repository.IdentifierForm(
                        identifierType = identifierToAdd.identifierType,
                        displayLabel = displayLabelFor(identifierToAdd),
                        rawValue = identifierToAdd.normalizedLastFour,
                    ),
                )
                if (identifierOutcome.result == IdentifierAddResult.Rejected) {
                    return LinkApplyResult.ValidationError(
                        code = "identifier_rejected",
                        messageAr = identifierOutcome.message
                            ?: "تعذّر حفظ المعرف — تحقق من التوافق مع نوع الحساب",
                    )
                }
            }

            if (rememberForFuture) {
                source?.let { runCatching { rules?.remember(linked, it, "source") } }
                destination?.let { runCatching { rules?.remember(linked, it, "destination") } }
            }

            // Persist link only after draft + identifier succeed.
            transactions.update(linked)

            val resolvedId = journals.replaceDraft(
                linked.id,
                draft.copy(sourceTransactionId = linked.id),
            )
            val validation = journals.post(resolvedId)
            if (!validation.valid) {
                runCatching { journals.discardUnpostedDrafts(linked.id) }
                transactions.update(fresh.copy(updatedAt = now()))
                return LinkApplyResult.ValidationError(
                    code = "journal_not_posted",
                    messageAr = "تعذّر ترحيل القيد — راجع توازن الحسابات ثم أعد المحاولة",
                )
            }

            val posted = linked.copy(
                linkedJournalEntryId = resolvedId,
                postingStatus = TransactionPostingStatus.POSTED,
                needsReview = false,
                userConfirmed = true,
                updatedAt = now(),
            )
            transactions.update(posted)
            LinkApplyResult.Success(posted, identifierOutcome)
        } catch (iae: IllegalArgumentException) {
            runCatching { journals.discardUnpostedDrafts(linked.id) }
            runCatching { transactions.update(fresh.copy(updatedAt = now())) }
            onError("applyUserLink validation/require for tx=${transaction.id}", iae)
            val code = iae.message.orEmpty()
            when (code) {
                "posted_journal_requires_correction", "active_journal_exists" ->
                    LinkApplyResult.ValidationError(
                        code = code,
                        messageAr = "هذه العملية مرتبطة بقيد مرحّل أو مسودة متعارضة — لا يمكن إعادة الربط تلقائيًا",
                    )
                else -> LinkApplyResult.Failure(
                    messageAr = "تعذّر حفظ التصنيف والربط: $code",
                    cause = iae,
                )
            }
        } catch (t: Throwable) {
            runCatching { journals.discardUnpostedDrafts(linked.id) }
            runCatching { transactions.update(fresh.copy(updatedAt = now())) }
            onError("applyUserLink failed for tx=${transaction.id}", t)
            LinkApplyResult.Failure(
                messageAr = "تعذّر حفظ التصنيف والربط: ${t.message ?: t.javaClass.simpleName}",
                cause = t,
            )
        } finally {
            inFlight.remove(linked.id)
        }
    }

    /** User chooses to ignore an unresolved transaction without posting a journal. */
    suspend fun ignoreTransaction(transaction: TransactionEntity): LinkApplyResult {
        if (transaction.postingStatus == TransactionPostingStatus.POSTED) {
            return LinkApplyResult.ValidationError(
                code = "posted_immutable",
                messageAr = "لا يمكن تجاهل عملية مُرحّلة مباشرة — استخدم التصحيح أولاً",
            )
        }
        return try {
            val ignored = transaction.copy(
                needsReview = false,
                accountLinkNeedsReview = false,
                userConfirmed = true,
                postingStatus = TransactionPostingStatus.VOIDED,
                exclusionReason = transaction.exclusionReason ?: "تجاهلها المستخدم",
                updatedAt = now(),
            )
            transactions.update(ignored)
            LinkApplyResult.Success(ignored)
        } catch (t: Throwable) {
            onError("ignoreTransaction failed for tx=${transaction.id}", t)
            LinkApplyResult.Failure("تعذّر تجاهل العملية", t)
        }
    }

    /** Re-run automatic matching without creating identifiers. */
    suspend fun reanalyze(
        transaction: TransactionEntity,
        accounts: List<FinancialAccount>,
        identifierEvidence: List<com.baraa.masroof.transaction.ParsedIdentifierEvidence> = emptyList(),
    ): TransactionEntity {
        if (transaction.postingStatus == TransactionPostingStatus.POSTED) return transaction
        return linkAndGenerate(transaction, accounts, identifierEvidence = identifierEvidence)
    }

    private fun displayLabelFor(candidate: IdentifierCandidate): String = when (candidate.identifierType) {
        AccountIdentifierType.ACCOUNT_LAST4 -> "حساب"
        AccountIdentifierType.CREDIT_CARD_LAST4 -> "بطاقة ائتمانية"
        AccountIdentifierType.DEBIT_CARD_LAST4 -> "بطاقة مدى"
        AccountIdentifierType.IBAN_LAST4 -> "آيبان"
        AccountIdentifierType.WALLET_LAST4 -> "محفظة"
    }

    suspend fun linkAndGenerate(
        transaction: TransactionEntity,
        accounts: List<FinancialAccount>,
        trackingStartDate: Long? = null,
        identifierEvidence: List<com.baraa.masroof.transaction.ParsedIdentifierEvidence> = emptyList(),
    ): TransactionEntity {
        if (transaction.postingStatus == TransactionPostingStatus.POSTED || transaction.linkedJournalEntryId != null) {
            return transaction
        }
        val beforeStart = trackingStartDate
            ?.let { start ->
                java.time.Instant.ofEpochMilli(start).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            }
            ?.let { tracking ->
                transaction.transactionDate != null && transaction.transactionDate.isBefore(tracking)
            }
            ?: false
        if (beforeStart) {
            val kept = transaction.copy(
                needsReview = true,
                userConfirmed = false,
                exclusionReason = transaction.exclusionReason ?: "عملية قبل تاريخ بداية المتابعة",
                postingStatus = TransactionPostingStatus.NEEDS_REVIEW,
                updatedAt = now(),
            )
            transactions.update(kept)
            return kept
        }
        val direct = AccountMatcher.match(transaction, accounts, identifierRepository, identifierEvidence)
        val remembered = if (direct.level == AccountLinkConfidence.UNMATCHED) {
            rules?.find(transaction, accounts)
        } else {
            null
        }
        val match = if (remembered == null) {
            direct
        } else {
            AccountMatcher.Match(
                account = remembered,
                source = AccountLinkSource.OWNED_ACCOUNT_RULE,
                confidence = 80,
                needsReview = true,
                level = AccountLinkConfidence.MEDIUM,
                diagnosticCode = "learned_rule",
            )
        }
        val linked = when (transaction.financialTreatment) {
            FinancialTreatment.INCOME, FinancialTreatment.REFUND -> transaction.copy(
                destinationAccountId = match.account?.id,
                sourceAccountId = null,
                accountLinkSource = match.source,
                accountLinkConfidence = match.confidence,
                accountLinkNeedsReview = match.needsReview,
                postingStatus = TransactionPostingStatus.NEEDS_REVIEW,
                updatedAt = now(),
            )
            FinancialTreatment.EXPENSE, FinancialTreatment.BANK_FEE -> transaction.copy(
                sourceAccountId = match.account?.id,
                destinationAccountId = null,
                accountLinkSource = match.source,
                accountLinkConfidence = match.confidence,
                accountLinkNeedsReview = match.needsReview,
                postingStatus = TransactionPostingStatus.NEEDS_REVIEW,
                updatedAt = now(),
            )
            else -> transaction.copy(
                accountLinkSource = match.source,
                accountLinkConfidence = match.confidence,
                accountLinkNeedsReview = match.needsReview,
                postingStatus = TransactionPostingStatus.NEEDS_REVIEW,
                updatedAt = now(),
            )
        }
        transactions.update(linked)
        // Do not auto-create journals for unresolved review items — wait for applyUserLink.
        return linked
    }

    fun resolveTransferTreatment(
        transaction: TransactionEntity,
        accounts: List<FinancialAccount>,
    ): FinancialTreatment {
        val source = accounts.firstOrNull { it.id == transaction.sourceAccountId }
        val destination = accounts.firstOrNull { it.id == transaction.destinationAccountId }
        return when {
            source != null && destination != null && source.isOwnedByUser && destination.isOwnedByUser ->
                FinancialTreatment.INTERNAL_TRANSFER
            else -> transaction.financialTreatment
        }
    }
}

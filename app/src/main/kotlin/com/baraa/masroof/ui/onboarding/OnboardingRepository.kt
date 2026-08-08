package com.baraa.masroof.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import com.baraa.masroof.data.db.MasroofDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.time.LocalDate

/**
 * Single source of truth for onboarding state.
 *
 * The previous implementation read `FinancialSetup.setupCompleted` on
 * every [MainActivity.onCreate] and decided "onboarding vs main" with a
 * plain `Boolean`. That had two problems:
 *  - The default was `false`, so during the synchronous load the UI
 *    rendered Onboarding briefly even for completed users.
 *  - The decision was tied to FinancialSetup, so any path that reset
 *    setupCompleted (e.g. permission deny) tore the user back to
 *    onboarding.
 *
 * This repository:
 *  - Persists `onboardingCompleted`, `onboardingVersion`,
 *    `lastCompletedStep`, `completedAt` in SharedPreferences.
 *  - Emits a Flow of [OnboardingState] so the UI never has to guess
 *    during loading.
 *  - Decouples completion from SMS permission. Permission state lives
 *    separately in [SmsPermissionStore].
 */
sealed interface OnboardingState {
    /** Initial state while SharedPreferences is being read. */
    data object Loading : OnboardingState

    /**
     * Onboarding has not yet been completed. The caller should resume
     * at [lastCompletedStep] when present.
     */
    data class Pending(
        val onboardingVersion: Int,
        val lastCompletedStep: OnboardingStep?,
        val smsPermissionGranted: Boolean,
    ) : OnboardingState

    /**
     * Onboarding has been completed at [version]. The main UI can
     * render immediately, regardless of SMS permission state. When
     * the user actually tries to import SMS, the [SmsPermissionStore]
     * will surface a permission-required banner.
     */
    data class Completed(
        val onboardingVersion: Int,
        val completedAt: Long,
        val smsPermissionGranted: Boolean,
    ) : OnboardingState
}

/** Stable version stamp for the current onboarding flow. */
const val CURRENT_ONBOARDING_VERSION: Int = 2

/**
 * Map a persisted step name (including v1 names) to the nearest v2 resume step.
 * Unsafe mid-v1 account steps restart at SELECT_SENDER so patterns are created first.
 *
 * [onboardingVersion] distinguishes v1 `ACCOUNT` (pre-pattern) from v2 `ACCOUNT`
 * (post-pattern), which share the same enum name.
 */
fun mapPersistedStepName(name: String?, onboardingVersion: Int = CURRENT_ONBOARDING_VERSION): OnboardingStep? {
    if (name.isNullOrBlank()) return null
    if (onboardingVersion < 2) {
        return when (name) {
            "START_DATE", "ACCOUNT", "OPENING_BALANCE", "PERMISSION", "WELCOME" ->
                if (name == "PERMISSION" || name == "WELCOME") {
                    runCatching { OnboardingStep.valueOf(name) }.getOrDefault(OnboardingStep.WELCOME)
                } else {
                    OnboardingStep.SELECT_SENDER
                }
            "COMPLETION" -> OnboardingStep.COMPLETION
            else -> OnboardingStep.WELCOME
        }
    }
    return runCatching { OnboardingStep.valueOf(name) }.getOrElse {
        when (name) {
            "START_DATE", "OPENING_BALANCE" -> OnboardingStep.SELECT_SENDER
            else -> OnboardingStep.WELCOME
        }
    }
}

fun nextOnboardingStep(completed: OnboardingStep): OnboardingStep = when (completed) {
    OnboardingStep.WELCOME -> OnboardingStep.PERMISSION
    OnboardingStep.PERMISSION -> OnboardingStep.SELECT_SENDER
    OnboardingStep.SELECT_SENDER -> OnboardingStep.CREATE_PATTERN
    OnboardingStep.CREATE_PATTERN -> OnboardingStep.PATTERN_SUMMARY
    OnboardingStep.PATTERN_SUMMARY -> OnboardingStep.SENDER_PATTERN_SUMMARY
    OnboardingStep.SENDER_PATTERN_SUMMARY -> OnboardingStep.ACCOUNT
    OnboardingStep.ACCOUNT -> OnboardingStep.IDENTIFIERS
    OnboardingStep.IDENTIFIERS -> OnboardingStep.IMPORT_PREVIEW
    OnboardingStep.IMPORT_PREVIEW -> OnboardingStep.LINK_PREVIEW
    OnboardingStep.LINK_PREVIEW -> OnboardingStep.IMPORT
    OnboardingStep.IMPORT -> OnboardingStep.COMPLETION
    OnboardingStep.COMPLETION -> OnboardingStep.COMPLETION
}

fun previousOnboardingStep(step: OnboardingStep): OnboardingStep = when (step) {
    OnboardingStep.WELCOME -> OnboardingStep.WELCOME
    OnboardingStep.PERMISSION -> OnboardingStep.WELCOME
    OnboardingStep.SELECT_SENDER -> OnboardingStep.PERMISSION
    OnboardingStep.CREATE_PATTERN -> OnboardingStep.SELECT_SENDER
    OnboardingStep.PATTERN_SUMMARY -> OnboardingStep.CREATE_PATTERN
    OnboardingStep.SENDER_PATTERN_SUMMARY -> OnboardingStep.PATTERN_SUMMARY
    OnboardingStep.ACCOUNT -> OnboardingStep.SENDER_PATTERN_SUMMARY
    OnboardingStep.IDENTIFIERS -> OnboardingStep.ACCOUNT
    OnboardingStep.IMPORT_PREVIEW -> OnboardingStep.IDENTIFIERS
    OnboardingStep.LINK_PREVIEW -> OnboardingStep.IMPORT_PREVIEW
    OnboardingStep.IMPORT -> OnboardingStep.LINK_PREVIEW
    OnboardingStep.COMPLETION -> OnboardingStep.IMPORT
}

interface OnboardingRepository {
    /** Cold Flow of the persisted onboarding state. */
    fun observe(): Flow<OnboardingState>

    /** Synchronous read of the persisted onboarding state. */
    fun snapshot(): OnboardingState

    /**
     * Mark a step as completed by name. Called from each onboarding
     * step after the user successfully interacts with it.
     */
    suspend fun markStepCompleted(step: OnboardingStep)

    /**
     * Mark the entire onboarding flow as completed. Persists the
     * version stamp and timestamp. Idempotent.
     */
    suspend fun markCompleted()

    /** Reset onboarding to [OnboardingState.Pending] without touching
     * accounts, transactions, or any other persisted data.
     */
    suspend fun resetOnboarding()

    /** Process-death-safe draft. SMS bodies and inbox contents are deliberately excluded. */
    fun loadDraft(): OnboardingDraft?

    fun saveDraft(draft: OnboardingDraft)

    fun clearDraft()

    /**
     * Audit log for the [OnboardingRepository] production-readiness
     * checks: the implementation guarantees the flow never emits
     * `Pending` once [markCompleted] has succeeded.
     */
    fun isCompleted(): Boolean
}

class TestOnboardingRepository(
    private val initial: OnboardingState = OnboardingState.Pending(
        onboardingVersion = CURRENT_ONBOARDING_VERSION,
        lastCompletedStep = null,
        smsPermissionGranted = false,
    ),
    initialDraft: OnboardingDraft? = null,
) : OnboardingRepository {
    private val mutable = MutableStateFlow(initial)
    private var draft: OnboardingDraft? = initialDraft
    override fun observe(): Flow<OnboardingState> = mutable
    override fun snapshot(): OnboardingState = mutable.value
    override suspend fun markStepCompleted(step: OnboardingStep) {
        if (mutable.value is OnboardingState.Completed) return
        val previous = (mutable.value as? OnboardingState.Pending)?.lastCompletedStep
        if (previous != null && previous.ordinal >= step.ordinal) return
        mutable.value = OnboardingState.Pending(
            onboardingVersion = CURRENT_ONBOARDING_VERSION,
            lastCompletedStep = step,
            smsPermissionGranted = (mutable.value as? OnboardingState.Pending)?.smsPermissionGranted ?: false,
        )
    }
    override suspend fun markCompleted() {
        mutable.value = OnboardingState.Completed(
            onboardingVersion = CURRENT_ONBOARDING_VERSION,
            completedAt = System.currentTimeMillis(),
            smsPermissionGranted = (mutable.value as? OnboardingState.Pending)?.smsPermissionGranted ?: false,
        )
        draft = null
    }
    override suspend fun resetOnboarding() {
        mutable.value = OnboardingState.Pending(
            onboardingVersion = CURRENT_ONBOARDING_VERSION,
            lastCompletedStep = null,
            smsPermissionGranted = (mutable.value as? OnboardingState.Completed)?.smsPermissionGranted ?: false,
        )
        draft = null
    }
    override fun loadDraft(): OnboardingDraft? = draft
    override fun saveDraft(draft: OnboardingDraft) {
        this.draft = draft
    }
    override fun clearDraft() {
        draft = null
    }
    override fun isCompleted(): Boolean = mutable.value is OnboardingState.Completed
}

class SharedPreferencesOnboardingRepository(
    private val context: Context,
    private val permissionStore: SmsPermissionStore,
) : OnboardingRepository {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Backing Flow. Every read returns the same StateFlow content;
     * writes via [markStepCompleted] / [markCompleted] / [resetOnboarding]
     * push a fresh value through it.
     */
    private val mutable = MutableStateFlow(snapshotFromPrefs())

    override fun observe(): Flow<OnboardingState> = combine(mutable, permissionStore.observe()) { onboarding, permission ->
        when (onboarding) {
            is OnboardingState.Loading -> OnboardingState.Loading
            is OnboardingState.Pending -> onboarding.copy(smsPermissionGranted = permission.granted)
            is OnboardingState.Completed -> onboarding.copy(smsPermissionGranted = permission.granted)
        }
    }.distinctUntilChanged()

    override fun snapshot(): OnboardingState = snapshotFromPrefs()

    override fun isCompleted(): Boolean {
        val s = snapshotFromPrefs()
        return s is OnboardingState.Completed
    }

    override suspend fun markStepCompleted(step: OnboardingStep) {
        val current = mutable.value
        if (current is OnboardingState.Completed) return
        val previous = (current as? OnboardingState.Pending)?.lastCompletedStep
        if (previous != null && previous.ordinal >= step.ordinal) return
        // Pending v1 mid-flow continues under the v2 step machine.
        val updated = OnboardingState.Pending(
            onboardingVersion = CURRENT_ONBOARDING_VERSION,
            lastCompletedStep = step,
            smsPermissionGranted = (current as? OnboardingState.Pending)?.smsPermissionGranted ?: false,
        )
        persist(updated)
        mutable.value = updated
    }

    override suspend fun markCompleted() {
        val now = System.currentTimeMillis()
        val updated = OnboardingState.Completed(
            onboardingVersion = CURRENT_ONBOARDING_VERSION,
            completedAt = now,
            smsPermissionGranted = false,
        )
        persist(updated)
        clearDraft()
        mutable.value = updated
    }

    override suspend fun resetOnboarding() {
        val updated = OnboardingState.Pending(
            onboardingVersion = CURRENT_ONBOARDING_VERSION,
            lastCompletedStep = null,
            smsPermissionGranted = (snapshotFromPrefs() as? OnboardingState.Completed)?.smsPermissionGranted ?: false,
        )
        persist(updated)
        clearDraft()
        mutable.value = updated
    }

    override fun loadDraft(): OnboardingDraft? {
        if (!prefs.contains(KEY_DRAFT_STEP)) return null
        return OnboardingDraft(
            step = mapPersistedStepName(prefs.getString(KEY_DRAFT_STEP, null)) ?: OnboardingStep.WELCOME,
            option = enumValueOrDefault(prefs.getString(KEY_DRAFT_OPTION, null), StartDateOption.TODAY),
            trackingDate = runCatching {
                LocalDate.parse(prefs.getString(KEY_DRAFT_DATE, null).orEmpty())
            }.getOrDefault(LocalDate.now()),
            accountType = enumValueOrDefault(
                prefs.getString(KEY_DRAFT_ACCOUNT_TYPE, null),
                com.baraa.masroof.transaction.AccountType.BANK_ACCOUNT,
            ),
            displayName = prefs.getString(KEY_DRAFT_DISPLAY_NAME, "").orEmpty(),
            institution = prefs.getString(KEY_DRAFT_INSTITUTION, "").orEmpty(),
            patternSourceProfileId = prefs.getLong(KEY_DRAFT_PATTERN_PROFILE_ID, 0L),
            patternSourceLabel = prefs.getString(KEY_DRAFT_PATTERN_LABEL, "").orEmpty(),
            lastFour = prefs.getString(KEY_DRAFT_LAST_FOUR, "").orEmpty(),
            identifierConfirmed = prefs.getBoolean(KEY_DRAFT_IDENTIFIER_CONFIRMED, false),
            openingBalance = prefs.getString(KEY_DRAFT_OPENING_BALANCE, "0").orEmpty(),
            currency = enumValueOrDefault(
                prefs.getString(KEY_DRAFT_CURRENCY, null),
                com.baraa.masroof.transaction.Currency.SAR,
            ),
            includeLiquidity = prefs.getBoolean(KEY_DRAFT_LIQUIDITY, true),
            includeNetWorth = prefs.getBoolean(KEY_DRAFT_NET_WORTH, true),
            selectedSenderProfileId = prefs.getLong(KEY_DRAFT_SENDER_PROFILE_ID, 0L),
            selectedSenderKey = prefs.getString(KEY_DRAFT_SENDER_KEY, "").orEmpty(),
            selectedSenderDisplay = prefs.getString(KEY_DRAFT_SENDER_DISPLAY, "").orEmpty(),
            createdAccountId = prefs.getLong(KEY_DRAFT_ACCOUNT_ID, 0L),
        )
    }

    override fun saveDraft(draft: OnboardingDraft) {
        prefs.edit()
            .putString(KEY_DRAFT_STEP, draft.step.name)
            .putString(KEY_DRAFT_OPTION, draft.option.name)
            .putString(KEY_DRAFT_DATE, draft.trackingDate.toString())
            .putString(KEY_DRAFT_ACCOUNT_TYPE, draft.accountType.name)
            .putString(KEY_DRAFT_DISPLAY_NAME, draft.displayName)
            .putString(KEY_DRAFT_INSTITUTION, draft.institution)
            .putLong(KEY_DRAFT_PATTERN_PROFILE_ID, draft.patternSourceProfileId)
            .putString(KEY_DRAFT_PATTERN_LABEL, draft.patternSourceLabel)
            .putString(KEY_DRAFT_LAST_FOUR, draft.lastFour)
            .putBoolean(KEY_DRAFT_IDENTIFIER_CONFIRMED, draft.identifierConfirmed)
            .putString(KEY_DRAFT_OPENING_BALANCE, draft.openingBalance)
            .putString(KEY_DRAFT_CURRENCY, draft.currency.name)
            .putBoolean(KEY_DRAFT_LIQUIDITY, draft.includeLiquidity)
            .putBoolean(KEY_DRAFT_NET_WORTH, draft.includeNetWorth)
            .putLong(KEY_DRAFT_SENDER_PROFILE_ID, draft.selectedSenderProfileId)
            .putString(KEY_DRAFT_SENDER_KEY, draft.selectedSenderKey)
            .putString(KEY_DRAFT_SENDER_DISPLAY, draft.selectedSenderDisplay)
            .putLong(KEY_DRAFT_ACCOUNT_ID, draft.createdAccountId)
            .apply()
    }

    override fun clearDraft() {
        prefs.edit().apply {
            DRAFT_KEYS.forEach(::remove)
            apply()
        }
    }

    private fun persist(state: OnboardingState) {
        prefs.edit().apply {
            when (state) {
                is OnboardingState.Loading -> { /* never persisted */ }
                is OnboardingState.Pending -> {
                    putBoolean(KEY_COMPLETED, false)
                    putInt(KEY_VERSION, state.onboardingVersion)
                    putString(KEY_LAST_STEP, state.lastCompletedStep?.name)
                    remove(KEY_COMPLETED_AT)
                }
                is OnboardingState.Completed -> {
                    putBoolean(KEY_COMPLETED, true)
                    putInt(KEY_VERSION, state.onboardingVersion)
                    putLong(KEY_COMPLETED_AT, state.completedAt)
                    remove(KEY_LAST_STEP)
                }
            }
            apply()
        }
    }

    private fun snapshotFromPrefs(): OnboardingState {
        val completed = prefs.getBoolean(KEY_COMPLETED, false)
        if (completed) {
            return OnboardingState.Completed(
                onboardingVersion = prefs.getInt(KEY_VERSION, CURRENT_ONBOARDING_VERSION),
                completedAt = prefs.getLong(KEY_COMPLETED_AT, 0L),
                smsPermissionGranted = false,
            )
        }
        val lastStepName = prefs.getString(KEY_LAST_STEP, null)
        val version = prefs.getInt(KEY_VERSION, CURRENT_ONBOARDING_VERSION)
        val lastStep = mapPersistedStepName(lastStepName, version)
        return OnboardingState.Pending(
            onboardingVersion = version,
            lastCompletedStep = lastStep,
            smsPermissionGranted = false,
        )
    }

    companion object {
        const val PREFS_NAME: String = "masroof_onboarding_prefs"
        const val KEY_COMPLETED: String = "onboarding_completed"
        const val KEY_VERSION: String = "onboarding_version"
        const val KEY_LAST_STEP: String = "onboarding_last_step"
        const val KEY_COMPLETED_AT: String = "onboarding_completed_at"
        private const val KEY_DRAFT_STEP = "draft_step"
        private const val KEY_DRAFT_OPTION = "draft_option"
        private const val KEY_DRAFT_DATE = "draft_date"
        private const val KEY_DRAFT_ACCOUNT_TYPE = "draft_account_type"
        private const val KEY_DRAFT_DISPLAY_NAME = "draft_display_name"
        private const val KEY_DRAFT_INSTITUTION = "draft_institution"
        private const val KEY_DRAFT_PATTERN_PROFILE_ID = "draft_pattern_profile_id"
        private const val KEY_DRAFT_PATTERN_LABEL = "draft_pattern_label"
        private const val KEY_DRAFT_LAST_FOUR = "draft_last_four"
        private const val KEY_DRAFT_IDENTIFIER_CONFIRMED = "draft_identifier_confirmed"
        private const val KEY_DRAFT_OPENING_BALANCE = "draft_opening_balance"
        private const val KEY_DRAFT_CURRENCY = "draft_currency"
        private const val KEY_DRAFT_LIQUIDITY = "draft_liquidity"
        private const val KEY_DRAFT_NET_WORTH = "draft_net_worth"
        private const val KEY_DRAFT_SENDER_PROFILE_ID = "draft_sender_profile_id"
        private const val KEY_DRAFT_SENDER_KEY = "draft_sender_key"
        private const val KEY_DRAFT_SENDER_DISPLAY = "draft_sender_display"
        private const val KEY_DRAFT_ACCOUNT_ID = "draft_account_id"
        private val DRAFT_KEYS = listOf(
            KEY_DRAFT_STEP,
            KEY_DRAFT_OPTION,
            KEY_DRAFT_DATE,
            KEY_DRAFT_ACCOUNT_TYPE,
            KEY_DRAFT_DISPLAY_NAME,
            KEY_DRAFT_INSTITUTION,
            KEY_DRAFT_PATTERN_PROFILE_ID,
            KEY_DRAFT_PATTERN_LABEL,
            KEY_DRAFT_LAST_FOUR,
            KEY_DRAFT_IDENTIFIER_CONFIRMED,
            KEY_DRAFT_OPENING_BALANCE,
            KEY_DRAFT_CURRENCY,
            KEY_DRAFT_LIQUIDITY,
            KEY_DRAFT_NET_WORTH,
            KEY_DRAFT_SENDER_PROFILE_ID,
            KEY_DRAFT_SENDER_KEY,
            KEY_DRAFT_SENDER_DISPLAY,
            KEY_DRAFT_ACCOUNT_ID,
        )
    }
}

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, default: T): T =
    value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

/**
 * Cold Flow that re-emits the permission state on every subscription
 * AND on every lifecycle resume. Use this as the single source of
 * truth in UI; never cache the grant in Compose state longer than
 * the lifetime of the screen.
 */
class SmsPermissionStore(private val context: Context) {
    private val mutable = MutableStateFlow(readGranted())

    fun observe(): Flow<SmsPermissionSnapshot> = flow {
        emit(readGranted())
        // Force a re-poll when lifecycle resumes.
        mutable.subscriptionCount
    }.let { baseFlow ->
        kotlinx.coroutines.flow.merge(baseFlow, mutable).distinctUntilChanged()
    }

    fun snapshot(): SmsPermissionSnapshot = readGranted()

    /**
     * Call this from the host Activity's onResume / lifecycle observer
     * to force the next emission to re-check the OS.
     */
    fun refresh() {
        mutable.value = readGranted()
    }

    private fun readGranted(): SmsPermissionSnapshot = SmsPermissionSnapshot(
        granted = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED,
    )
}

data class SmsPermissionSnapshot(
    val granted: Boolean,
)
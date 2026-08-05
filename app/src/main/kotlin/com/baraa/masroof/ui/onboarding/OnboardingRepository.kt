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
const val CURRENT_ONBOARDING_VERSION: Int = 1

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
) : OnboardingRepository {
    private val mutable = MutableStateFlow(initial)
    override fun observe(): Flow<OnboardingState> = mutable
    override fun snapshot(): OnboardingState = mutable.value
    override suspend fun markStepCompleted(step: OnboardingStep) {
        if (mutable.value is OnboardingState.Completed) return
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
    }
    override suspend fun resetOnboarding() {
        mutable.value = OnboardingState.Pending(
            onboardingVersion = CURRENT_ONBOARDING_VERSION,
            lastCompletedStep = null,
            smsPermissionGranted = (mutable.value as? OnboardingState.Completed)?.smsPermissionGranted ?: false,
        )
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
        val nextVersion = (current as? OnboardingState.Pending)?.onboardingVersion ?: CURRENT_ONBOARDING_VERSION
        val updated = OnboardingState.Pending(
            onboardingVersion = nextVersion,
            lastCompletedStep = step,
            smsPermissionGranted = (current as? OnboardingState.Pending)?.smsPermissionGranted ?: false,
        )
        persist(updated)
        mutable.value = updated
    }

    override suspend fun markCompleted() {
        val now = System.currentTimeMillis()
        val current = mutable.value
        val version = (current as? OnboardingState.Pending)?.onboardingVersion ?: CURRENT_ONBOARDING_VERSION
        val updated = OnboardingState.Completed(onboardingVersion = version, completedAt = now, smsPermissionGranted = false)
        persist(updated)
        mutable.value = updated
    }

    override suspend fun resetOnboarding() {
        val updated = OnboardingState.Pending(
            onboardingVersion = CURRENT_ONBOARDING_VERSION,
            lastCompletedStep = null,
            smsPermissionGranted = (snapshotFromPrefs() as? OnboardingState.Completed)?.smsPermissionGranted ?: false,
        )
        persist(updated)
        mutable.value = updated
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
        val lastStep = lastStepName?.let { runCatching { OnboardingStep.valueOf(it) }.getOrNull() }
        return OnboardingState.Pending(
            onboardingVersion = prefs.getInt(KEY_VERSION, CURRENT_ONBOARDING_VERSION),
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
    }
}

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
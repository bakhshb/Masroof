package com.baraa.masroof.ui.senders

import com.baraa.masroof.sms.PatternDraft

/**
 * Process-local handoff for the manual pattern-creation flow.
 *
 * Navigation arguments are primitives, but a freshly built [PatternDraft] is
 * a structured object that must reach [TemplateEditorScreen]'s draft entry
 * WITHOUT being persisted to Room first. This holder carries at most one
 * pending draft; the editor consumes it immediately on entry and clears it
 * so a stale draft is never silently reused after process recreation.
 */
object PatternDraftHolder {
    @Volatile
    private var pending: PatternDraft? = null

    fun set(draft: PatternDraft) {
        pending = draft
    }

    /** Returns the pending draft and clears it (one-shot). */
    fun consume(): PatternDraft? {
        val draft = pending
        pending = null
        return draft
    }
}

/**
 * Cross-screen action-result message. The template editor sets a result here
 * before navigating back; [SenderDetailsScreen] consumes it on resume and
 * surfaces it through a Snackbar (never a hidden bottom-of-page string).
 */
object PatternActionResultHolder {
    data class Result(val message: String, val reviewPatternId: Long?)

    @Volatile
    private var pending: Result? = null

    fun set(message: String, reviewPatternId: Long? = null) {
        pending = Result(message, reviewPatternId)
    }

    fun consume(): Result? {
        val result = pending
        pending = null
        return result
    }
}
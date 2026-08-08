package com.baraa.masroof.ui.onboarding

/**
 * Pattern-first onboarding steps (v2).
 * Order: teach sender patterns → create account from patterned senders → identifiers → import matched only.
 */
enum class OnboardingStep {
    WELCOME,
    PERMISSION,
    SELECT_SENDER,
    CREATE_PATTERN,
    PATTERN_SUMMARY,
    SENDER_PATTERN_SUMMARY,
    ACCOUNT,
    IDENTIFIERS,
    IMPORT_PREVIEW,
    LINK_PREVIEW,
    IMPORT,
    COMPLETION,
}

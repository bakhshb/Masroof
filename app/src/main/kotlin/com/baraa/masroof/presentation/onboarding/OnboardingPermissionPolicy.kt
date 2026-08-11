package com.baraa.masroof.presentation.onboarding

import android.Manifest

object OnboardingPermissionPolicy {
    val REQUIRED_SMS_PERMISSIONS: Array<String> = arrayOf(
        Manifest.permission.READ_SMS,
        Manifest.permission.RECEIVE_SMS,
    )
}

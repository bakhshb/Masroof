package com.baraa.masroof.ui.onboarding

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.baraa.masroof.ui.theme.PrimaryButton
import com.baraa.masroof.ui.theme.SecondaryButton

/**
 * Stable tri-state for READ_SMS permission flow.
 *
 * - [Granted]    — permission is in effect; onboarding may proceed.
 * - [NotAsked]   — permission has never been requested; primary path is
 *                  the Android permission dialog.
 * - [Denied]     — user has denied once; primary path is "إعادة طلب"
 *                  and the dialog still appears.
 * - [PermanentlyDenied] — user selected "Don't ask again" or twice denied
 *                  on newer Android versions; only "إعدادات التطبيق"
 *                  can recover.
 */
enum class SmsPermissionState { Granted, NotAsked, Denied, PermanentlyDenied }

/**
 * First-launch SMS permission screen. Shown before any other UI when
 * READ_SMS is missing. The screen is the only authority on whether
 * onboarding can proceed; we **never** mark `setupCompleted = true` while
 * READ_SMS is not Granted.
 *
 * Permission state is re-checked on every onResume so a grant from the
 * system Settings activity is detected without relaunching the app.
 */
@Composable
fun SmsPermissionScreen(
    onPermissionGranted: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context as? Activity

    var state by remember {
        mutableStateOf(snapshotPermissionState(context, activity))
    }

    // Re-check the permission whenever the lifecycle owner returns to RESUMED.
    // Critical because the user can grant the permission from Settings and
    // return to the app without re-launching it.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                state = snapshotPermissionState(context, activity)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            state = SmsPermissionState.Granted
        } else {
            state = SmsPermissionState.Denied
        }
    }

    SideEffect {
        // Fire off the onboarding transition as soon as we transition to Granted.
        if (state == SmsPermissionState.Granted) onPermissionGranted()
    }

    SmsPermissionContent(
        state = state,
        onRequestPermission = {
            state = SmsPermissionState.NotAsked
            launcher.launch(Manifest.permission.READ_SMS)
        },
        onOpenAppSettings = {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}"),
            )
            context.startActivity(intent)
        },
    )
}

private fun snapshotPermissionState(context: android.content.Context, activity: Activity?): SmsPermissionState {
    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
    if (granted) return SmsPermissionState.Granted
    // On Android 11+ checkSelfPermission + shouldShowRequestPermissionRationale combo tells us
    // whether the system will still surface the dialog.
    val canAsk = activity?.shouldShowRequestPermissionRationale(Manifest.permission.READ_SMS) != false
    // If we've never asked and shouldShowRequestPermissionRationale returns false, that's NotAsked.
    // If it returns false AFTER a previous ask, that's PermanentlyDenied.
    return when {
        activity == null -> SmsPermissionState.NotAsked
        canAsk -> SmsPermissionState.NotAsked
        else -> SmsPermissionState.PermanentlyDenied
    }
}

@Composable
private fun SmsPermissionContent(
    state: SmsPermissionState,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Spacer(Modifier.height(40.dp))
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Box(Modifier.padding(20.dp)) {
                    Text("رسائل البنك", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Text("السماح بقراءة الرسائل البنكية", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            Text(
                "يحتاج التطبيق إلى إذن قراءة الرسائل للتعرف على العمليات البنكية واستيرادها. التطبيق يقرأ الرسائل فقط، ولن يرسل أو يعدل أو يحذف أي رسالة.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PermissionStatusBadge(state)

            Spacer(Modifier.weight(1f))
            when (state) {
                SmsPermissionState.Granted -> {
                    PrimaryButton("متابعة", onRequestPermission)
                }
                SmsPermissionState.NotAsked, SmsPermissionState.Denied -> {
                    PrimaryButton("السماح بقراءة الرسائل", onRequestPermission)
                    if (state == SmsPermissionState.Denied) {
                        SecondaryButton("إعادة طلب الصلاحية", onRequestPermission)
                    }
                }
                SmsPermissionState.PermanentlyDenied -> {
                    SecondaryButton("فتح إعدادات التطبيق", onOpenAppSettings)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PermissionStatusBadge(state: SmsPermissionState) {
    val (text, container, onContainer) = when (state) {
        SmsPermissionState.Granted -> Triple(
            "تم منح إذن قراءة الرسائل",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
        )
        SmsPermissionState.PermanentlyDenied -> Triple(
            "تم رفض الإذن. يمكنك منحه من إعدادات التطبيق",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        )
        else -> Triple(
            "لم يتم منح إذن قراءة الرسائل",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = container,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(Modifier.padding(16.dp)) {
            Text(text, style = MaterialTheme.typography.titleSmall, color = onContainer)
        }
    }
}

/**
 * Defer helper: lets the rest of the app decide whether to skip or
 * hold until the SMS permission screen is satisfied. The returned lambda
 * is the next-step action that runs only when the permission state is
 * `Granted`.
 */
fun onGrantAction(state: SmsPermissionState, action: () -> Unit): () -> Unit = {
    if (state == SmsPermissionState.Granted) action()
}

package com.baraa.masroof.ui.sms

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * Inline banner shown **only** when READ_SMS is not currently granted.
 *
 * The actual Android permission state ([ContextCompat.checkSelfPermission])
 * is the single source of truth. We DO NOT cache a permission-granted
 * Boolean in any SavedStateHandle / onboarding state — re-checking
 * [checkSelfPermission] on every ON_RESUME guarantees the banner
 * disappears the moment the user grants the permission from Settings.
 *
 * When permission is granted, a compact status row is shown instead.
 */
@Composable
fun SmsPermissionRequiredBanner(
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    fun checkGranted(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_SMS,
    ) == PackageManager.PERMISSION_GRANTED

    var granted by remember { mutableStateOf(checkGranted()) }
    var permanentlyDenied by remember {
        mutableStateOf(
            !checkGranted() &&
                (context as? android.app.Activity)?.shouldShowRequestPermissionRationale(Manifest.permission.READ_SMS) == false,
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = checkGranted()
                permanentlyDenied = !granted &&
                    (context as? android.app.Activity)?.shouldShowRequestPermissionRationale(Manifest.permission.READ_SMS) == false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        granted = isGranted
        permanentlyDenied = !isGranted &&
            (context as? android.app.Activity)?.shouldShowRequestPermissionRationale(Manifest.permission.READ_SMS) == false
    }

    if (granted) {
        // Compact status row only — no large banner.
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Text(
                "إذن قراءة الرسائل مفعّل ✓",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("مطلوب إذن قراءة الرسائل", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
            Text("يحتاج التطبيق إلى إذن قراءة الرسائل للتعرف على العمليات البنكية.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(label = "منح الصلاحية", onClick = { launcher.launch(Manifest.permission.READ_SMS) })
                if (permanentlyDenied) SecondaryButton(label = "فتح إعدادات التطبيق", onClick = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                })
            }
        }
    }
}

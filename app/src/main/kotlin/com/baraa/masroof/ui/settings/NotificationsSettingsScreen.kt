package com.baraa.masroof.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.baraa.masroof.MasroofApplication
import com.baraa.masroof.ui.theme.FinancialShapes
import com.baraa.masroof.ui.theme.FinancialTypography
import com.baraa.masroof.ui.theme.MasroofTopAppBar
import com.baraa.masroof.ui.theme.PrimaryButton
import com.baraa.masroof.ui.theme.SecondaryButton
import com.baraa.masroof.ui.theme.SectionHeader
import com.baraa.masroof.ui.theme.Spacing

/**
 * Settings screen: "إشعار عند تسجيل عملية جديدة".
 *
 * Lets the user toggle system notifications for new transactions.
 * Requests POST_NOTIFICATIONS only on Android 13+ when the user enables
 * the option.
 */
@Composable
fun NotificationsSettingsScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as MasroofApplication
    val prefs = app.developerPreferences
    val activity = context as? Activity

    var notificationsEnabled by remember { mutableStateOf(prefs.transactionNotificationsEnabled) }
    var reviewOnly by remember { mutableStateOf(prefs.needsReviewNotificationsOnly) }
    var balanceInNotif by remember { mutableStateOf(prefs.balanceInNotifications) }
    var granted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        granted = isGranted
        if (isGranted) {
            prefs.transactionNotificationsEnabled = true
            notificationsEnabled = true
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        MasroofTopAppBar(title = "إشعار عند تسجيل عملية جديدة", onBack = onClose)
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.x4), verticalArrangement = Arrangement.spacedBy(Spacing.x3)) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = FinancialShapes.medium,
                color = if (granted) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
            ) {
                Text(
                    if (granted) "إذن إرسال الإشعارات ممنوح" else "إذن إرسال الإشعارات غير ممنوح",
                    modifier = Modifier.padding(Spacing.x4),
                    style = FinancialTypography.merchant,
                )
            }
            SectionHeader("الإعدادات")
            SwitchRow(
                label = "إشعار عند تسجيل عملية جديدة",
                description = "يُعلمك التطبيق عند تسجيل عملية بنكية جديدة.",
                checked = notificationsEnabled,
                onCheckedChange = { desired ->
                    if (desired) {
                        if (!granted) {
                            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            prefs.transactionNotificationsEnabled = true
                            notificationsEnabled = true
                        }
                    } else {
                        prefs.transactionNotificationsEnabled = false
                        notificationsEnabled = false
                    }
                },
            )
            SwitchRow(
                label = "إشعارات المراجعة فقط",
                description = "إظهار الإشعارات للعمليات التي تحتاج مراجعة فقط.",
                checked = reviewOnly,
                onCheckedChange = {
                    prefs.needsReviewNotificationsOnly = it
                    reviewOnly = it
                },
                enabled = notificationsEnabled,
            )
            SwitchRow(
                label = "عرض الرصيد في الإشعار",
                description = "يعرض الرصيد المحسوب للجانب الآخر للعملية.",
                checked = balanceInNotif,
                onCheckedChange = {
                    prefs.balanceInNotifications = it
                    balanceInNotif = it
                },
                enabled = notificationsEnabled,
            )
            Text(
                "قد يحتوي الإشعار على آخر 4 أرقام فقط، ولا يعرض رقم البطاقة كاملًا ولا رمز التحقق.",
                style = FinancialTypography.metadata,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val permanentlyDenied = !granted && (activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == false)
            if (!granted && permanentlyDenied) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                    SecondaryButton(label = "فتح إعدادات التطبيق", onClick = {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                    })
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = FinancialShapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(modifier = Modifier.padding(Spacing.x4), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.x1)) {
                Text(label, style = FinancialTypography.merchant)
                Text(description, style = FinancialTypography.metadata, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
    }
}
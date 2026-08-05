package com.baraa.masroof.ui.settings

import android.Manifest
import android.app.Activity
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
 * Settings screen: "استيراد رسائل البنك تلقائياً".
 *
 * Lets the user toggle automatic processing of new incoming bank SMS
 * messages. Requires both READ_SMS and RECEIVE_SMS. Displays the
 * current permission state and a contextual description per spec
 * section J.
 */
@Composable
fun AutoSmsImportSettingsScreen(
    onClose: () -> Unit,
    onRequestReceiveSms: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as MasroofApplication
    val prefs = app.developerPreferences
    val activity = context as? Activity

    var readSmsGranted by remember { mutableStateOf(snapshotPermission(context, Manifest.permission.READ_SMS)) }
    var receiveSmsGranted by remember { mutableStateOf(snapshotPermission(context, Manifest.permission.RECEIVE_SMS)) }
    var autoEnabled by remember { mutableStateOf(prefs.automaticSmsImportEnabled) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                readSmsGranted = snapshotPermission(context, Manifest.permission.READ_SMS)
                receiveSmsGranted = snapshotPermission(context, Manifest.permission.RECEIVE_SMS)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val receiveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        receiveSmsGranted = granted
    }

    val permanentlyDenied = !receiveSmsGranted && (activity?.shouldShowRequestPermissionRationale(Manifest.permission.RECEIVE_SMS) == false)

    Column(modifier = Modifier.fillMaxSize()) {
        MasroofTopAppBar(title = "استيراد رسائل البنك تلقائياً", onBack = onClose)
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.x4), verticalArrangement = Arrangement.spacedBy(Spacing.x3)) {
            SectionHeader("الحالة الحالية")
            StatusRow(readSmsGranted, "إذن قراءة الرسائل (READ_SMS)", !readSmsGranted, "الصلاحية ممنوحة", "الصلاحية غير ممنوحة")
            StatusRow(receiveSmsGranted, "إذن استقبال الرسائل (RECEIVE_SMS)", permanentlyDenied, "الصلاحية ممنوحة", "صلاحية استقبال الرسائل غير ممنوحة")

            SectionHeader("الإعداد")
            Surface(modifier = Modifier.fillMaxWidth(), shape = FinancialShapes.medium, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(Spacing.x4), verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("استيراد رسائل البنك تلقائياً", modifier = Modifier.weight(1f), style = FinancialTypography.merchant)
                        Switch(
                            checked = autoEnabled && readSmsGranted && receiveSmsGranted,
                            onCheckedChange = { desired ->
                                if (desired) {
                                    if (!readSmsGranted || !receiveSmsGranted) {
                                        if (!receiveSmsGranted) receiveLauncher.launch(Manifest.permission.RECEIVE_SMS)
                                    } else {
                                        prefs.automaticSmsImportEnabled = true
                                        autoEnabled = true
                                    }
                                } else {
                                    prefs.automaticSmsImportEnabled = false
                                    autoEnabled = false
                                }
                            },
                            enabled = readSmsGranted && receiveSmsGranted,
                        )
                    }
                    Text(
                        "عند تفعيله، يسجّل التطبيق العمليات الجديدة فور وصول رسائل البنك.",
                        style = FinancialTypography.metadata,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (!readSmsGranted || !receiveSmsGranted) {
                SectionHeader("المنح المطلوب")
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                    if (!receiveSmsGranted) {
                        PrimaryButton(label = "طلب إذن استقبال الرسائل", onClick = { receiveLauncher.launch(Manifest.permission.RECEIVE_SMS) })
                    }
                    if (permanentlyDenied) {
                        SecondaryButton(label = "فتح إعدادات التطبيق", onClick = {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                        })
                    }
                }
            }

            Text(
                "تعطيل تحسين البطارية قد يؤثر على بعض الأجهزة.",
                style = FinancialTypography.metadata,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusRow(granted: Boolean, label: String, errorVariant: Boolean, grantedText: String, errorText: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = FinancialShapes.medium,
        color = if (granted) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
    ) {
        Text(
            if (granted) "$grantedText • $label" else "$errorText • $label",
            modifier = Modifier.padding(Spacing.x4),
            style = FinancialTypography.metadata,
        )
    }
}

private fun snapshotPermission(context: android.content.Context, perm: String): Boolean =
    ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
package com.baraa.masroof.diagnostics

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.baraa.masroof.MasroofApplication
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes a sanitized diagnostic report to the app cache and surfaces
 * an Android Sharesheet chooser. The FileProvider URI is shared with
 * FLAG_GRANT_READ_URI_PERMISSION only — the receiving app cannot
 * write back to the cache file.
 */
object DiagnosticShareHelper {

    fun share(
        context: Context,
        snapshot: DiagnosticSnapshot,
        app: MasroofApplication,
    ) {
        val baseDir = File(context.cacheDir, "diagnostic_reports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val textFile = File(baseDir, "diagnostic_$stamp.txt")
        val jsonFile = File(baseDir, "diagnostic_$stamp.json")
        textFile.writeText(DiagnosticReport.renderText(snapshot), Charsets.UTF_8)
        jsonFile.writeText(DiagnosticReport.renderJson(snapshot), Charsets.UTF_8)

        val authority = "${app.packageName}.fileprovider"
        val textUri = FileProvider.getUriForFile(context, authority, textFile)
        val jsonUri = FileProvider.getUriForFile(context, authority, jsonFile)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, textUri)
            putExtra(Intent.EXTRA_SUBJECT, "تقرير تشخيص مصروف")
            putExtra(Intent.EXTRA_TEXT, "مرفق تقرير التشخيص. الملف النصي متاح أيضًا: ${jsonUri}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "تصدير تقرير التشخيص")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
package com.baraa.masroof.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.core.content.getSystemService

/**
 * Clipboard helper for the "نسخ نموذج منقح" action on a parse-failure
 * message. The user must preview the sanitized text in a dialog first
 * (see [com.baraa.masroof.ui.diagnostics.ParseFailureDialog]) and only
 * then copy it. The clipboard write is a single primary-clip
 * replacement with a non-sensitive label.
 */
object ClipboardHelper {

    fun copy(context: Context, text: String, label: String = "Masroof sanitized text") {
        val cm: ClipboardManager = context.getSystemService() ?: return
        val clip = ClipData.newPlainText(label, text)
        cm.setPrimaryClip(clip)
    }
}
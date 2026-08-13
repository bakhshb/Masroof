package com.baraa.masroof.presentation.dashboard

import android.content.Context
import android.content.Intent

object SharePlainText {
    fun share(
        context: Context,
        text: String,
        chooserTitle: String,
        subject: String? = null,
    ) {
        if (text.isBlank()) return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            if (!subject.isNullOrBlank()) {
                putExtra(Intent.EXTRA_SUBJECT, subject)
            }
        }
        context.startActivity(Intent.createChooser(send, chooserTitle))
    }
}

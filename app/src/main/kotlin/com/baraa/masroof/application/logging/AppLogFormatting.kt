package com.baraa.masroof.application.logging

import com.baraa.masroof.application.sms.SmsScanResult
import com.baraa.masroof.domain.model.MessageFamily

object AppLogFormatting {
    fun maskSender(sender: String): String {
        val trimmed = sender.trim()
        if (trimmed.length <= 4) return "****"
        return "…${trimmed.takeLast(4)}"
    }

    fun maskId(id: String): String {
        val trimmed = id.trim()
        if (trimmed.length <= 4) return "****"
        return "…${trimmed.takeLast(4)}"
    }

    fun messageFamilyLabel(family: MessageFamily): String = family.name.lowercase()

    fun scanSummary(result: SmsScanResult): String =
        buildString {
            append("scanned=${result.scanned}")
            append(" inserted=${result.inserted}")
            append(" duplicates=${result.duplicates}")
            append(" parsed=${result.parsed}")
            append(" review=${result.reviewRequired}")
            append(" non_financial=${result.nonFinancial}")
            append(" unsupported=${result.unsupported}")
            append(" not_relevant=${result.notRelevant}")
            append(" skipped=${result.skippedMalformed}")
            append(" failed=${result.failed}")
            result.failure?.let { append(" failure=$it") }
        }
}

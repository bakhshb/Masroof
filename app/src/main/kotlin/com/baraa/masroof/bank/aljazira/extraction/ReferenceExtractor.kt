package com.baraa.masroof.bank.aljazira.extraction

import com.baraa.masroof.parsing.model.NormalizedSms

class ReferenceExtractor {
    fun extract(sms: NormalizedSms): String? {
        val normalized = sms.normalizedBody
        val comparison = sms.comparisonBody
        val match = PATTERN.find(comparison) ?: return null
        val range = match.groups[1]?.range ?: return null
        return normalized.substring(range.first, range.last + 1).trim().takeIf { it.isNotBlank() }
    }

    companion object {
        private val PATTERN = Regex("""رقم\s*المعاملة\s*:\s*([^\n]+)""")
    }
}

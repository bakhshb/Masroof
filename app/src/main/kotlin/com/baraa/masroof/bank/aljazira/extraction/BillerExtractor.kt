package com.baraa.masroof.bank.aljazira.extraction

import com.baraa.masroof.parsing.model.NormalizedSms

data class BillerExtraction(
    val biller: String? = null,
    val billerCode: String? = null,
)

class BillerExtractor {
    fun extract(sms: NormalizedSms): BillerExtraction {
        val normalized = sms.normalizedBody
        val comparison = sms.comparisonBody
        val billerMatch = BILLER.find(comparison)
        val biller = billerMatch?.groups?.get(1)?.range?.let { range ->
            normalized.substring(range.first, range.last + 1).trim()
        }
        val codeMatch = BILLER_CODE.find(comparison)
        val code = codeMatch?.groupValues?.getOrNull(1)
        return BillerExtraction(biller = biller?.takeIf { it.isNotBlank() }, billerCode = code)
    }

    companion object {
        private val BILLER = Regex("""المفوتر\s*:\s*([^\n]+)""")
        private val BILLER_CODE = Regex("""رمز\s*المفوتر\s*:\s*([^\n]+)""")
    }
}

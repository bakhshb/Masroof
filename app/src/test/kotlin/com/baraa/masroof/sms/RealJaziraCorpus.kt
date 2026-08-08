package com.baraa.masroof.sms

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Shared loader for the permanent Jazira regression corpus
 * (`app/src/test/resources/sms_corpus/jazira_real_corpus.json`).
 *
 * The corpus preserves the exact message SHAPE (Arabic/English wording, labels,
 * punctuation, whitespace, line structure, field order, currency placement,
 * amount formatting, date/time, backslashes, optional-line presence). Only
 * personal names, merchant names, account/card last4, references and bank
 * counterparties are replaced with stable TEST values.
 */
@Serializable
data class CorpusExpected(
    val financial: Boolean,
    val transactionType: String,
    val direction: String,
    val amount: String,
    val currency: String,
    val sourceAccountLast4: String? = null,
    val destinationAccountLast4: String? = null,
    val creditCardLast4: String? = null,
    val debitCardLast4: String? = null,
    val ibanLast4: String? = null,
    val sourceIbanLast4: String? = null,
    val destinationIbanLast4: String? = null,
)

@Serializable
data class CorpusCase(
    val id: String,
    val description: String,
    val body: String,
    val expected: CorpusExpected,
    val familyGroup: String? = null,
)

object RealJaziraCorpus {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): List<CorpusCase> {
        val resource = "/sms_corpus/jazira_real_corpus.json"
        val text = object {}.javaClass.getResourceAsStream(resource)
            ?.bufferedReader()?.use { it.readText() }
            ?: error("corpus resource not found: $resource")
        return json.decodeFromString(text)
    }
}
package com.baraa.masroof.sms

import com.baraa.masroof.data.db.PatternCanonicalField
import com.baraa.masroof.transaction.MoneyFlowDirection
import com.baraa.masroof.transaction.MonetaryFieldClassifier
import com.baraa.masroof.transaction.MonetaryRole
import com.baraa.masroof.transaction.TransactionType
import com.baraa.masroof.transaction.TransactionTypeTaxonomy

const val SEMANTIC_SCHEMA_VERSION: Int = 2

/**
 * One shared, deterministic label -> canonical role classifier.
 *
 * It classifies labels only. Values never participate in semantic identity.
 */
object CanonicalPatternFieldClassifier {
    fun classify(label: String?): Set<PatternCanonicalField> {
        if (label.isNullOrBlank()) return emptySet()
        val n = CanonicalMessageNormalizer.normalizeLabel(label)
            .replace(Regex("""[\p{P}\p{S}]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        val moneyRole = MonetaryFieldClassifier.classify(label).takeUnless {
            it == MonetaryRole.UNKNOWN
        } ?: MonetaryFieldClassifier.classify(n)
        MonetaryFieldClassifier.toPatternField(moneyRole)?.let {
            return setOf(it)
        }

        return when {
            isCreditCard(n) -> setOf(PatternCanonicalField.CREDIT_CARD_LAST4)
            isDebitCard(n) -> setOf(PatternCanonicalField.DEBIT_CARD_LAST4)
            isSourceIban(n) -> setOf(PatternCanonicalField.SOURCE_IBAN_LAST4)
            isDestinationIban(n) -> setOf(PatternCanonicalField.DESTINATION_IBAN_LAST4)
            isIban(n) -> setOf(PatternCanonicalField.IBAN_LAST4)
            isWallet(n) -> setOf(PatternCanonicalField.WALLET_LAST4)
            isSourceAccount(n) -> setOf(PatternCanonicalField.SOURCE_ACCOUNT_LAST4)
            isDestinationAccount(n) -> setOf(PatternCanonicalField.DESTINATION_ACCOUNT_LAST4)
            isAccount(n) -> setOf(PatternCanonicalField.ACCOUNT_LAST4)
            isMerchant(n) -> setOf(PatternCanonicalField.MERCHANT)
            isBeneficiary(n) -> setOf(PatternCanonicalField.BENEFICIARY)
            isDateTime(n) -> setOf(
                PatternCanonicalField.TRANSACTION_DATE,
                PatternCanonicalField.TRANSACTION_TIME,
            )
            isDate(n) -> setOf(PatternCanonicalField.TRANSACTION_DATE)
            isTime(n) -> setOf(PatternCanonicalField.TRANSACTION_TIME)
            isReference(n) -> setOf(PatternCanonicalField.TRANSACTION_REFERENCE)
            isCurrency(n) -> setOf(PatternCanonicalField.CURRENCY)
            else -> emptySet()
        }
    }

    fun monetaryRole(label: String?): MonetaryRole = MonetaryFieldClassifier.classify(label)

    private fun isCreditCard(n: String) =
        "ائتمان" in n || "credit card" in n

    private fun isDebitCard(n: String) =
        "مدي" in n || "debit card" in n || "بطاقه خصم" in n

    private fun isSourceIban(n: String) =
        isIban(n) && ("من" in n || "source" in n || "خصم" in n)

    private fun isDestinationIban(n: String) =
        isIban(n) && ("الي" in n || "destination" in n || "مستفيد" in n)

    private fun isIban(n: String) = "ايبان" in n || "iban" in n

    private fun isWallet(n: String) =
        "محفظه" in n || ("wallet" in n && "card" !in n)

    private fun isSourceAccount(n: String) =
        "من حساب" in n || "خصمت من" in n || "خصم من" in n ||
            "source account" in n || "debited from account" in n

    private fun isDestinationAccount(n: String) =
        "الي حساب" in n || "حساب المستفيد" in n || "اودعت الي" in n ||
            "destination account" in n || "credited to account" in n

    private fun isAccount(n: String) =
        n == "الحساب" || n == "رقم الحساب" || n == "account" ||
            n == "بطاقه" || n == "card"

    private fun isMerchant(n: String) =
        n == "لدي" || n == "التاجر" || n == "merchant" || n == "at" ||
            n == "ل" || n == "لـ" || "اسم التاجر" in n

    private fun isBeneficiary(n: String) =
        "مستفيد" in n || "beneficiary" in n || "اسم المرسل" in n ||
            n == "الي" || n == "to"

    private fun isDateTime(n: String) = n == "في" || n == "on" || n == "التاريخ والوقت"

    private fun isDate(n: String) = "تاريخ" in n || "date" in n

    private fun isTime(n: String) = "وقت" in n || "time" in n

    private fun isReference(n: String) =
        "مرجع" in n || "reference" in n || n == "ref" ||
            "رقم العمليه" in n || "رقم المعامله" in n

    private fun isCurrency(n: String) = "عمله" in n || "currency" in n
}

enum class SemanticPaymentInstrument {
    DEBIT_CARD,
    CREDIT_CARD,
    ACCOUNT,
    IBAN,
    WALLET,
    UNKNOWN,
}

data class SemanticPatternSchema(
    val transactionType: TransactionType,
    val direction: MoneyFlowDirection,
    val paymentInstrument: SemanticPaymentInstrument,
    val requiredFields: Set<PatternCanonicalField>,
    val structuralFields: Set<PatternCanonicalField>,
) {
    fun stableKey(): String = buildString {
        append("semantic-v")
        append(SEMANTIC_SCHEMA_VERSION)
        append('|')
        append(transactionType.name)
        append('|')
        append(direction.name)
        append('|')
        append(paymentInstrument.name)
        append("|required=")
        append(requiredFields.map { it.name }.sorted().joinToString(","))
        append("|structural=")
        append(structuralFields.map { it.name }.sorted().joinToString(","))
    }
}

sealed interface SemanticSchemaResult {
    data class Safe(val schema: SemanticPatternSchema) : SemanticSchemaResult {
        val key: String get() = schema.stableKey()
    }

    data class Ambiguous(val reason: String) : SemanticSchemaResult
    data class NonFinancial(val reason: String = "NON_FINANCIAL") : SemanticSchemaResult
}

/**
 * Semantic projection shared by raw SMS and generated templates.
 *
 * Both entry points first use [CanonicalMessageNormalizer], then execute the
 * same projection. This is not a second structural normalizer.
 */
object SemanticPatternSchemaNormalizer {
    fun fromBody(body: String?): SemanticSchemaResult {
        if (body.isNullOrBlank()) return SemanticSchemaResult.Ambiguous("EMPTY_BODY")
        if (BankSmsFilter.isOtpOrAuthenticationMessage(body)) {
            return SemanticSchemaResult.NonFinancial("OTP_OR_AUTH")
        }
        val cue = MessageTypeCueCatalog.detect(body)
        return derive(
            structure = CanonicalMessageNormalizer.normalizeBody(body),
            transactionType = cue.transactionType,
        )
    }

    fun fromTemplate(
        templateText: String?,
        transactionTypeName: String?,
    ): SemanticSchemaResult {
        if (templateText.isNullOrBlank()) return SemanticSchemaResult.Ambiguous("EMPTY_TEMPLATE")
        val explicitType = TransactionTypeTaxonomy.parse(transactionTypeName)
        val cueType = MessageTypeCueCatalog.detect(templateText).transactionType
        if (explicitType != null && cueType != null && explicitType != cueType) {
            return SemanticSchemaResult.Ambiguous("CONFLICTING_TRANSACTION_TYPE")
        }
        return derive(
            structure = CanonicalMessageNormalizer.normalizeTemplate(templateText),
            transactionType = explicitType ?: cueType,
        )
    }

    private fun derive(
        structure: CanonicalMessageNormalizer.CanonicalMessageStructure,
        transactionType: TransactionType?,
    ): SemanticSchemaResult {
        val type = transactionType
            ?: return SemanticSchemaResult.Ambiguous("UNKNOWN_TRANSACTION_TYPE")
        if (type == TransactionType.NON_FINANCIAL) {
            return SemanticSchemaResult.NonFinancial()
        }
        if (type == TransactionType.OTHER_FINANCIAL) {
            return SemanticSchemaResult.Ambiguous("UNSPECIFIC_TRANSACTION_TYPE")
        }

        val observed = structure.lines
            .flatMap { CanonicalPatternFieldClassifier.classify(it.originalLabel) }
            .toSet()
        val instrument = if (type in ROUTING_FIELD_TYPES) {
            SemanticPaymentInstrument.UNKNOWN
        } else {
            paymentInstrument(observed)
                ?: return SemanticSchemaResult.Ambiguous("CONFLICTING_PAYMENT_INSTRUMENT")
        }
        if (type in PURCHASE_TYPES && instrument == SemanticPaymentInstrument.UNKNOWN) {
            return SemanticSchemaResult.Ambiguous("UNKNOWN_PAYMENT_INSTRUMENT")
        }

        val required = setOf(PatternCanonicalField.TRANSACTION_AMOUNT)
        val structural = if (type in ROUTING_FIELD_TYPES) {
            emptySet()
        } else {
            observed - NON_IDENTITY_FIELDS
        }
        return SemanticSchemaResult.Safe(
            SemanticPatternSchema(
                transactionType = type,
                direction = TransactionTypeTaxonomy.directionOf(type),
                paymentInstrument = instrument,
                requiredFields = required,
                structuralFields = structural,
            ),
        )
    }

    private fun paymentInstrument(fields: Set<PatternCanonicalField>): SemanticPaymentInstrument? {
        val detected = buildSet {
            if (PatternCanonicalField.DEBIT_CARD_LAST4 in fields) add(SemanticPaymentInstrument.DEBIT_CARD)
            if (PatternCanonicalField.CREDIT_CARD_LAST4 in fields) add(SemanticPaymentInstrument.CREDIT_CARD)
            if (PatternCanonicalField.WALLET_LAST4 in fields) add(SemanticPaymentInstrument.WALLET)
            if (fields.any { it in IBAN_FIELDS }) add(SemanticPaymentInstrument.IBAN)
            if (fields.any { it in ACCOUNT_FIELDS }) add(SemanticPaymentInstrument.ACCOUNT)
        }
        return when (detected.size) {
            0 -> SemanticPaymentInstrument.UNKNOWN
            1 -> detected.single()
            else -> null
        }
    }

    private val PURCHASE_TYPES = setOf(TransactionType.PURCHASE, TransactionType.ONLINE_PURCHASE)
    private val ROUTING_FIELD_TYPES = setOf(
        TransactionType.SALARY,
        TransactionType.TRANSFER_IN,
        TransactionType.TRANSFER_OUT,
    )
    private val ACCOUNT_FIELDS = setOf(
        PatternCanonicalField.ACCOUNT_LAST4,
        PatternCanonicalField.SOURCE_ACCOUNT_LAST4,
        PatternCanonicalField.DESTINATION_ACCOUNT_LAST4,
    )
    private val IBAN_FIELDS = setOf(
        PatternCanonicalField.IBAN_LAST4,
        PatternCanonicalField.SOURCE_IBAN_LAST4,
        PatternCanonicalField.DESTINATION_IBAN_LAST4,
    )
    private val NON_IDENTITY_FIELDS = setOf(
        PatternCanonicalField.TRANSACTION_AMOUNT,
        PatternCanonicalField.CURRENCY,
        PatternCanonicalField.AVAILABLE_BALANCE,
        PatternCanonicalField.CARD_AMOUNT_DUE,
        PatternCanonicalField.TRANSACTION_REFERENCE,
        PatternCanonicalField.CHANNEL,
    )
}

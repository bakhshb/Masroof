package com.baraa.masroof.sms

import com.baraa.masroof.transaction.LineBasedFieldParser
import com.baraa.masroof.transaction.MonetaryFieldClassifier
import com.baraa.masroof.transaction.MonetaryRole

/**
 * Display-only metadata extracted from SMS. Not used as a canonical
 * identity — that role is owned by [CanonicalMessageNormalizer] and
 * [StructuralSignatureGenerator].
 *
 * Retained for user-facing friendly names and backwards-compatibility
 * callers that want a transaction-type hint from a raw body.
 */
object SemanticPatternCanonicalizer {

    /** Core slots that define the family (order is fixed for determinism). */
    enum class CoreSlot {
        CREDIT_CARD_LAST4,
        DEBIT_CARD_LAST4,
        ACCOUNT_LAST4,
        IBAN_LAST4,
        WALLET_LAST4,
        MERCHANT,
        BENEFICIARY,
        AMOUNT,
        DATETIME,
        SOURCE_ACCOUNT,
        DESTINATION_ACCOUNT,
        REFERENCE,
    }

    /** Context slots that may appear or not without creating a new family. */
    enum class OptionalSlot {
        AVAILABLE_BALANCE,
        TOTAL_DUE,
        CREDIT_LIMIT,
        CHANNEL,
    }

    data class Fingerprint(
        /** Stable identity used as [canonicalKey]. */
        val familyKey: String,
        val typeToken: String,
        val coreSlots: Set<CoreSlot>,
        val optionalSlots: Set<OptionalSlot>,
        /** Observed wallet/provider tokens, e.g. GOOGLE_PAY — metadata only. */
        val observedChannels: Set<String>,
        /** Arabic display name without wallet suffix. */
        val displayNameAr: String,
    )

    private val PLACEHOLDER = Regex("""\{([A-Z0-9_]+)\}""")

    fun fromBody(body: String?): Fingerprint {
        if (body.isNullOrBlank()) {
            return Fingerprint("TYPE:UNKNOWN", "TYPE:UNKNOWN", emptySet(), emptySet(), emptySet(), "نمط رسالة")
        }
        val cue = MessageTypeCueCatalog.detect(body)
        val cores = linkedSetOf<CoreSlot>()
        val optionals = linkedSetOf<OptionalSlot>()
        val channels = linkedSetOf<String>()
        cue.channelToken?.removePrefix("CHANNEL:")?.let { channels += it }

        for (line in LineBasedFieldParser.splitLines(body)) {
            val (label, wallet) = MessageTypeCueCatalog.stripWalletSuffix(line.label)
            wallet?.let { channels += it }
            classifyLine(label, line.value, cores, optionals)
        }
        // Title-only type lines may carry a wallet; already handled via cue.
        return build(cue.typeToken, cue.displayNameAr, cores, optionals, channels)
    }

    fun fromTemplate(
        templateText: String?,
        transactionTypeName: String? = null,
        signatureFallback: String = "",
    ): Fingerprint {
        if (templateText.isNullOrBlank()) {
            // Legacy: derive a coarse family from signature tokens, stripping channel.
            return fromLegacySignature(signatureFallback, transactionTypeName)
        }
        val typeToken = typeTokenFromName(transactionTypeName)
            ?: typeTokenFromTemplateHeader(templateText)
            ?: "TYPE:UNKNOWN"
        val cueName = displayNameForType(typeToken)
        val cores = linkedSetOf<CoreSlot>()
        val optionals = linkedSetOf<OptionalSlot>()
        val channels = linkedSetOf<String>()

        for (raw in templateText.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val (labelPart, _, valuePart) = splitPreserve(line) ?: continue
            val (label, wallet) = MessageTypeCueCatalog.stripWalletSuffix(labelPart.trim())
            wallet?.let { channels += it }
            // Prefer placeholders when present.
            val placeholders = PLACEHOLDER.findAll(valuePart).map { it.groupValues[1] }.toList()
            if (placeholders.isNotEmpty()) {
                for (p in placeholders) mapPlaceholder(p, cores, optionals, channels)
            } else {
                classifyLine(label, valuePart, cores, optionals)
            }
            // Label-only type header with wallet already stripped.
            if (valuePart.isBlank()) {
                MessageTypeCueCatalog.detectFromFragment(label)?.channelToken
                    ?.removePrefix("CHANNEL:")
                    ?.let { channels += it }
            }
        }
        return build(typeToken, cueName, cores, optionals, channels)
    }

    fun familyKey(body: String?): String = fromBody(body).familyKey

    fun familyKeyFromTemplate(
        templateText: String?,
        transactionTypeName: String? = null,
        signatureFallback: String = "",
    ): String = fromTemplate(templateText, transactionTypeName, signatureFallback).familyKey

    /** Display title: type only — wallets belong in variant metadata. */
    fun displayName(body: String?): String = fromBody(body).displayNameAr

    private fun build(
        typeToken: String,
        displayNameAr: String,
        cores: Set<CoreSlot>,
        optionals: Set<OptionalSlot>,
        channels: Set<String>,
    ): Fingerprint {
        val orderedCores = CoreSlot.entries.filter { it in cores }
        val key = buildString {
            append(typeToken.ifBlank { "TYPE:UNKNOWN" })
            for (slot in orderedCores) {
                append('|')
                append(slot.name)
            }
        }
        return Fingerprint(
            familyKey = key,
            typeToken = typeToken.ifBlank { "TYPE:UNKNOWN" },
            coreSlots = orderedCores.toSet(),
            optionalSlots = optionals,
            observedChannels = channels,
            displayNameAr = displayNameAr.ifBlank { "نمط رسالة" },
        )
    }

    private fun classifyLine(
        label: String,
        value: String,
        cores: MutableSet<CoreSlot>,
        optionals: MutableSet<OptionalSlot>,
    ) {
        when (CanonicalPatternFieldClassifier.monetaryRole(label)) {
            MonetaryRole.AVAILABLE_BALANCE -> optionals += OptionalSlot.AVAILABLE_BALANCE
            MonetaryRole.TOTAL_DUE, MonetaryRole.OUTSTANDING_BALANCE -> optionals += OptionalSlot.TOTAL_DUE
            MonetaryRole.CREDIT_LIMIT -> optionals += OptionalSlot.CREDIT_LIMIT
            else -> Unit
        }
        for (field in CanonicalPatternFieldClassifier.classify(label)) {
            when (field) {
                com.baraa.masroof.data.db.PatternCanonicalField.TRANSACTION_AMOUNT -> cores += CoreSlot.AMOUNT
                com.baraa.masroof.data.db.PatternCanonicalField.CREDIT_CARD_LAST4 -> cores += CoreSlot.CREDIT_CARD_LAST4
                com.baraa.masroof.data.db.PatternCanonicalField.DEBIT_CARD_LAST4 -> cores += CoreSlot.DEBIT_CARD_LAST4
                com.baraa.masroof.data.db.PatternCanonicalField.ACCOUNT_LAST4 -> cores += CoreSlot.ACCOUNT_LAST4
                com.baraa.masroof.data.db.PatternCanonicalField.SOURCE_ACCOUNT_LAST4 -> cores += CoreSlot.SOURCE_ACCOUNT
                com.baraa.masroof.data.db.PatternCanonicalField.DESTINATION_ACCOUNT_LAST4 -> cores += CoreSlot.DESTINATION_ACCOUNT
                com.baraa.masroof.data.db.PatternCanonicalField.IBAN_LAST4,
                com.baraa.masroof.data.db.PatternCanonicalField.SOURCE_IBAN_LAST4,
                com.baraa.masroof.data.db.PatternCanonicalField.DESTINATION_IBAN_LAST4,
                -> cores += CoreSlot.IBAN_LAST4
                com.baraa.masroof.data.db.PatternCanonicalField.WALLET_LAST4 -> cores += CoreSlot.WALLET_LAST4
                com.baraa.masroof.data.db.PatternCanonicalField.MERCHANT -> cores += CoreSlot.MERCHANT
                com.baraa.masroof.data.db.PatternCanonicalField.BENEFICIARY -> cores += CoreSlot.BENEFICIARY
                com.baraa.masroof.data.db.PatternCanonicalField.TRANSACTION_DATE,
                com.baraa.masroof.data.db.PatternCanonicalField.TRANSACTION_TIME,
                -> cores += CoreSlot.DATETIME
                com.baraa.masroof.data.db.PatternCanonicalField.TRANSACTION_REFERENCE -> cores += CoreSlot.REFERENCE
                else -> Unit
            }
        }
    }

    private fun mapPlaceholder(
        name: String,
        cores: MutableSet<CoreSlot>,
        optionals: MutableSet<OptionalSlot>,
        channels: MutableSet<String>,
    ) {
        when (name.uppercase()) {
            "AMOUNT", "TRANSACTION_AMOUNT" -> cores += CoreSlot.AMOUNT
            "CREDIT_CARD_LAST4" -> cores += CoreSlot.CREDIT_CARD_LAST4
            "DEBIT_CARD_LAST4" -> cores += CoreSlot.DEBIT_CARD_LAST4
            "ACCOUNT_LAST4" -> cores += CoreSlot.ACCOUNT_LAST4
            "IBAN_LAST4", "SOURCE_IBAN_LAST4", "DESTINATION_IBAN_LAST4" -> cores += CoreSlot.IBAN_LAST4
            "WALLET_LAST4" -> cores += CoreSlot.WALLET_LAST4
            "MERCHANT" -> cores += CoreSlot.MERCHANT
            "BENEFICIARY" -> cores += CoreSlot.BENEFICIARY
            "DATE", "TIME", "DATETIME" -> cores += CoreSlot.DATETIME
            "AVAILABLE_BALANCE" -> optionals += OptionalSlot.AVAILABLE_BALANCE
            "TOTAL_DUE", "CARD_AMOUNT_DUE" -> optionals += OptionalSlot.TOTAL_DUE
            "CREDIT_LIMIT" -> optionals += OptionalSlot.CREDIT_LIMIT
            "TRANSACTION_ID", "REFERENCE" -> cores += CoreSlot.REFERENCE
            "CURRENCY" -> Unit // never identity
            else -> if (name.uppercase().startsWith("CHANNEL")) {
                channels += name.removePrefix("CHANNEL_").removePrefix("CHANNEL:")
                optionals += OptionalSlot.CHANNEL
            }
        }
    }

    private fun fromLegacySignature(signature: String, transactionTypeName: String?): Fingerprint {
        if (signature.isBlank()) {
            val t = typeTokenFromName(transactionTypeName) ?: "TYPE:UNKNOWN"
            return build(t, displayNameForType(t), emptySet(), emptySet(), emptySet())
        }
        val parts = signature.split('|').map { it.trim() }.filter { it.isNotEmpty() }
        var typeToken = typeTokenFromName(transactionTypeName)
            ?: parts.firstOrNull { it.startsWith("type=") }
                ?.substringAfter("type=")
                ?.removeSurrounding("<", ">")
            ?: "TYPE:UNKNOWN"
        val cores = linkedSetOf<CoreSlot>()
        val optionals = linkedSetOf<OptionalSlot>()
        val channels = linkedSetOf<String>()
        for (p in parts) {
            when {
                p.startsWith("channel=") -> {
                    val ch = p.substringAfter("channel=")
                        .removeSurrounding("<", ">")
                        .removePrefix("CHANNEL:")
                    if (ch.isNotBlank()) {
                        channels += ch
                        optionals += OptionalSlot.CHANNEL
                    }
                }
                p.startsWith("type=") -> Unit
                else -> {
                    val label = p.substringBefore('=').trim()
                    val value = p.substringAfter('=', "")
                    classifyLine(label, value, cores, optionals)
                }
            }
        }
        // Opaque legacy signatures with no recoverable slots: preserve uniqueness
        // after stripping channel so wallet variants still merge when possible.
        if (cores.isEmpty() && parts.none { it.startsWith("type=") || '=' in it }) {
            val stripped = parts.filterNot { it.startsWith("channel=") }.joinToString("|")
            return Fingerprint(
                familyKey = "S:$stripped",
                typeToken = typeToken,
                coreSlots = emptySet(),
                optionalSlots = optionals,
                observedChannels = channels,
                displayNameAr = displayNameForType(typeToken),
            )
        }
        return build(typeToken, displayNameForType(typeToken), cores, optionals, channels)
    }

    private fun typeTokenFromName(name: String?): String? {
        if (name.isNullOrBlank()) return null
        return when (name.uppercase()) {
            "PURCHASE" -> "TYPE:POS_PURCHASE"
            "ONLINE_PURCHASE" -> "TYPE:ONLINE_PURCHASE"
            "TRANSFER_OUT" -> "TYPE:TRANSFER_OUT"
            "TRANSFER_IN" -> "TYPE:TRANSFER_IN"
            "INTERNAL_TRANSFER" -> "TYPE:INTERNAL_TRANSFER"
            "CASH_WITHDRAWAL" -> "TYPE:CASH_WITHDRAWAL"
            "CARD_PAYMENT" -> "TYPE:CARD_PAYMENT"
            "BILL_PAYMENT" -> "TYPE:BILL_PAYMENT"
            "REFUND" -> "TYPE:REFUND"
            "SALARY" -> "TYPE:SALARY"
            "FEE" -> "TYPE:FEE"
            "NON_FINANCIAL" -> "TYPE:NON_FINANCIAL"
            "OTHER_FINANCIAL" -> "TYPE:OTHER_FINANCIAL"
            else -> if (name.startsWith("TYPE:")) name else "TYPE:$name"
        }
    }

    private fun typeTokenFromTemplateHeader(templateText: String): String? {
        val first = templateText.lineSequence().map { it.trim() }.firstOrNull { it.isNotBlank() } ?: return null
        val label = splitPreserve(first)?.first ?: first
        val (cleaned, _) = MessageTypeCueCatalog.stripWalletSuffix(label)
        return MessageTypeCueCatalog.detectFromFragment(cleaned)?.typeToken
            ?: MessageTypeCueCatalog.detect(cleaned).typeToken.takeIf { it != "TYPE:UNKNOWN" }
    }

    private fun displayNameForType(typeToken: String): String = when (typeToken) {
        "TYPE:POS_PURCHASE", "TYPE:PURCHASE" -> "شراء عبر نقاط البيع"
        "TYPE:ONLINE_PURCHASE" -> "شراء عبر الإنترنت"
        "TYPE:TRANSFER_OUT" -> "تحويل صادر"
        "TYPE:TRANSFER_IN" -> "تحويل وارد"
        "TYPE:INTERNAL_TRANSFER" -> "تحويل داخلي"
        "TYPE:CASH_WITHDRAWAL" -> "سحب نقدي"
        "TYPE:CARD_PAYMENT" -> "سداد بطاقة"
        "TYPE:BILL_PAYMENT" -> "سداد فاتورة"
        "TYPE:REFUND" -> "استرداد"
        "TYPE:SALARY" -> "راتب"
        "TYPE:FEE" -> "رسوم بنكية"
        "TYPE:NON_FINANCIAL", "TYPE:OTP" -> "رسالة غير مالية"
        "TYPE:OTHER_FINANCIAL" -> "عملية مالية أخرى"
        else -> "نمط رسالة"
    }

    private fun isCreditCardLabel(n: String) =
        "ائتمان" in n || "credit" in n

    private fun isDebitCardLabel(n: String) =
        "مدى" in n || "debit" in n

    private fun isIbanLabel(n: String) =
        "ايبان" in n || "iban" in n

    private fun isWalletLabel(n: String) =
        "محفظه" in n || "محفظة" in n || (n.contains("wallet") && "card" !in n)

    private fun isSourceAccountLabel(n: String) =
        "من حساب" in n || "خصمت من" in n || "source" in n || n == "من"

    private fun isDestinationAccountLabel(n: String) =
        "الى حساب" in n || "إلى حساب" in n || "destination" in n ||
            n == "الى" || n == "إلى" || "اودعت" in n || "أودعت" in n

    private fun isAccountLabel(n: String) =
        "حساب" in n || "account" in n || "بطاقه" in n || "بطاقة" in n || "card" in n

    private fun isMerchantLabel(n: String) =
        n == "لدي" || n == "لدى" || n == "at" || n == "ل" || "تاجر" in n || "merchant" in n

    private fun isBeneficiaryLabel(n: String) =
        "مستفيد" in n || "beneficiary" in n

    private fun isDatetimeLabel(n: String) =
        n == "في" || n == "on" || "وقت" in n || "تاريخ" in n || "time" in n || "date" in n

    private fun isReferenceLabel(n: String) =
        "مرجع" in n || "reference" in n || "رقم المعامله" in n || "رقم المعاملة" in n || "ref" == n

    private fun splitPreserve(line: String): Triple<String, String, String>? {
        for (m in listOf("：", ":", "=")) {
            val idx = line.indexOf(m)
            if (idx <= 0) continue
            val label = line.substring(0, idx)
            var sepEnd = idx + m.length
            while (sepEnd < line.length && line[sepEnd].isWhitespace()) sepEnd++
            return Triple(label, line.substring(idx, sepEnd), line.substring(sepEnd))
        }
        return null
    }
}

package com.baraa.masroof.transaction

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

open class GenericBankSmsParser : BankSmsParser {
    override val name: String = "Generic"
    override val version: String = "1.0.0"
    override val priority: Int = 0
    open val senderAliases: List<String> = emptyList()

    override fun canParse(sender: String?, body: String?): Boolean {
        if (senderAliases.isEmpty()) return true
        if (sender.isNullOrBlank()) return false
        val normalized = BankTextNormalizer.normalizeForParsing(sender)
        return senderAliases.any { it == normalized }
    }

    override fun parse(
        sender: String?,
        body: String?,
        smsTimestampMillis: Long?,
    ): ParsedTransaction {
        val notes = mutableListOf<String>()
        val matchedRules = mutableListOf<String>()
        val lines = LineBasedFieldParser.splitLines(body.orEmpty())
        if (lines.isEmpty()) {
            notes.add("empty body")
            return emptyResult(sender, body, notes, matchedRules)
        }
        if (senderAliases.isNotEmpty() && !sender.isNullOrBlank()) {
            matchedRules.add("sender_alias_match:${sender.trim()}")
        }
        val currency = detectCurrencyForMatcher(lines, matchedRules)
        val type = detectType(lines)
        val status = detectStatus(lines)
        val amount = extractAmountWithNotes(lines, notes)
        val identifiers = extractIdentifiers(lines)
        val cardOrAccount = identifiers.firstOrNull()?.lastFour
        val merchant = extractMerchant(lines)
        val (date, time) = extractDateTime(lines, smsTimestampMillis, notes)
        val confidence = computeConfidence(amount, currency, type, merchant, cardOrAccount, date, time)
        val missingFields = buildList {
            if (amount == null) add("amount")
            if (currency == Currency.UNKNOWN) add("currency")
            if (type == TransactionType.UNKNOWN) add("transactionType")
            if (merchant.isNullOrBlank()) add("merchant")
            if (cardOrAccount.isNullOrBlank()) add("accountOrCardLastFourDigits")
            if (date == null) add("transactionDate")
        }
        val finalStatus = if (amount == null || confidence < NEEDS_REVIEW_CONFIDENCE_FLOOR) {
            if (status == TransactionStatus.DECLINED) status else TransactionStatus.NEEDS_REVIEW
        } else status
        return ParsedTransaction(
            originalSender = sender,
            originalMessage = body,
            transactionType = type,
            amount = amount,
            currency = currency,
            merchant = merchant,
            accountOrCardLastFourDigits = cardOrAccount,
            transactionDate = date,
            transactionTime = time,
            status = finalStatus,
            confidence = confidence,
            parsingNotes = notes,
            parserName = name,
            parserVersion = version,
            matchedRules = matchedRules.toList(),
            missingFields = missingFields,
            identifierEvidence = identifiers,
        )
    }

    protected open fun detectCurrency(lines: List<ParsedLine>): Currency {
        val joined = lines.joinToString(" ") { it.label + " " + it.value }
        val lower = joined.lowercase(Locale.ROOT)
        return when {
            "sar" in lower || "ريال" in joined || "ر.س" in joined -> Currency.SAR
            "usd" in lower || "$" in joined -> Currency.USD
            "eur" in lower || "€" in joined -> Currency.EUR
            else -> Currency.UNKNOWN
        }
    }

    private fun detectCurrencyForMatcher(lines: List<ParsedLine>, matchedRules: MutableList<String>): Currency {
        val cur = detectCurrency(lines)
        if (cur != Currency.UNKNOWN) matchedRules.add("currency:${cur.name.lowercase()}")
        return cur
    }

    protected open fun detectType(lines: List<ParsedLine>): TransactionType {
        val joinedLabel = lines.joinToString(" ") { it.label.lowercase(Locale.ROOT) }
        val joinedAll = lines.joinToString(" ") { (it.label + " " + it.value).lowercase(Locale.ROOT) }
        val pairs = listOf(
            DECLINED_LABELS.map { it.lowercase(Locale.ROOT) } to TransactionType.DECLINED,
            ONLINE_PURCHASE_LABELS.map { it.lowercase(Locale.ROOT) } to TransactionType.ONLINE_PURCHASE,
            TRANSFER_IN_LABELS.map { it.lowercase(Locale.ROOT) } to TransactionType.TRANSFER_IN,
            TRANSFER_OUT_LABELS.map { it.lowercase(Locale.ROOT) } to TransactionType.TRANSFER_OUT,
            CASH_WITHDRAWAL_LABELS.map { it.lowercase(Locale.ROOT) } to TransactionType.CASH_WITHDRAWAL,
            CARD_PAYMENT_LABELS.map { it.lowercase(Locale.ROOT) } to TransactionType.CARD_PAYMENT,
            REFUND_LABELS.map { it.lowercase(Locale.ROOT) } to TransactionType.REFUND,
            SALARY_LABELS.map { it.lowercase(Locale.ROOT) } to TransactionType.SALARY,
            BANK_FEE_LABELS.map { it.lowercase(Locale.ROOT) } to TransactionType.BANK_FEE,
            PURCHASE_LABELS.map { it.lowercase(Locale.ROOT) } to TransactionType.PURCHASE,
        )
        return matchPair(pairs, joinedAll) ?: TransactionType.UNKNOWN
    }

    private fun matchPair(pairs: List<Pair<List<String>, TransactionType>>, text: String): TransactionType? {
        // Longer labels always win over their shorter substrings so that
        // "شراء عبر الإنترنت" is recognized as ONLINE_PURCHASE rather than the
        // generic "شراء" substring.
        var best: Pair<Int, TransactionType>? = null
        for ((labels, type) in pairs) for (label in labels) if (label in text) {
            val len = label.length
            if (best == null || len > best.first) best = len to type
        }
        return best?.second
    }

    protected open fun detectStatus(lines: List<ParsedLine>): TransactionStatus {
        val joinedLabel = lines.joinToString(" ") { it.label.lowercase(Locale.ROOT) }
        val joinedAll = lines.joinToString(" ") { (it.label + " " + it.value).lowercase(Locale.ROOT) }
        val pairs = listOf(
            DECLINED_LABELS.map { it.lowercase(Locale.ROOT) } to TransactionStatus.DECLINED,
            REVERSED_LABELS.map { it.lowercase(Locale.ROOT) } to TransactionStatus.REVERSED,
            PENDING_LABELS.map { it.lowercase(Locale.ROOT) } to TransactionStatus.PENDING,
        )
        return matchStatusPair(pairs, joinedLabel) ?: matchStatusPair(pairs, joinedAll) ?: TransactionStatus.COMPLETED
    }

    private fun matchStatusPair(pairs: List<Pair<List<String>, TransactionStatus>>, text: String): TransactionStatus? {
        for ((labels, type) in pairs) if (labels.any { it in text }) return type
        return null
    }

    protected open fun extractAmount(lines: List<ParsedLine>): BigDecimal? {
        val line = lines.firstOrNull { LineBasedFieldParser.containsAmountLabel(it.label) } ?: return null
        val amount = Regex("""[-+]?\d+(?:\.\d+)?""").find(line.value.replace(",", ""))?.value ?: return null
        return runCatching { BigDecimal(amount) }.getOrNull()?.takeIf { it.signum() > 0 }
    }

    protected open fun extractAmountWithNotes(lines: List<ParsedLine>, notes: MutableList<String>): BigDecimal? {
        val amount = extractAmount(lines)
        if (amount == null) notes.add("AMOUNT_NOT_RELIABLY_IDENTIFIED")
        return amount
    }

    /** Only labeled fields become identifier evidence; never unlabelled numbers. */
    protected open fun extractIdentifiers(lines: List<ParsedLine>): List<ParsedIdentifierEvidence> = buildList {
        for (line in lines) {
            val label = line.label.lowercase(Locale.ROOT)
            val type = when {
                "مدى" in label || "debit" in label -> com.baraa.masroof.data.db.AccountIdentifierType.DEBIT_CARD_LAST4
                "ائتمان" in label || "credit" in label -> com.baraa.masroof.data.db.AccountIdentifierType.CREDIT_CARD_LAST4
                "iban" in label || "آيبان" in label || "الايبان" in label -> com.baraa.masroof.data.db.AccountIdentifierType.IBAN_LAST4
                "محفظ" in label || "wallet" in label -> com.baraa.masroof.data.db.AccountIdentifierType.WALLET_LAST4
                LineBasedFieldParser.cardLabelRegex().matches(line.label) || LineBasedFieldParser.bankAccountLabelRegex().matches(line.label) -> com.baraa.masroof.data.db.AccountIdentifierType.ACCOUNT_LAST4
                else -> null
            } ?: continue
            val digits = LineBasedFieldParser.lastFourFromValue(line.value) ?: continue
            val role = when {
                "من" in label || "source" in label -> IdentifierRole.SOURCE
                "إلى" in label || "to" in label || "destination" in label -> IdentifierRole.DESTINATION
                else -> IdentifierRole.UNSPECIFIED
            }
            add(ParsedIdentifierEvidence(type, digits, role, 90, "label:${type.name}"))
        }
    }

    protected open fun extractLastFour(lines: List<ParsedLine>): String? = extractIdentifiers(lines).firstOrNull()?.lastFour

    protected open fun extractMerchant(lines: List<ParsedLine>): String? {
        for (line in lines) {
            if (LineBasedFieldParser.merchantLabelRegex().matches(line.label)) {
                val original = line.value
                val trimmed = original.trim().trim('.', ' ', ',')
                if (trimmed.isNotEmpty()) return trimmed
            }
        }
        return null
    }

    protected open fun extractDateTime(
        lines: List<ParsedLine>,
        smsTimestampMillis: Long?,
        notes: MutableList<String>,
    ): Pair<LocalDate?, LocalTime?> {
        val (date, time) = LineBasedFieldParser.parseDateTimeField(lines)
        if (date != null) {
            notes.add(if (time != null) "date and time from message body" else "date from message body")
            return date to time
        }
        if (smsTimestampMillis != null && smsTimestampMillis > 0L) {
            val zone = ZoneId.systemDefault()
            val instant = Instant.ofEpochMilli(smsTimestampMillis)
            notes.add("date from SMS metadata (no date found in message body)")
            return instant.atZone(zone).toLocalDate() to instant.atZone(zone).toLocalTime()
        }
        notes.add("no date in message body and no SMS timestamp available")
        return null to null
    }

    protected open fun computeConfidence(
        amount: BigDecimal?,
        currency: Currency,
        type: TransactionType,
        merchant: String?,
        cardOrAccount: String?,
        date: LocalDate?,
        time: LocalTime?,
    ): Int {
        var score = 0
        if (amount != null) score += 25
        if (currency != Currency.UNKNOWN) score += 10
        if (type != TransactionType.UNKNOWN) score += 25
        if (!merchant.isNullOrBlank()) score += 15
        if (!cardOrAccount.isNullOrBlank()) score += 10
        if (date != null) score += 10
        if (time != null) score += 5
        return score.coerceIn(0, 100)
    }

    private fun containsAny(text: String, keywords: List<String>): Boolean = keywords.any { it in text }

    private fun emptyResult(
        sender: String?,
        body: String?,
        notes: List<String>,
        matchedRules: List<String>,
    ): ParsedTransaction = ParsedTransaction(
        originalSender = sender,
        originalMessage = body,
        transactionType = TransactionType.UNKNOWN,
        amount = null,
        currency = Currency.UNKNOWN,
        merchant = null,
        accountOrCardLastFourDigits = null,
        transactionDate = null,
        transactionTime = null,
        status = TransactionStatus.NEEDS_REVIEW,
        confidence = 0,
        parsingNotes = notes,
        parserName = name,
        parserVersion = version,
        matchedRules = matchedRules,
        missingFields = listOf("amount", "currency", "transactionType", "merchant", "accountOrCardLastFourDigits", "transactionDate"),
    )

    companion object {
        const val NEEDS_REVIEW_CONFIDENCE_FLOOR: Int = 30
        private val ONLINE_PURCHASE_LABELS = listOf("شراء عبر الإنترنت", "شراء عبر الانترنت", "online purchase", "online_purchase", "Google Pay")
        private val PURCHASE_LABELS = listOf("عملية شراء", "شراء", "purchase", "pos purchase", "شراء عبر نقاط البيع")
        private val CASH_WITHDRAWAL_LABELS = listOf("سحب نقدي", "سحب من الصراف", "atm withdrawal", "cash withdrawal", "withdrawal")
        private val TRANSFER_OUT_LABELS = listOf("عملية حوالة مالية صادرة مقبولة", "حوالة مالية صادرة", "حوالة صادرة", "تحويل صادر", "حوالة", "outgoing transfer", "transfer out", "sent")
        private val TRANSFER_IN_LABELS = listOf("حوالة واردة", "حوالة واردة داخلية", "تحويل وارد", "حوالة", "وارد إليك", "incoming transfer", "transfer in", "received")
        private val CARD_PAYMENT_LABELS = listOf("سداد بطاقة", "سداد بطاقة ائتمانية", "card payment", "credit card payment", "سداد")
        private val REFUND_LABELS = listOf("استرداد", "مسترد", "refund", "reversed transaction")
        private val SALARY_LABELS = listOf("راتب", "salary", "wages")
        private val BANK_FEE_LABELS = listOf("رسوم", "bank fee", "service charge", "fee")
        private val DECLINED_LABELS = listOf("عملية مرفوضة", "مرفوضة", "مرفوض", "declined", "rejected", "failed")
        private val REVERSED_LABELS = listOf("معكوسة", "معكوس", "reversed", "rolled back")
        private val PENDING_LABELS = listOf("قيد المعالجة", "في الانتظار", "pending")
    }
}

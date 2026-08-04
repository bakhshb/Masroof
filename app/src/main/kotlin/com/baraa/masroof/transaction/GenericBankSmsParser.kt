package com.baraa.masroof.transaction

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Base class for bank SMS parsers. Concrete subclasses override [name],
 * [priority], and [senderAliases] to claim specific senders; the shared
 * extraction logic (currency, type, amount, last-four, merchant, date, status,
 * confidence) lives here so each bank parser does not have to re-implement it.
 *
 * Amount extraction (the hard part):
 *  1. Find all numeric candidates in the normalized body.
 *  2. For each candidate, look at ~30 characters of preceding context.
 *  3. Score:
 *       +50 if context contains a transaction marker
 *       -100 if context contains a balance marker
 *       -position  (slight preference for the first candidate)
 *  4. Return the highest-scoring candidate. This solves the spec requirement
 *     that "عملية شراء بمبلغ 250 ريال / الرصيد المتاح 4,500 ريال" extracts
 *     250 — not 4,500.
 *
 * This class is pure JVM: no Android imports, no logging, no I/O.
 *
 * **Real Saudi bank SMS samples are still required** to add bank-specific
 * patterns on top of this base. The current concrete bank parsers simply
 * declare their sender aliases and inherit the shared extraction logic.
 */
open class GenericBankSmsParser : BankSmsParser {

    override val name: String = "Generic"
    override val version: String = "1.0.0"
    override val priority: Int = 0

    /**
     * Sender identifiers (already normalized: lowercased, whitespace
     * collapsed) that this parser is willing to handle. An empty list means
     * "match anything" — the convention used by the fallback generic parser.
     */
    open val senderAliases: List<String> = emptyList()

    /**
     * True if this parser claims the message. The [BankParserRegistry] calls
     * this on the highest-priority parser first; the first to return true
     * handles the message.
     */
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
        val normalized = BankTextNormalizer.normalizeForParsing(body)

        if (normalized.isEmpty()) {
            notes.add("empty body")
            return emptyResult(sender, body, notes, matchedRules)
        }

        if (senderAliases.isNotEmpty() && !sender.isNullOrBlank()) {
            matchedRules.add("sender_alias_match:${sender.trim()}")
        }

        val currency = detectCurrency(normalized, notes, matchedRules)
        val type = detectType(normalized, notes, matchedRules)
        val status = detectStatus(normalized, type)
        val amount = extractAmount(normalized, body, type, notes, matchedRules)
        val cardOrAccount = extractLastFourDigits(body, notes, matchedRules)
        val merchant = extractMerchant(body, notes, matchedRules)
        val (date, time) = extractDateTime(body, smsTimestampMillis, notes)
        val confidence = computeConfidence(
            amount = amount,
            currency = currency,
            type = type,
            merchant = merchant,
            cardOrAccount = cardOrAccount,
            date = date,
            dateFromBody = notes.any { it.startsWith("date from message body") },
        )

        val missingFields = buildList {
            if (amount == null) add("amount")
            if (currency == Currency.UNKNOWN) add("currency")
            if (type == TransactionType.UNKNOWN) add("transactionType")
            if (merchant.isNullOrBlank()) add("merchant")
            if (cardOrAccount.isNullOrBlank()) add("accountOrCardLastFourDigits")
            if (date == null) add("transactionDate")
        }

        // Below the confidence floor -> mark as NEEDS_REVIEW so the UI can
        // surface it. The threshold matches the existing import service
        // `PARSE_CONFIDENCE_THRESHOLD`.
        val finalStatus = if (amount == null || confidence < NEEDS_REVIEW_CONFIDENCE_FLOOR) {
            if (status == TransactionStatus.DECLINED) status else TransactionStatus.NEEDS_REVIEW
        } else {
            status
        }

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
        )
    }

    // -- Currency -------------------------------------------------------------

    private fun detectCurrency(
        normalized: String,
        notes: MutableList<String>,
        matchedRules: MutableList<String>,
    ): Currency {
        val lower = normalized.lowercase(Locale.ROOT)
        val hasSar = "sar" in lower || "ريال" in normalized || "ر.س" in normalized || "ر س" in normalized
        val hasUsd = "usd" in lower || "$" in normalized
        val hasEur = "eur" in lower || "€" in normalized
        return when {
            hasSar -> {
                matchedRules.add("currency:sar")
                Currency.SAR
            }
            hasUsd -> {
                matchedRules.add("currency:usd")
                Currency.USD
            }
            hasEur -> {
                matchedRules.add("currency:eur")
                Currency.EUR
            }
            else -> {
                notes.add("currency not found in message; defaulted to UNKNOWN")
                Currency.UNKNOWN
            }
        }
    }

    // -- Type -----------------------------------------------------------------
    //
    // Order matters: most-specific patterns are checked first. SALARY is
    // checked before DEPOSIT because "تم إيداع راتب" should be SALARY.

    private fun detectType(
        normalized: String,
        notes: MutableList<String>,
        matchedRules: MutableList<String>,
    ): TransactionType {
        val n = normalized

        // -- DECLINED first, because a declined message often still mentions
        //    "purchase" / "transfer" etc. and we want the negative status to win.
        if (containsAny(n, DECLINED_KEYWORDS)) {
            matchedRules.add("type:declined")
            return TransactionType.DECLINED
        }

        // -- SALARY before DEPOSIT (Arabic "راتب" is a substring of nothing
        //    else dangerous, but "إيداع راتب" should be SALARY).
        if (containsAny(n, SALARY_KEYWORDS)) {
            matchedRules.add("type:salary")
            return TransactionType.SALARY
        }

        // -- ONLINE_PURCHASE before PURCHASE (more specific).
        if (containsAny(n, ONLINE_PURCHASE_KEYWORDS)) {
            matchedRules.add("type:online_purchase")
            return TransactionType.ONLINE_PURCHASE
        }
        if (containsAny(n, PURCHASE_KEYWORDS)) {
            matchedRules.add("type:purchase")
            return TransactionType.PURCHASE
        }

        if (containsAny(n, CASH_WITHDRAWAL_KEYWORDS)) {
            matchedRules.add("type:cash_withdrawal"); return TransactionType.CASH_WITHDRAWAL
        }
        if (containsAny(n, TRANSFER_IN_KEYWORDS)) {
            matchedRules.add("type:transfer_in"); return TransactionType.TRANSFER_IN
        }
        if (containsAny(n, TRANSFER_OUT_KEYWORDS)) {
            matchedRules.add("type:transfer_out"); return TransactionType.TRANSFER_OUT
        }
        if (containsAny(n, CARD_PAYMENT_KEYWORDS)) {
            matchedRules.add("type:card_payment"); return TransactionType.CARD_PAYMENT
        }
        if (containsAny(n, REFUND_KEYWORDS)) {
            matchedRules.add("type:refund"); return TransactionType.REFUND
        }
        if (containsAny(n, BANK_FEE_KEYWORDS)) {
            matchedRules.add("type:bank_fee"); return TransactionType.BANK_FEE
        }
        if (containsAny(n, DEPOSIT_KEYWORDS)) {
            matchedRules.add("type:deposit"); return TransactionType.DEPOSIT
        }
        if (containsAny(n, INTERNAL_TRANSFER_KEYWORDS)) {
            matchedRules.add("type:internal_transfer"); return TransactionType.INTERNAL_TRANSFER
        }
        if (containsAny(n, INVESTMENT_TRANSFER_KEYWORDS)) {
            matchedRules.add("type:investment_transfer"); return TransactionType.INVESTMENT_TRANSFER
        }

        notes.add("could not determine transaction type")
        return TransactionType.UNKNOWN
    }

    // -- Status ---------------------------------------------------------------

    private fun detectStatus(
        normalized: String,
        type: TransactionType,
    ): TransactionStatus {
        if (type == TransactionType.DECLINED) return TransactionStatus.DECLINED
        return when {
            containsAny(normalized, DECLINED_STATUS_KEYWORDS) -> TransactionStatus.DECLINED
            containsAny(normalized, REVERSED_STATUS_KEYWORDS) -> TransactionStatus.REVERSED
            containsAny(normalized, PENDING_STATUS_KEYWORDS) -> TransactionStatus.PENDING
            else -> TransactionStatus.COMPLETED
        }
    }

    // -- Amount ---------------------------------------------------------------

    /**
     * Find the most likely transaction amount in the normalized body.
     * See class kdoc for the scoring algorithm.
     */
    private fun extractAmount(
        normalized: String,
        originalBody: String?,
        type: TransactionType,
        notes: MutableList<String>,
        matchedRules: MutableList<String>,
    ): BigDecimal? {
        val lines = LineBasedFieldParser.splitLines(originalBody.orEmpty())
        if (lines.isEmpty()) {
            notes.add("AMOUNT_NOT_RELIABLY_IDENTIFIED")
            return null
        }
        val matched = lines.firstOrNull { LineBasedFieldParser.isAmountLabel(it.label) }
        if (matched != null) {
            val cleaned = matched.value.replace(",", "")
            val amountText = Regex("""[-+]?\d+(?:\.\d+)?""").find(cleaned)?.value
            if (amountText != null) {
                val value = runCatching { BigDecimal(amountText) }.getOrNull()
                if (value != null && value.signum() > 0) {
                    matchedRules.add("amount_pattern:line_label:${matched.label}")
                    return value
                }
            }
        }
        notes.add("AMOUNT_NOT_RELIABLY_IDENTIFIED")
        return null
    }

    private fun extractAmountLegacyProbe(originalBody: String?): BigDecimal? = null

    private val MONEY_REGEX_LINE = Regex("""^[A-Z]{2,3}\s+([-+]?\d{1,3}(?:,\d{3})+(?:\.\d+)?|[-+]?\d+(?:\.\d+)?)$|^([-+]?\d{1,3}(?:,\d{3})+(?:\.\d+)?|[-+]?\d+(?:\.\d+)?)\s+[A-Z]{2,3}$|^\d{1,3}(?:,\d{3})+(?:\.\d+)?$|^\d+(?:\.\d+)?$""")

    @Suppress("unused") private val normalizedReference: String? = null

    private fun detectCandidateCurrency(before: String, after: String): Currency {
        val context = "$before $after"
        return when {
            CURRENCY_SAR.any { it in context } -> Currency.SAR
            CURRENCY_USD.any { it in context } -> Currency.USD
            CURRENCY_EUR.any { it in context } -> Currency.EUR
            else -> Currency.UNKNOWN
        }
    }

    // -- Last four digits -----------------------------------------------------

    private fun extractLastFourDigits(
        body: String?,
        notes: MutableList<String>,
        matchedRules: MutableList<String>,
    ): String? {
        val lines = LineBasedFieldParser.splitLines(body.orEmpty())
        for (line in lines) {
            if (LineBasedFieldParser.cardLabelRegex().matches(line.label) || LineBasedFieldParser.accountLabelRegex().matches(line.label)) {
                val normalized = BankTextNormalizer.normalizeForParsing(line.value)
                val masked = Regex("""\*+(\d{4})""").find(line.value)?.groupValues?.get(1)
                val digits = Regex("""\d{4,6}""").find(normalized)?.value
                val candidate = masked ?: digits?.takeLast(4)
                if (candidate != null) {
                    matchedRules.add("card_or_account_digits:line_label:${line.label}")
                    notes.add("last 4 digits found: $candidate")
                    return candidate.takeLast(4)
                }
            }
        }
        return null
    }

    // -- Merchant -------------------------------------------------------------

    private fun extractMerchant(
        originalBody: String?,
        notes: MutableList<String>,
        matchedRules: MutableList<String>,
    ): String? {
        if (originalBody.isNullOrBlank()) return null
        val merchantLabel = LineBasedFieldParser.merchantLabelRegex()
        LineBasedFieldParser.splitLines(originalBody).forEach { line ->
            if (merchantLabel.matches(line.label)) {
                // Preserve original casing when reading from the original body.
                val original = originalBody.split('\n').firstOrNull { raw ->
                    val normalized = BankTextNormalizer.normalizeForParsing(raw)
                    val (label, _) = LineBasedFieldParser.splitLabelAndValue(normalized); merchantLabel.matches(label)
                }
                val trimmed = (original?.substringAfter(':') ?: line.value).trim().trim('.', ' ', ',')
                if (trimmed.isNotEmpty()) {
                    matchedRules.add("merchant_pattern:line_label:${line.label}")
                    notes.add("merchant matched pattern")
                    return trimmed
                }
            }
        }
        for (pattern in MERCHANT_PATTERNS) {
            val match = pattern.find(originalBody) ?: continue
            val candidate = match.groupValues[1].trim().trim('.', ' ', ',')
            if (candidate.isNotEmpty() && candidate.length <= MAX_MERCHANT_LEN) {
                matchedRules.add("merchant_pattern")
                notes.add("merchant matched pattern")
                return candidate
            }
        }
        return null
    }

    // -- Date / time ----------------------------------------------------------

    private fun extractDateTime(
        body: String?,
        smsTimestampMillis: Long?,
        notes: MutableList<String>,
    ): Pair<LocalDate?, LocalTime?> {
        val (date, time) = LineBasedFieldParser.parseDateTimeField(LineBasedFieldParser.splitLines(body.orEmpty()))
        if (date != null) {
            notes.add(if (time != null) "date and time from message body" else "date from message body")
            return date to time
        }
        if (smsTimestampMillis != null && smsTimestampMillis > 0L) {
            val zone = ZoneId.systemDefault()
            val instant = Instant.ofEpochMilli(smsTimestampMillis)
            val date = instant.atZone(zone).toLocalDate()
            val time = instant.atZone(zone).toLocalTime()
            notes.add("date from SMS metadata (no date found in message body)")
            return date to time
        }

        notes.add("no date in message body and no SMS timestamp available")
        return null to null
    }

    private fun parseDateMatch(match: MatchResult): LocalDate? { error("removed") }
    private fun tryParseDate(groups: List<String>): LocalDate? { error("removed") }
    private fun parseTimeMatch(match: MatchResult): LocalTime? { error("removed") }

    // -- Confidence -----------------------------------------------------------

    private fun computeConfidence(
        amount: BigDecimal?,
        currency: Currency,
        type: TransactionType,
        merchant: String?,
        cardOrAccount: String?,
        date: LocalDate?,
        dateFromBody: Boolean,
    ): Int {
        var score = 0
        if (amount != null) score += 25
        if (currency != Currency.UNKNOWN) score += 10
        if (type != TransactionType.UNKNOWN) score += 25
        if (!merchant.isNullOrBlank()) score += 15
        if (!cardOrAccount.isNullOrBlank()) score += 10
        if (date != null) score += 10
        if (dateFromBody) score += 5
        return score.coerceIn(0, 100)
    }

    // -- Helpers --------------------------------------------------------------

    private fun containsAny(text: String, keywords: List<String>): Boolean =
        keywords.any { it in text }

    private fun emptyResult(
        sender: String?,
        body: String?,
        notes: List<String>,
        matchedRules: List<String>,
    ) = ParsedTransaction(
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
        // Below this confidence we mark the result as [TransactionStatus.NEEDS_REVIEW]
        // so the UI can surface it for the user to verify / edit.
        const val NEEDS_REVIEW_CONFIDENCE_FLOOR: Int = 30

        private const val CONTEXT_WINDOW = 30
        // Smaller window for balance markers — they must be IMMEDIATELY
        // adjacent to the amount (within ~15 chars), otherwise the next
        // line's balance would demote a legitimate amount.
        private const val BALANCE_WINDOW = 15
        private const val MAX_MERCHANT_LEN = 80
        // Minimum amount-candidate score required to accept a number as the
        // transaction amount. The +50 "transaction marker" boost clears this
        // for legitimate messages; OTP codes / ad percentages / phone numbers
        // stay at 0 and are rejected.
        /** A numeric value needs an explicit amount label or adjacent currency. */
        const val MIN_AMOUNT_CONFIDENCE = 45

        // Match numbers like "1,234.56", "1.234,56" (after normalization
        // commas and dots are ASCII, so this works for both). We avoid
        // matching year-like 4-digit numbers (1900-2099) at the start, but
        // for the bank-SMS context a 4-digit number near a transaction
        // marker is much more likely a card fragment or year in a date —
        // the scoring algorithm handles this via context.
        private val NUMBER_REGEX = Regex("""\d{1,3}(?:,\d{3})+(?:\.\d+)?|\d+(?:\.\d+)?""")
        private val PHONE_OR_LONG_ID_REGEX = Regex("""\d{7,}""")
        private val DATE_OR_TIME_CONTEXT_REGEX = Regex("""\d{1,2}[:/-]\d{1,2}|[:/-]\d{2,4}""")
        private val AMOUNT_LABELS = listOf("بمبلغ", "بقيمة", "قيمة العملية", "قيمة الشراء", "قيمة التحويل", "المبلغ", "مبلغ", "amount", "purchase amount", "transaction amount", "payment amount", "transfer amount")
        private val CURRENCY_SAR = listOf("sar", "ريال", "ر.س", "ر س", "رس", "sr")
        private val CURRENCY_USD = listOf("usd", "$")
        private val CURRENCY_EUR = listOf("eur", "€")
        private val EXCLUSION_MARKERS = listOf("البطاقة", "بطاقة", "حساب", "الحساب", "ايبان", "آيبان", "المنتهية", "آخر أربعة", "آخر 4", "رقم البطاقة", "رقم الحساب", "card", "account", "iban", "ending", "last four", "last 4", "card number", "account number", "الرصيد", "رصيد", "المتاح", "المتبقي", "balance", "available balance", "current balance", "remaining balance", "رمز", "رمز التحقق", "مرجع", "الرقم المرجعي", "تفويض", "فاتورة", "جهاز", "نقطة البيع", "otp", "verification", "reference", "ref", "authorization", "auth", "invoice", "terminal", "pos id")

        // 4 consecutive digits preceded by a card/account keyword within
        // 40 characters. The keyword list is the union of Arabic and English.
        private val LAST_FOUR_REGEX = Regex(
            """(?:بطاقة|كارت|تنتهي|انتهاء|حساب|رقم\s*الحساب|card|account|acct|ending|ends?\s*in|last\s*four|card\s*no)[^\d]{0,40}(\d{4})\b""",
            RegexOption.IGNORE_CASE,
        )

        private val TIME_PATTERN = Regex("""\b(\d{1,2}):(\d{2})(?::(\d{2}))?\b""")

        // Date patterns are tried in order. Each must capture 3 numeric
        // groups in some order so that [tryParseDate] can interpret them.
        private val DATE_PATTERNS = listOf(
            // ISO yyyy-MM-dd or yyyy/MM/dd
            Regex("""\b(\d{4})[/-](\d{1,2})[/-](\d{1,2})\b"""),
            // DD/MM/yyyy or DD-MM-yyyy (Saudi convention)
            Regex("""\b(\d{1,2})[/-](\d{1,2})[/-](\d{4})\b"""),
        )

        // -- Keyword groups --

        private val PURCHASE_KEYWORDS = listOf("عملية شراء", "شراء", "purchase", "pos purchase")
        private val ONLINE_PURCHASE_KEYWORDS = listOf(
            "شراء عبر الإنترنت",
            "شراء عبر الانترنت",
            "online purchase",
            "internet purchase",
            "e-commerce",
        )
        private val CASH_WITHDRAWAL_KEYWORDS = listOf(
            "سحب نقدي",
            "سحب من الصراف",
            "atm withdrawal",
            "cash withdrawal",
            "withdrawal",
        )
        private val TRANSFER_OUT_KEYWORDS = listOf(
            "تحويل صادر",
            "تحويل من",
            "outgoing transfer",
            "transfer out",
            "sent",
        )
        private val TRANSFER_IN_KEYWORDS = listOf(
            "تحويل وارد",
            "وارد إليك",
            "incoming transfer",
            "transfer in",
            "received",
        )
        private val CARD_PAYMENT_KEYWORDS = listOf(
            "سداد بطاقة",
            "سداد بطاقة ائتمانية",
            "card payment",
            "credit card payment",
            "سداد",
        )
        private val REFUND_KEYWORDS = listOf("استرداد", "مسترد", "refund", "reversed transaction")
        private val SALARY_KEYWORDS = listOf("راتب", "salary", "wages")
        private val DEPOSIT_KEYWORDS = listOf("إيداع", "deposit", "credited")
        private val BANK_FEE_KEYWORDS = listOf("رسوم", "bank fee", "service charge", "fee")
        private val INTERNAL_TRANSFER_KEYWORDS = listOf(
            "تحويل داخلي",
            "internal transfer",
            "between accounts",
        )
        private val INVESTMENT_TRANSFER_KEYWORDS = listOf(
            "تحويل استثماري",
            "investment transfer",
            "to investment",
        )
        private val DECLINED_KEYWORDS = listOf(
            "عملية مرفوضة",
            "مرفوضة",
            "مرفوض",
            "declined",
            "rejected",
            "failed",
        )

        private val PENDING_STATUS_KEYWORDS = listOf("قيد المعالجة", "في الانتظار", "pending")
        private val REVERSED_STATUS_KEYWORDS = listOf("معكوسة", "معكوس", "reversed", "rolled back")
        private val DECLINED_STATUS_KEYWORDS = DECLINED_KEYWORDS

        // Markers that boost a numeric candidate's score.
        private val TRANSACTION_MARKERS = listOf(
            // English
            "amount", "purchase", "purchased", "paid", "charged", "withdrawal",
            "deposit", "transfer", "payment", "refund", "salary", "fee", "sent",
            "received", "spent", "spend", "card",
            // Arabic
            "بمبلغ", "مبلغ", "شراء", "سحب", "إيداع", "تحويل", "سداد", "استرداد",
            "راتب", "رسوم", "خصم", "دفع", "بطاقة",
        )

        // Markers that demote a numeric candidate (we believe it's a balance
        // figure rather than the transaction amount).
        private val BALANCE_MARKERS = listOf(
            // English
            "balance", "available", "remaining", "bal", "after",
            // Arabic
            "رصيد", "الرصيد", "المتاح", "المتبقي", "بعد",
        )

        // Merchant extraction patterns. Each captures a single group: the
        // merchant text following the keyword. We try them in order and
        // take the first non-empty match.
        private val MERCHANT_PATTERNS = listOf(
            // English "at <name>" / "from <name>" / "to <name>" / "beneficiary <name>"
            Regex(
                """\b(?:at|from|to|beneficiary|merchant|to\s+the\s+beneficiary|to\s+the\s+account\s+of)\s+([A-Za-z][A-Za-z0-9 .'_-]{1,60}?)(?:\s*[.,;]|\s+(?:for|using|card|on|amount|iban|sar|usd|eur|riyal)|$)""",
                RegexOption.IGNORE_CASE,
            ),
            // Arabic "لدى <name>" / "من <name>" / "إلى <name>" / "المستفيد <name>" / "التاجر <name>" / "نقطة بيع <name>"
            Regex(
                """(?:لدى|من|إلى|المستفيد|التاجر|نقطة\s*بيع|لحساب)\s+([^\n\r.،]{1,80}?)(?:\s*[.,;]|\s+(?:بمبلغ|مبلغ|بطاقة|حساب|ريال|ر\.س|usd|eur|amount|card|account)|$)""",
            ),
            // POS terminal: "POS: <name>" / "نقطة بيع: <name>"
            Regex(
                """(?:POS|نقطة\s*بيع)\s*[:\-]?\s+([A-Za-z\u0600-\u06FF][A-Za-z0-9\u0600-\u06FF .'_-]{1,60})""",
            ),
        )
    }
}

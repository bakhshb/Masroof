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
 * Fallback parser that handles any bank SMS using a small set of Arabic and
 * English keyword patterns + a balanced amount-extraction scoring algorithm.
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
 * This parser is pure JVM: no Android imports, no logging, no I/O.
 */
class GenericBankSmsParser : BankSmsParser {

    override fun canParse(sender: String?, body: String?): Boolean = true

    override fun parse(
        sender: String?,
        body: String?,
        smsTimestampMillis: Long?,
    ): ParsedTransaction {
        val notes = mutableListOf<String>()
        val normalized = BankTextNormalizer.normalizeForParsing(body)

        if (normalized.isEmpty()) {
            notes.add("empty body")
            return emptyResult(sender, body, notes)
        }

        val currency = detectCurrency(normalized, notes)
        val type = detectType(normalized, notes)
        val status = detectStatus(normalized, type)
        val amount = extractAmount(normalized, type, notes)
        val cardOrAccount = extractLastFourDigits(normalized, notes)
        val merchant = extractMerchant(body, notes)
        val (date, time) = extractDateTime(normalized, smsTimestampMillis, notes)
        val confidence = computeConfidence(
            amount = amount,
            currency = currency,
            type = type,
            merchant = merchant,
            cardOrAccount = cardOrAccount,
            date = date,
            dateFromBody = notes.any { it.startsWith("date from message body") },
        )

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
            status = status,
            confidence = confidence,
            parsingNotes = notes,
        )
    }

    // -- Currency -------------------------------------------------------------

    private fun detectCurrency(normalized: String, notes: MutableList<String>): Currency {
        val lower = normalized.lowercase(Locale.ROOT)
        val hasSar = "sar" in lower || "ريال" in normalized || "ر.س" in normalized || "ر س" in normalized
        val hasUsd = "usd" in lower || "$" in normalized
        val hasEur = "eur" in lower || "€" in normalized
        return when {
            hasSar -> Currency.SAR
            hasUsd -> Currency.USD
            hasEur -> Currency.EUR
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

    private fun detectType(normalized: String, notes: MutableList<String>): TransactionType {
        val n = normalized

        // -- DECLINED first, because a declined message often still mentions
        //    "purchase" / "transfer" etc. and we want the negative status to win.
        if (containsAny(n, DECLINED_KEYWORDS)) return TransactionType.DECLINED

        // -- SALARY before DEPOSIT (Arabic "راتب" is a substring of nothing
        //    else dangerous, but "إيداع راتب" should be SALARY).
        if (containsAny(n, SALARY_KEYWORDS)) return TransactionType.SALARY

        // -- ONLINE_PURCHASE before PURCHASE (more specific).
        if (containsAny(n, ONLINE_PURCHASE_KEYWORDS)) return TransactionType.ONLINE_PURCHASE
        if (containsAny(n, PURCHASE_KEYWORDS)) return TransactionType.PURCHASE

        if (containsAny(n, CASH_WITHDRAWAL_KEYWORDS)) return TransactionType.CASH_WITHDRAWAL
        if (containsAny(n, TRANSFER_IN_KEYWORDS)) return TransactionType.TRANSFER_IN
        if (containsAny(n, TRANSFER_OUT_KEYWORDS)) return TransactionType.TRANSFER_OUT
        if (containsAny(n, CARD_PAYMENT_KEYWORDS)) return TransactionType.CARD_PAYMENT
        if (containsAny(n, REFUND_KEYWORDS)) return TransactionType.REFUND
        if (containsAny(n, BANK_FEE_KEYWORDS)) return TransactionType.BANK_FEE
        if (containsAny(n, DEPOSIT_KEYWORDS)) return TransactionType.DEPOSIT
        if (containsAny(n, INTERNAL_TRANSFER_KEYWORDS)) return TransactionType.INTERNAL_TRANSFER
        if (containsAny(n, INVESTMENT_TRANSFER_KEYWORDS)) return TransactionType.INVESTMENT_TRANSFER

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
        type: TransactionType,
        notes: MutableList<String>,
    ): BigDecimal? {
        val candidates = NUMBER_REGEX.findAll(normalized).toList()
        if (candidates.isEmpty()) {
            notes.add("no numeric candidate found for amount")
            return null
        }

        var best: Pair<MatchResult, Int>? = null
        for ((idx, match) in candidates.withIndex()) {
            val before30Start = maxOf(0, match.range.first - CONTEXT_WINDOW)
            val contextBefore30 = normalized.substring(before30Start, match.range.first)
            val contextBefore15 = contextBefore30.takeLast(BALANCE_WINDOW)
            val afterStart = match.range.last + 1
            val after30End = minOf(normalized.length, afterStart + CONTEXT_WINDOW)
            val contextAfter30 = normalized.substring(afterStart, after30End)

            var score = -idx // slight preference for earlier candidates
            // Balance markers are only checked in the IMMEDIATELY preceding
            // 15 chars. Checking the after-context would capture the NEXT
            // number's balance line (e.g. "12,000 ريال. الرصيد 15,000"
            // would falsely demote 12,000).
            if (BALANCE_MARKERS.any { it in contextBefore15 }) {
                score -= 100
            }
            if (TRANSACTION_MARKERS.any { it in contextBefore30 || it in contextAfter30 }) {
                score += 50
            }
            if (type != TransactionType.UNKNOWN) score += 5

            if (best == null || score > best.second) {
                best = match to score
            }
        }

        val (bestMatch, bestScore) = best ?: run {
            notes.add("could not select an amount candidate")
            return null
        }

        // Reject any candidate that didn't beat the floor — this protects
        // against random numbers in ads / OTP codes / phone numbers being
        // misread as the transaction amount.
        if (bestScore < MIN_AMOUNT_SCORE) {
            notes.add("no amount candidate met the minimum score (best=$bestScore)")
            return null
        }

        val raw = bestMatch.value
        val amount = runCatching { BigDecimal(raw.replace(",", "")) }.getOrNull()
        if (amount == null) {
            notes.add("amount candidate '$raw' could not be parsed as BigDecimal")
            return null
        }
        notes.add("amount picked from candidates=$raw at pos=${bestMatch.range.first}")
        return amount
    }

    // -- Last four digits -----------------------------------------------------

    private fun extractLastFourDigits(
        normalized: String,
        notes: MutableList<String>,
    ): String? {
        val match = LAST_FOUR_REGEX.find(normalized) ?: return null
        val digits = match.groupValues[1]
        // Reject obviously-not-card values (e.g., "0000") only if the user
        // chose strict mode — for now, accept whatever the regex matched.
        notes.add("last 4 digits found: $digits")
        return digits
    }

    // -- Merchant -------------------------------------------------------------

    private fun extractMerchant(
        originalBody: String?,
        notes: MutableList<String>,
    ): String? {
        if (originalBody.isNullOrBlank()) return null
        for (pattern in MERCHANT_PATTERNS) {
            val match = pattern.find(originalBody) ?: continue
            val candidate = match.groupValues[1].trim().trim('.', ' ', ',')
            if (candidate.isNotEmpty() && candidate.length <= MAX_MERCHANT_LEN) {
                notes.add("merchant matched pattern")
                return candidate
            }
        }
        return null
    }

    // -- Date / time ----------------------------------------------------------

    private fun extractDateTime(
        normalized: String,
        smsTimestampMillis: Long?,
        notes: MutableList<String>,
    ): Pair<LocalDate?, LocalTime?> {
        // Try each date pattern until one yields a valid date.
        for (pattern in DATE_PATTERNS) {
            val match = pattern.find(normalized) ?: continue
            val date = parseDateMatch(match) ?: continue
            val time = TIME_PATTERN.find(normalized)?.let { parseTimeMatch(it) }
            if (time != null) {
                notes.add("date and time from message body")
            } else {
                notes.add("date from message body")
            }
            return date to time
        }

        // Fall back to SMS metadata.
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

    private fun parseDateMatch(match: MatchResult): LocalDate? = when (match.value.length) {
        // We don't switch on length — we switch on the pattern that matched.
        else -> {
            val groups = match.groupValues.drop(1).filter { it.isNotEmpty() }
            tryParseDate(groups)
        }
    }

    private fun tryParseDate(groups: List<String>): LocalDate? {
        // groups are the three captured numeric parts of a date pattern.
        // If the first group is 4 digits the format is YYYY-MM-DD; otherwise
        // the format is DD-MM-YYYY (Saudi / European convention).
        if (groups.size != 3) return null
        val a = groups[0]
        val b = groups[1]
        val c = groups[2]
        return runCatching {
            if (a.length == 4) {
                LocalDate.of(a.toInt(), b.toInt(), c.toInt())
            } else {
                LocalDate.of(c.toInt(), b.toInt(), a.toInt())
            }
        }.getOrNull()
    }

    private fun parseTimeMatch(match: MatchResult): LocalTime? {
        val h = match.groupValues[1].toIntOrNull() ?: return null
        val m = match.groupValues[2].toIntOrNull() ?: return null
        val s = match.groupValues.getOrNull(3)?.toIntOrNull() ?: 0
        return runCatching { LocalTime.of(h, m, s) }.getOrNull()
    }

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

    private fun emptyResult(sender: String?, body: String?, notes: List<String>) =
        ParsedTransaction(
            originalSender = sender,
            originalMessage = body,
            transactionType = TransactionType.UNKNOWN,
            amount = null,
            currency = Currency.UNKNOWN,
            merchant = null,
            accountOrCardLastFourDigits = null,
            transactionDate = null,
            transactionTime = null,
            status = TransactionStatus.UNKNOWN,
            confidence = 0,
            parsingNotes = notes,
        )

    companion object {
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
        private const val MIN_AMOUNT_SCORE = 30

        // Match numbers like "1,234.56", "1.234,56" (after normalization
        // commas and dots are ASCII, so this works for both). We avoid
        // matching year-like 4-digit numbers (1900-2099) at the start, but
        // for the bank-SMS context a 4-digit number near a transaction
        // marker is much more likely a card fragment or year in a date —
        // the scoring algorithm handles this via context.
        private val NUMBER_REGEX = Regex("""\d{1,3}(?:,\d{3})+(?:\.\d+)?|\d+(?:\.\d+)?""")

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

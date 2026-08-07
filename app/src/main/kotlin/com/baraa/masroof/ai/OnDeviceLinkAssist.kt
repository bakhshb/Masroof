package com.baraa.masroof.ai

import com.baraa.masroof.transaction.FinancialTreatment

/**
 * On-device link assist parsing.
 *
 * Gemma-1B is unreliable at JSON, so the preferred model output is simple
 * KEY=VALUE lines. JSON is still accepted. If the model fails entirely,
 * [suggestFromSmsBody] reads the SMS text directly (last-4 + keywords).
 */
object OnDeviceLinkAssist {

    val ALLOWED_TREATMENTS: Set<FinancialTreatment> = setOf(
        FinancialTreatment.EXPENSE,
        FinancialTreatment.INCOME,
        FinancialTreatment.INTERNAL_TRANSFER,
        FinancialTreatment.CREDIT_CARD_PAYMENT,
        FinancialTreatment.CASH_WITHDRAWAL,
        FinancialTreatment.BANK_FEE,
        FinancialTreatment.REFUND,
        FinancialTreatment.INVESTMENT,
    )

    private const val MAX_REASON_LENGTH = 120
    private const val MAX_BODY_CHARS = 2_000

    const val ON_DEVICE_TIMEOUT_MILLIS: Long = 90_000L

    fun buildPrompt(request: LinkAssistRequest): String {
        val body = request.smsBody.trim().take(MAX_BODY_CHARS)
        val accountLines = request.accounts.joinToString("\n") { acc ->
            val ids = acc.identifierLast4s.joinToString(",")
            "- id=${acc.id} name=${acc.displayName} type=${acc.accountType} last4=[$ids]"
        }
        val exampleId = request.accounts.first().id
        return buildString {
            appendLine("Link this bank SMS to an owned account.")
            appendLine("Reply with EXACTLY these lines (no JSON, no markdown):")
            appendLine("TREATMENT=EXPENSE")
            appendLine("ACCOUNT=$exampleId")
            appendLine("ACCOUNT2=")
            appendLine("CONF=80")
            appendLine("REASON=شراء")
            appendLine("TREATMENT one of: EXPENSE,INCOME,INTERNAL_TRANSFER,CREDIT_CARD_PAYMENT,CASH_WITHDRAWAL,BANK_FEE,REFUND,INVESTMENT")
            appendLine("ACCOUNT = source for EXPENSE/BANK_FEE/CASH_WITHDRAWAL, destination for INCOME/REFUND.")
            appendLine("For INTERNAL_TRANSFER/CREDIT_CARD_PAYMENT set ACCOUNT and ACCOUNT2 to two different ids.")
            appendLine("Accounts:")
            appendLine(accountLines)
            appendLine("SMS:")
            appendLine(body)
            appendLine("Answer:")
        }
    }

    fun buildRepairPrompt(rawModelOutput: String, request: LinkAssistRequest): String {
        val exampleId = request.accounts.first().id
        val ids = request.accounts.joinToString(",") { it.id.toString() }
        return buildString {
            appendLine("Rewrite as KEY=VALUE lines only:")
            appendLine("TREATMENT=EXPENSE")
            appendLine("ACCOUNT=$exampleId")
            appendLine("ACCOUNT2=")
            appendLine("CONF=70")
            appendLine("REASON=تصحيح")
            appendLine("Allowed ACCOUNT ids: $ids")
            appendLine("Broken:")
            appendLine(rawModelOutput.trim().take(500))
            appendLine("Answer:")
        }
    }

    data class ParseResult(
        val suggestion: LinkAssistSuggestion?,
        val diagnosticAr: String,
        val rawPreview: String,
    )

    fun parseSuggestion(raw: String, request: LinkAssistRequest): LinkAssistSuggestion? =
        parseDetailed(raw, request).suggestion

    fun parseDetailed(raw: String, request: LinkAssistRequest): ParseResult {
        val preview = sanitizePreview(raw)
        return try {
            parseKeyValue(raw, request)?.let {
                return ParseResult(it, "ok_lines", preview)
            }
            parseJsonSuggestion(raw, request)?.let {
                return ParseResult(it, "ok_json", preview)
            }
            parseLooseText(raw, request)?.let {
                return ParseResult(it, "ok_loose", preview)
            }
            ParseResult(null, diagnoseFailure(raw), preview)
        } catch (_: Throwable) {
            ParseResult(null, "تعذّر تحليل الرد", preview)
        }
    }

    /**
     * Deterministic suggestion from the SMS body itself (not the bank parser
     * fields). Used when the LLM output is unusable.
     */
    fun suggestFromSmsBody(request: LinkAssistRequest): LinkAssistSuggestion? {
        val treatment = treatmentFromSmsText(request.smsBody)
            ?: treatmentFromParsedType(request.transactionType)
            ?: FinancialTreatment.EXPENSE
        val accountId = matchAccountBySmsLastFour(request)
            ?: matchAccountByLastFour(request)
            ?: matchAccountBySenderHint(request)
            ?: request.accounts.singleOrNull()?.id
            ?: request.accounts.firstOrNull()?.id
            ?: return null
        if (treatment.requiresTwoAccounts) {
            // Prefer a usable single-sided guess over returning nothing.
            val singleSided = FinancialTreatment.EXPENSE
            return finalizeSuggestion(
                treatment = singleSided,
                sourceIn = accountId,
                destIn = accountId,
                confidence = 55,
                reason = "من نص الرسالة (حساب واحد — أكّد إن كانت تحويلًا داخليًا)",
                request = request,
            )
        }
        return finalizeSuggestion(
            treatment = treatment,
            sourceIn = accountId,
            destIn = accountId,
            confidence = 65,
            reason = "من نص الرسالة (آخر 4 أرقام + كلمات الرسالة)",
            request = request,
        )
    }

    fun deterministicFallback(request: LinkAssistRequest): LinkAssistSuggestion? =
        suggestFromSmsBody(request)

    fun sanitizePreview(raw: String): String =
        raw.replace('\n', ' ').replace(Regex("\\s+"), " ").trim().take(140)

    // -- KEY=VALUE -----------------------------------------------------------

    private fun parseKeyValue(raw: String, request: LinkAssistRequest): LinkAssistSuggestion? {
        val map = linkedMapOf<String, String>()
        for (line in raw.lineSequence()) {
            val trimmed = line.trim().trimStart('`', '-', '*', ' ')
            val idx = trimmed.indexOf('=')
            if (idx <= 0) continue
            val key = trimmed.substring(0, idx).trim().uppercase()
            val value = trimmed.substring(idx + 1).trim().trim('"', '\'', '`')
            if (key.isNotEmpty()) map[key] = value
        }
        // Also accept "TREATMENT: EXPENSE" / "ACCOUNT: 1"
        if (map.isEmpty()) {
            for (line in raw.lineSequence()) {
                val trimmed = line.trim()
                val idx = trimmed.indexOf(':')
                if (idx <= 0) continue
                val key = trimmed.substring(0, idx).trim().uppercase()
                val value = trimmed.substring(idx + 1).trim().trim('"', '\'', '`')
                if (key in setOf("TREATMENT", "ACCOUNT", "ACCOUNT2", "SOURCE", "DEST", "CONF", "CONFIDENCE", "REASON", "REASONAR")) {
                    map[key] = value
                }
            }
        }
        if (map.isEmpty()) return null

        val treatment = resolveTreatment(
            map["TREATMENT"] ?: map["TYPE"] ?: map["KIND"],
        ) ?: return null

        var account = resolveAccountToken(map["ACCOUNT"] ?: map["SOURCE"] ?: map["ACCOUNTID"], request)
        var account2 = resolveAccountToken(map["ACCOUNT2"] ?: map["DEST"] ?: map["DESTINATION"], request)
        val conf = map["CONF"]?.toIntOrNull()
            ?: map["CONFIDENCE"]?.toIntOrNull()
            ?: map["CONF"]?.toDoubleOrNull()?.let { if (it <= 1.0) (it * 100).toInt() else it.toInt() }
            ?: 75
        val reason = map["REASON"] ?: map["REASONAR"]

        if (treatment.requiresTwoAccounts) {
            if (account == null || account2 == null) return null
            return finalizeSuggestion(treatment, account, account2, conf, reason, request)
        }
        if (account == null && account2 != null) account = account2
        return finalizeSuggestion(treatment, account, account, conf, reason, request)
    }

    // -- JSON (legacy / optional) --------------------------------------------

    private fun parseJsonSuggestion(raw: String, request: LinkAssistRequest): LinkAssistSuggestion? {
        val normalized = normalizeModelJson(raw)
        val json = extractJsonObject(normalized) ?: return null
        val declared = stringField(json, "treatment")
            ?: unquotedToken(json, "treatment")
            ?: stringField(json, "type")
        val treatment = resolveTreatment(declared) ?: return null
        if (treatment !in ALLOWED_TREATMENTS) return null

        var sourceId = resolveAccountId(json, listOf("sourceAccountId", "source_id", "sourceId", "accountId", "account"), request)
        var destId = resolveAccountId(json, listOf("destinationAccountId", "destination_id", "destinationId", "destAccountId"), request)
        if (sourceId == null) sourceId = resolveAccountByName(stringField(json, "sourceAccountName"), request)
        if (destId == null) destId = resolveAccountByName(stringField(json, "destinationAccountName"), request)
        if (sourceId == null && destId == null) {
            resolveAccountByName(stringField(json, "accountName") ?: stringField(json, "account"), request)?.let {
                sourceId = it
                destId = it
            }
        }
        return finalizeSuggestion(treatment, sourceId, destId, parseConfidence(json) ?: 70, stringField(json, "reasonAr"), request)
    }

    private fun parseLooseText(raw: String, request: LinkAssistRequest): LinkAssistSuggestion? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        val treatment = resolveTreatment(text)
            ?: ALLOWED_TREATMENTS.firstOrNull { text.contains(it.name, ignoreCase = true) }
            ?: return null
        val idsInText = Regex("""\b(\d{1,6})\b""").findAll(text).mapNotNull { it.groupValues[1].toLongOrNull() }
            .filter { id -> request.accounts.any { it.id == id } }
            .distinct()
            .toList()
        var sourceId: Long? = null
        var destId: Long? = null
        when {
            treatment.requiresTwoAccounts && idsInText.size >= 2 -> {
                sourceId = idsInText[0]
                destId = idsInText[1]
            }
            else -> {
                val id = idsInText.firstOrNull()
                    ?: request.accounts.firstOrNull { text.contains(it.displayName, ignoreCase = true) }?.id
                sourceId = id
                destId = id
            }
        }
        return finalizeSuggestion(treatment, sourceId, destId, 55, "من رد حر للنموذج", request)
    }

    private fun finalizeSuggestion(
        treatment: FinancialTreatment,
        sourceIn: Long?,
        destIn: Long?,
        confidence: Int,
        reason: String?,
        request: LinkAssistRequest,
    ): LinkAssistSuggestion? {
        var sourceId = sourceIn
        var destId = destIn
        when (treatment) {
            FinancialTreatment.EXPENSE, FinancialTreatment.BANK_FEE, FinancialTreatment.CASH_WITHDRAWAL -> {
                if (sourceId == null && destId != null) {
                    sourceId = destId
                    destId = null
                }
            }
            FinancialTreatment.INCOME, FinancialTreatment.REFUND -> {
                if (destId == null && sourceId != null) {
                    destId = sourceId
                    sourceId = null
                }
            }
            else -> Unit
        }

        val matched = matchAccountBySmsLastFour(request) ?: matchAccountByLastFour(request)
        if (treatment.requiresTwoAccounts) {
            if (sourceId == null || destId == null || sourceId == destId) return null
        } else {
            when (treatment) {
                FinancialTreatment.INCOME, FinancialTreatment.REFUND -> {
                    if (destId == null) destId = matched
                    if (destId == null && request.accounts.size == 1) destId = request.accounts.first().id
                    if (destId == null) return null
                    sourceId = null
                }
                FinancialTreatment.EXPENSE, FinancialTreatment.BANK_FEE, FinancialTreatment.CASH_WITHDRAWAL -> {
                    if (sourceId == null) sourceId = matched
                    if (sourceId == null && request.accounts.size == 1) sourceId = request.accounts.first().id
                    if (sourceId == null) return null
                    destId = null
                }
                else -> {
                    if (sourceId == null) sourceId = matched
                    if (destId == null) destId = matched
                    if (sourceId == null || destId == null || sourceId == destId) return null
                }
            }
        }

        return LinkAssistSuggestion(
            treatment = treatment,
            sourceAccountId = sourceId,
            destinationAccountId = destId,
            confidence = confidence.coerceIn(0, 100),
            reasonAr = reason?.trim().orEmpty().ifEmpty { "اقتراح من نص الرسالة" }.take(MAX_REASON_LENGTH),
        )
    }

    private fun diagnoseFailure(raw: String): String = when {
        raw.isBlank() -> "النموذج أعاد ردًا فارغًا"
        !raw.contains('=') && !raw.contains('{') -> "الرد ليس بالصيغة المتوقعة (أسطر KEY=VALUE)"
        raw.contains('{') -> "JSON غير مكتمل أو غير صالح"
        else -> "تعذّر قراءة TREATMENT/ACCOUNT من الرد"
    }

    private fun treatmentFromSmsText(sms: String): FinancialTreatment? {
        val t = sms.lowercase()
        return when {
            listOf("استرداد", "مرتجع", "refund").any { t.contains(it) } -> FinancialTreatment.REFUND
            listOf("رسوم", "fee", "عمولة").any { t.contains(it) } -> FinancialTreatment.BANK_FEE
            listOf("سحب نقد", "صراف", "atm", "cash withdraw").any { t.contains(it) } ->
                FinancialTreatment.CASH_WITHDRAWAL
            listOf("سداد بطاقة", "دفع بطاقة", "card payment").any { t.contains(it) } ->
                FinancialTreatment.CREDIT_CARD_PAYMENT
            listOf("تحويل داخلي", "بين حساباتك", "internal transfer").any { t.contains(it) } ->
                FinancialTreatment.INTERNAL_TRANSFER
            listOf("راتب", "salary", "حوالة واردة", "ايداع", "إيداع", "deposit").any { t.contains(it) } ->
                FinancialTreatment.INCOME
            listOf("شراء", "مشتريات", "pos", "purchase", "مدى", "apple pay", "مدفوعة").any { t.contains(it) } ->
                FinancialTreatment.EXPENSE
            listOf("حوالة صادرة", "تحويل إلى", "transfer to").any { t.contains(it) } ->
                FinancialTreatment.EXPENSE
            else -> null
        }
    }

    private fun treatmentFromParsedType(type: String?): FinancialTreatment = when (type?.uppercase()) {
        "SALARY", "DEPOSIT", "TRANSFER_IN" -> FinancialTreatment.INCOME
        "REFUND" -> FinancialTreatment.REFUND
        "INTERNAL_TRANSFER" -> FinancialTreatment.INTERNAL_TRANSFER
        "CARD_PAYMENT" -> FinancialTreatment.CREDIT_CARD_PAYMENT
        "CASH_WITHDRAWAL" -> FinancialTreatment.CASH_WITHDRAWAL
        "BANK_FEE" -> FinancialTreatment.BANK_FEE
        else -> FinancialTreatment.EXPENSE
    }

    private fun resolveTreatment(raw: String?): FinancialTreatment? {
        if (raw.isNullOrBlank()) return null
        val t = raw.trim().trim('"', '\'')
        runCatching { FinancialTreatment.valueOf(t.uppercase().replace(' ', '_').replace('-', '_')) }
            .getOrNull()
            ?.takeIf { it in ALLOWED_TREATMENTS }
            ?.let { return it }
        val lower = t.lowercase()
        return when {
            listOf("expense", "purchase", "مصروف", "شراء", "دفع").any { lower.contains(it) } ->
                FinancialTreatment.EXPENSE
            listOf("income", "salary", "دخل", "راتب", "ايداع", "إيداع").any { lower.contains(it) } ->
                FinancialTreatment.INCOME
            listOf("internal", "تحويل داخلي").any { lower.contains(it) } ->
                FinancialTreatment.INTERNAL_TRANSFER
            listOf("credit_card", "card_payment", "سداد بطاقة").any { lower.contains(it) } ->
                FinancialTreatment.CREDIT_CARD_PAYMENT
            listOf("cash_withdrawal", "atm", "سحب نقد", "صراف").any { lower.contains(it) } ->
                FinancialTreatment.CASH_WITHDRAWAL
            listOf("bank_fee", "fee", "رسوم").any { lower.contains(it) } ->
                FinancialTreatment.BANK_FEE
            listOf("refund", "استرداد", "مرتجع").any { lower.contains(it) } ->
                FinancialTreatment.REFUND
            listOf("investment", "استثمار").any { lower.contains(it) } ->
                FinancialTreatment.INVESTMENT
            else -> null
        }
    }

    private fun resolveAccountToken(token: String?, request: LinkAssistRequest): Long? {
        val t = token?.trim().orEmpty()
        if (t.isEmpty() || t.equals("null", true) || t == "-" || t == "none") return null
        t.toLongOrNull()?.let { id ->
            if (request.accounts.any { it.id == id }) return id
        }
        return resolveAccountByName(t, request)
    }

    private fun resolveAccountId(json: String, keys: List<String>, request: LinkAssistRequest): Long? {
        for (key in keys) {
            longField(json, key)?.let { id ->
                if (request.accounts.any { it.id == id }) return id
            }
            stringField(json, key)?.let { resolveAccountToken(it, request) }?.let { return it }
        }
        return null
    }

    private fun resolveAccountByName(name: String?, request: LinkAssistRequest): Long? {
        val n = name?.trim().orEmpty()
        if (n.isEmpty() || n.equals("null", true)) return null
        request.accounts.firstOrNull { it.displayName.equals(n, ignoreCase = true) }?.id?.let { return it }
        request.accounts.firstOrNull {
            it.displayName.contains(n, ignoreCase = true) || n.contains(it.displayName, ignoreCase = true)
        }?.id?.let { return it }
        return null
    }

    private fun matchAccountByLastFour(request: LinkAssistRequest): Long? {
        val last4 = request.lastFourEvidence?.takeLast(4)?.filter { it.isDigit() } ?: return null
        if (last4.length != 4) return null
        return request.accounts.filter { acc ->
            acc.identifierLast4s.any { it.takeLast(4) == last4 }
        }.singleOrNull()?.id
    }

    private fun matchAccountBySmsLastFour(request: LinkAssistRequest): Long? {
        return runCatching {
            val body = request.smsBody
            val candidates = LinkedHashSet<String>()
            // *1234 / xxxx1234
            Regex("""[*xX]{1,4}(\d{4})""").findAll(body).forEach { candidates += it.groupValues[1] }
            // Any 4 digits after Arabic/English account/card words (no Arabic inside the regex engine).
            val markers = listOf("بطاقة", "حساب", "card", "acct", "Card", "CARD")
            for (marker in markers) {
                var from = 0
                while (true) {
                    val at = body.indexOf(marker, from, ignoreCase = true)
                    if (at < 0) break
                    val slice = body.substring(at, minOf(body.length, at + marker.length + 12))
                    Regex("""(\d{4})""").find(slice)?.groupValues?.getOrNull(1)?.let { candidates += it }
                    from = at + marker.length
                }
            }
            if (candidates.isEmpty()) return@runCatching null
            val matches = candidates.flatMap { last4 ->
                request.accounts.filter { acc -> acc.identifierLast4s.any { it.takeLast(4) == last4 } }
            }.distinctBy { it.id }
            matches.singleOrNull()?.id
        }.getOrNull()
    }

    /** When last-4 is ambiguous/missing, prefer the only account whose name overlaps the sender. */
    private fun matchAccountBySenderHint(request: LinkAssistRequest): Long? {
        val sender = request.sender?.trim().orEmpty()
        if (sender.isBlank()) return null
        val hits = request.accounts.filter { acc ->
            val name = acc.displayName.trim()
            name.isNotBlank() && (
                sender.contains(name, ignoreCase = true) ||
                    name.contains(sender, ignoreCase = true) ||
                    sender.contains(name.take(4), ignoreCase = true)
                )
        }
        return hits.singleOrNull()?.id
    }

    private fun normalizeModelJson(raw: String): String {
        var text = raw.trim()
            .replace('“', '"')
            .replace('”', '"')
        if (text.contains("'treatment'") || text.contains("'sourceAccountId'") || text.contains("'EXPENSE'")) {
            text = text.replace('\'', '"')
        }
        return text.replace(Regex(",\\s*}"), "}").replace(Regex(",\\s*]"), "]")
    }

    fun extractJsonObject(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) return trimmed
        val fenced = runCatching {
            Regex(
                pattern = "```(?:json)?\\s*(\\{.*?})\\s*```",
                options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            ).find(trimmed)?.groupValues?.getOrNull(1)
        }.getOrNull()
        if (fenced != null) return fenced.trim()
        val start = trimmed.indexOf('{')
        if (start < 0) return null
        val end = trimmed.lastIndexOf('}')
        if (end > start) return trimmed.substring(start, end + 1)
        val partial = trimmed.substring(start)
        val opens = partial.count { it == '{' } - partial.count { it == '}' }
        if (opens > 0 && partial.length > 8) return partial + "}".repeat(opens)
        return null
    }

    private fun stringField(json: String, key: String): String? {
        val quoted = Regex("\"$key\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"", RegexOption.IGNORE_CASE)
            .find(json)?.groupValues?.getOrNull(1)
        if (quoted != null) return quoted.replace("\\\"", "\"").replace("\\\\", "\\")
        if (Regex("\"$key\"\\s*:\\s*null", RegexOption.IGNORE_CASE).containsMatchIn(json)) return null
        return null
    }

    private fun unquotedToken(json: String, key: String): String? =
        Regex("\"$key\"\\s*:\\s*([A-Za-z_]+)", RegexOption.IGNORE_CASE)
            .find(json)?.groupValues?.getOrNull(1)

    private fun longField(json: String, key: String): Long? {
        if (Regex("\"$key\"\\s*:\\s*null", RegexOption.IGNORE_CASE).containsMatchIn(json)) return null
        return Regex("\"$key\"\\s*:\\s*(-?\\d+)", RegexOption.IGNORE_CASE)
            .find(json)?.groupValues?.getOrNull(1)?.toLongOrNull()
    }

    private fun parseConfidence(json: String): Int? {
        val floatMatch = Regex("\"confidence\"\\s*:\\s*(-?\\d+\\.\\d+)", RegexOption.IGNORE_CASE).find(json)
            ?.groupValues?.getOrNull(1)?.toDoubleOrNull()
        if (floatMatch != null) {
            return if (floatMatch <= 1.0) (floatMatch * 100).toInt().coerceIn(0, 100)
            else floatMatch.toInt().coerceIn(0, 100)
        }
        return Regex("\"confidence\"\\s*:\\s*(-?\\d+)", RegexOption.IGNORE_CASE).find(json)
            ?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 100)
    }
}

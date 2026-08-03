package com.baraa.masroof.ai

/**
 * Minimal JSON parser + extractor for the AI response.
 *
 * The provider is expected to return a JSON object with these keys:
 *   {
 *     "category_id": <number>,
 *     "category_name": "<string>",
 *     "normalized_merchant_name": "<string>",
 *     "confidence": <0..100>,
 *     "explanation": "<short string>",
 *   }
 *
 * The parser is **strict**: it rejects the response if any required key
 * is missing, if the type is wrong, or if `confidence` is outside 0..100.
 * No exception escapes the public surface — all errors come back as
 * [FailureReason.MALFORMED] or [FailureReason.INVALID_CONFIDENCE].
 */
object AiResponseParser {

    /** Cap on explanation length. Anything longer is truncated. */
    private const val MAX_EXPLANATION_LENGTH = 200

    /**
     * Validate the parsed payload against the request's allowed categories
     * and return a sanitized [AiCategorizationResult], or null when the
     * response cannot be used.
     */
    fun validate(
        rawBody: String,
        request: AiCategorizationRequest,
        providerName: String,
        modelName: String,
    ): AiCategorizationResult? {
        val map = parseJsonObject(rawBody) ?: return null
        val categoryId = (map["category_id"] as? Number)?.toLong() ?: return null
        val categoryName = (map["category_name"] as? String)?.trim().orEmpty()
        val normalizedMerchant = (map["normalized_merchant_name"] as? String)?.trim().orEmpty()
        val confidence = (map["confidence"] as? Number)?.toInt() ?: return null
        val explanation = (map["explanation"] as? String)?.trim().orEmpty()
        // Reject invented categories — the id MUST be in the allowed list.
        if (request.allowedCategories.none { it.id == categoryId }) return null
        // Reject category names that don't match an allowed one (some
        // providers echo back our id with a different label — that's fine,
        // as long as the id matches).
        if (categoryName.isEmpty() || normalizedMerchant.isEmpty()) return null
        // Confidence must be in 0..100.
        if (confidence !in 0..100) return null
        return AiCategorizationResult(
            categoryId = categoryId,
            categoryName = categoryName,
            normalizedMerchantName = normalizedMerchant,
            confidence = confidence,
            explanation = if (explanation.length > MAX_EXPLANATION_LENGTH) {
                explanation.substring(0, MAX_EXPLANATION_LENGTH)
            } else {
                explanation
            },
            providerName = providerName,
            modelName = modelName,
            responseVersion = AiPromptBuilder.RESPONSE_VERSION,
        )
    }

    // -- Minimal JSON parser (objects only, no arrays needed here) --------

    private sealed interface Token {
        data object LBrace : Token
        data object RBrace : Token
        data class Str(val value: String) : Token
        data class Num(val value: Double) : Token
        data object Comma : Token
        data object Colon : Token
        data object Eof : Token
    }

    private fun tokenize(input: String): List<Token> {
        val out = ArrayList<Token>()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            when {
                c == '{' -> { out.add(Token.LBrace); i++ }
                c == '}' -> { out.add(Token.RBrace); i++ }
                c == ',' -> { out.add(Token.Comma); i++ }
                c == ':' -> { out.add(Token.Colon); i++ }
                c.isWhitespace() -> i++
                c == '"' -> {
                    val sb = StringBuilder()
                    i++
                    while (i < input.length && input[i] != '"') {
                        if (input[i] == '\\' && i + 1 < input.length) {
                            when (input[i + 1]) {
                                '"', '\\', '/' -> { sb.append(input[i + 1]); i += 2 }
                                'n' -> { sb.append('\n'); i += 2 }
                                'r' -> { sb.append('\r'); i += 2 }
                                't' -> { sb.append('\t'); i += 2 }
                                'b' -> { sb.append('\b'); i += 2 }
                                'f' -> { sb.append('\u000C'); i += 2 }
                                'u' -> {
                                    if (i + 5 < input.length) {
                                        val hex = input.substring(i + 2, i + 6)
                                        try {
                                            sb.append(hex.toInt(16).toChar())
                                        } catch (_: Throwable) { sb.append(input[i + 1]); i += 2 }
                                        i += 6
                                    } else {
                                        sb.append(input[i]); i++
                                    }
                                }
                                else -> { sb.append(input[i]); i++ }
                            }
                        } else {
                            sb.append(input[i]); i++
                        }
                    }
                    i++ // closing "
                    out.add(Token.Str(sb.toString()))
                }
                c == '-' -> {
                    // Only treat '-' as a number start when followed by a digit.
                    if (i + 1 < input.length && input[i + 1].isDigit()) {
                        val start = i
                        i++
                        while (i < input.length && (input[i].isDigit() || input[i] == '.')) i++
                        out.add(Token.Num(input.substring(start, i).toDouble()))
                    } else {
                        out.add(Token.Str(input.substring(i, i + 1))); i++
                    }
                }
                c.isDigit() -> {
                    val start = i
                    while (i < input.length && (input[i].isDigit() || input[i] == '.')) i++
                    out.add(Token.Num(input.substring(start, i).toDouble()))
                }
                else -> { out.add(Token.Str(input.substring(i, i + 1))); i++ }
            }
        }
        out.add(Token.Eof)
        return out
    }

    private fun parseJsonObject(raw: String): Map<String, Any?>? {
        val tokens = tokenize(raw)
        if (tokens.firstOrNull() != Token.LBrace) return null
        return try {
            parseObject(tokens, 0) as Map<String, Any?>?
        } catch (_: Throwable) {
            null
        }
    }

    private var tokenIdx = 0
    private fun parseObject(tokens: List<Token>, startIdx: Int): Any? {
        tokenIdx = startIdx
        // Skip the opening brace and parse the body.
        if (tokens[tokenIdx] != Token.LBrace) return null
        tokenIdx++
        return parseObjectBody(tokens)
    }

    private fun parseObjectBody(tokens: List<Token>): Map<String, Any?>? {
        val map = LinkedHashMap<String, Any?>()
        if (tokens[tokenIdx] == Token.RBrace) { tokenIdx++; return map }
        while (true) {
            if (tokens[tokenIdx] !is Token.Str) return null
            val key = (tokens[tokenIdx] as Token.Str).value
            tokenIdx++
            if (tokens[tokenIdx] != Token.Colon) return null
            tokenIdx++
            val value = parseValue(tokens) ?: return null
            map[key] = value
            if (tokens[tokenIdx] == Token.Comma) {
                tokenIdx++
                continue
            }
            if (tokens[tokenIdx] == Token.RBrace) {
                tokenIdx++
                return map
            }
            return null
        }
    }

    private fun parseValue(tokens: List<Token>): Any? = when (val t = tokens[tokenIdx]) {
        is Token.Str -> { tokenIdx++; t.value }
        is Token.Num -> { tokenIdx++; t.value }
        Token.LBrace -> { tokenIdx++; parseObjectBody(tokens) }
        else -> { tokenIdx++; null }
    }
}
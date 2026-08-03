package com.baraa.masroof.ai

/**
 * Builds the (very short) prompt sent to the AI provider. Lives in code so
 * we can keep [PROMPT_VERSION] in lock-step with the response parser.
 *
 * The prompt NEVER contains:
 *  - the raw SMS body
 *  - any account number / last-4
 *  - any sender phone number
 *  - any timestamp or balance
 *  - any user history
 *
 * The prompt is constructed **entirely from fields on
 * [AiCategorizationRequest]** — those fields are themselves sanitized by
 * the caller. The categories list is provided so the model cannot invent
 * new categories.
 */
object AiPromptBuilder {

    /** Bump when the prompt or response schema changes. */
    const val PROMPT_VERSION: String = "v1"

    /** Expected response JSON shape, used both in the prompt and for parsing. */
    const val RESPONSE_VERSION: String = "v1"

    /**
     * Build the system message (Arabic for ar, English for en). The user
     * message is the [request] itself.
     */
    fun systemPrompt(language: String): String = when (language.lowercase()) {
        "ar" -> ARABIC_SYSTEM
        else -> ENGLISH_SYSTEM
    }

    /**
     * Build the user message — the minimal merchant-only payload. The
     * output is safe to log for debugging (no PII).
     */
    fun userPrompt(request: AiCategorizationRequest): String {
        val allowed = request.allowedCategories
            .joinToString("\n") { "  - id=${it.id} | ${it.nameAr}" }
        return buildString {
            appendLine("merchant=${request.normalizedMerchant}")
            appendLine("transaction_type=${request.transactionType}")
            appendLine("channel=${request.channel.displayNameAr}")
            appendLine("currency=${request.currency}")
            appendLine("amount_bucket=${request.amountBucket.displayNameAr}")
            if (request.includeExactAmount && request.exactAmountBucketOnly != null) {
                appendLine("exact_amount=${request.exactAmountBucketOnly}")
            }
            appendLine("language=${request.language}")
            appendLine()
            appendLine("allowed_categories:")
            appendLine(allowed)
        }.trimEnd()
    }

    private val ARABIC_SYSTEM = """
        أنت مساعد تصنيف مصروفات. مهمتك اقتراح تصنيف واحد فقط من قائمة التصنيفات المتاحة.
        اختر التصنيف الأنسب بناءً على اسم التاجر. لا تخترع تصنيفات جديدة.
        أعد النتيجة بصيغة JSON فقط بدون أي شرح آخر.
        أعد نسبة ثقة بين 0 و100. أعد ثقة منخفضة (أقل من 60) إذا لم تكن متأكدًا.
        لا تتخذ أي قرار مالي (مثل: تحويل داخلي، سداد بطاقة، استرداد، استثمار، حذف عملية).
        استخدم اسم التاجر كإشارة رئيسية، ونطاق المبلغ ونوع القناة كإشارات مساعدة فقط.
    """.trimIndent()

    private val ENGLISH_SYSTEM = """
        You are a spending-categorization assistant. Suggest exactly one category from the provided allowed list.
        Choose based primarily on the merchant name. Do not invent new categories.
        Return JSON only — no other prose. Confidence must be 0..100. Use low confidence (<60) if uncertain.
        Do NOT make financial decisions (no internal transfers, no card payments, no refunds, no investments, no delete).
        Use merchant name as the primary signal. Amount bucket and channel are supporting signals only.
    """.trimIndent()
}
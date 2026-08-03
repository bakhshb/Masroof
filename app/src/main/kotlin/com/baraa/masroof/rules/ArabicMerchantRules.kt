package com.baraa.masroof.rules

/**
 * Data-driven Arabic merchant classification rules. Each entry is a regex
 * (lowercased before comparison) + a category name + a confidence score +
 * a diagnostic rule name. The rule engine applies these in order; the first
 * match wins. Lives in its own object so future maintenance can move it to
 * a JSON file without changing the rule code.
 *
 * Vague terms that should NOT trigger a category on their own (e.g. a body
 * that just says "شركة" or "مؤسسة") are listed in [VAGUE_BLACKLIST].
 *
 * ## Word boundaries
 *
 * Java's `\b` is ASCII-only; Arabic letters are not `\w`. We use a Unicode
 * boundary `(?<![\\p{L}\\p{N}])` / `(?![\\p{L}\\p{N}])` instead so that
 * "بندة" matches inside "تم الشراء من سوبرماركت بندة" without being
 * embedded inside a longer Arabic token.
 */
object ArabicMerchantRules {

    /**
     * Build a regex of `(?<![\\p{L}\\p{N}])(token)(?![\\p{L}\\p{N}])` for
     * each token. This is the closest analogue to `\b` that works with
     * Arabic (and is also valid for ASCII tokens like "stc" or "kfc").
     */
    private fun boundaryRegex(tokens: List<String>): Regex {
        val escaped = tokens.joinToString("|") { Regex.escape(it) }
        return Regex("""(?<![\p{L}\p{N}])($escaped)(?![\p{L}\p{N}])""")
    }

    data class Pattern(
        val regex: Regex,
        val categoryName: String,
        val confidence: Int,
        val ruleName: String,
    )

    val GROCERIES = listOf(
        Pattern(
            boundaryRegex(
                listOf(
                    "سوبرماركت", "هايبرماركت", "هايبر", "تموينات", "بقالة", "بقاله",
                    "أسواق", "اسواق", "سوق المركزي", "بنده", "بندة",
                    "العثيم", "الدانوب", "كارفور", "لولو هايبر", "لولو", "تميم",
                    "بيم", "نستو", "ماركت", "هايبر بنده", "بن داود",
                )
            ),
            "مقاضي", 90, "ArabicGroceries",
        ),
    )

    val RESTAURANTS = listOf(
        Pattern(
            boundaryRegex(
                listOf(
                    "مطعم", "مطاعم", "مطعام", "كوفي", "قهوة", "كافيه",
                    "مقهى", "مقھى", "حلويات", "توصيل طعام", "توصيل",
                    "جاهز", "برقر", "كنتاكي", "ماكدونالدز", "بيتزا",
                    "شاورما", "فلافل", "كبسة", "مندي", "البيك", "بنده",
                )
            ),
            "مطاعم", 90, "ArabicRestaurants",
        ),
    )

    val FUEL = listOf(
        Pattern(
            boundaryRegex(
                listOf(
                    "محطة", "محطه", "وقود", "بنزين", "محروقات", "ديزل",
                    "غاز", "أرامكس", "أرامكو",
                )
            ),
            "وقود", 90, "ArabicFuel",
        ),
    )

    val PHARMACY = listOf(
        Pattern(
            boundaryRegex(
                listOf(
                    "صيدلية", "صيدليه", "مستشفى", "مستشفي",
                    "عيادة", "عياده", "مختبر", "تحاليل طبية",
                    "أشعة", "طبيب",
                )
            ),
            "صيدلية", 90, "ArabicPharmacy",
        ),
    )

    val TELECOM = listOf(
        Pattern(
            boundaryRegex(
                listOf(
                    "اتصالات", "جوال", "انترنت", "إنترنت", "شحن رصيد",
                    "stc", "موبايلي", "زين", "بيانات", "فواتير",
                )
            ),
            "جوال", 90, "ArabicTelecom",
        ),
    )

    val EDUCATION = listOf(
        Pattern(
            boundaryRegex(
                listOf(
                    "مدرسة", "مدارس", "جامعة", "رسوم دراسية", "تعليم",
                    "مكتبة", "كتب", "كورس",
                )
            ),
            "رسوم دراسية", 80, "ArabicEducation",
        ),
    )

    val TRANSPORT = listOf(
        Pattern(
            boundaryRegex(
                listOf(
                    "تاكسي", "أوبر", "كريم", "اوبر", "نقل",
                    "مواقف", "جراج", "موقف سيارات",
                )
            ),
            "نقل وتوصيل", 80, "ArabicTransport",
        ),
    )

    /** All non-vague patterns, in priority order. */
    val ALL: List<Pattern> = GROCERIES + RESTAURANTS + FUEL + PHARMACY + TELECOM + EDUCATION + TRANSPORT

    /**
     * Tokens that, on their own, are too vague to trigger a category.
     * If the message text contains ONLY one of these (and no other rule
     * match), the rule engine falls through to PENDING_REVIEW.
     */
    val VAGUE_BLACKLIST: Set<String> = setOf(
        "شركة", "مؤسسة", "مؤسسات", "الشركة", "المؤسسة", "المؤسسات",
        "متجر", "محل", "السوق",
    )
}
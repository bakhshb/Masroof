package com.baraa.masroof.sms

import com.baraa.masroof.data.db.AccountIdentifierType
import com.baraa.masroof.data.db.MessagePatternDefinitionEntity
import com.baraa.masroof.data.db.MessagePatternStatus
import com.baraa.masroof.data.db.PatternCanonicalField
import com.baraa.masroof.data.db.PatternFieldDefinitionEntity
import com.baraa.masroof.data.db.PatternFieldRole
import com.baraa.masroof.data.db.PatternOrigin
import com.baraa.masroof.data.db.PatternValueType
import com.baraa.masroof.data.repository.MessagePattern
import com.baraa.masroof.transaction.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Approved templates are the source of truth for import parsing.
 * Generic heuristics must not override template semantics.
 */
class TemplateDrivenImportTest {

    private val posBody = """
        POS Purchase (Apple Pay)
        Credit Card: 3478
        at: MAREA DHAIATNA Co
        of: 202.50 SAR
        on: 2026-07-26 16:43
        Available Balance: 5035.61 SAR
        Due Amount: 13549.5 SAR
    """.trimIndent()

    private val posTemplate = """
        POS Purchase
        Credit Card: {CREDIT_CARD_LAST4}
        at: {MERCHANT}
        of: {AMOUNT} SAR
        on: {DATE} {TIME}
        Available Balance: {AVAILABLE_BALANCE} SAR
        Due Amount: {TOTAL_DUE} SAR
    """.trimIndent()

    private fun approvedPattern(
        id: Long = 1L,
        template: String = posTemplate,
        type: String = TransactionType.PURCHASE.name,
        canonicalKey: String = "TYPE:POS_PURCHASE|CREDIT_CARD_LAST4|MERCHANT|AMOUNT|DATETIME",
        status: MessagePatternStatus = MessagePatternStatus.APPROVED,
    ): MessagePattern {
        val def = MessagePatternDefinitionEntity(
            id = id,
            senderProfileId = 10L,
            userFriendlyName = "شراء عبر نقاط البيع",
            normalizedSignature = "sig-$id",
            canonicalKey = canonicalKey,
            templateText = template,
            transactionType = type,
            status = status,
            isActive = status == MessagePatternStatus.APPROVED,
            origin = PatternOrigin.USER_TRAINED,
            userConfirmed = true,
            exampleCount = 5,
            normalizationVersion = com.baraa.masroof.sms.NORMALIZATION_VERSION,
            createdAt = 0L,
            updatedAt = 0L,
        )
        val fields = listOf(
            PatternFieldDefinitionEntity(
                patternId = id,
                canonicalField = PatternCanonicalField.CREDIT_CARD_LAST4,
                placeholderToken = "CREDIT_CARD_LAST4",
                sourceLabel = "Credit Card",
                role = PatternFieldRole.SOURCE,
                valueType = PatternValueType.LAST4,
            ),
            PatternFieldDefinitionEntity(
                patternId = id,
                canonicalField = PatternCanonicalField.MERCHANT,
                placeholderToken = "MERCHANT",
                sourceLabel = "at",
                valueType = PatternValueType.TEXT,
            ),
            PatternFieldDefinitionEntity(
                patternId = id,
                canonicalField = PatternCanonicalField.TRANSACTION_AMOUNT,
                placeholderToken = "AMOUNT",
                sourceLabel = "of",
                required = true,
                valueType = PatternValueType.MONEY,
            ),
            PatternFieldDefinitionEntity(
                patternId = id,
                canonicalField = PatternCanonicalField.TRANSACTION_DATE,
                placeholderToken = "DATE",
                sourceLabel = "on",
                valueType = PatternValueType.DATE,
            ),
            PatternFieldDefinitionEntity(
                patternId = id,
                canonicalField = PatternCanonicalField.TRANSACTION_TIME,
                placeholderToken = "TIME",
                sourceLabel = "on",
                valueType = PatternValueType.TIME,
            ),
        )
        return MessagePattern(def, fields)
    }

    @Test
    fun templateExtractsAmountNotDueOrBalance() {
        val pattern = approvedPattern()
        val outcome = TemplateResolutionService.resolve("AlRajhi", posBody, null, listOf(pattern))
        val resolved = outcome as TemplateResolutionResult.Matched
        assertEquals(0, BigDecimal("202.50").compareTo(resolved.parsed.amount))
        assertNotEquals(0, BigDecimal("13549.5").compareTo(resolved.parsed.amount!!))
        assertNotEquals(0, BigDecimal("5035.61").compareTo(resolved.parsed.amount!!))
        assertEquals("202.50", resolved.extractedValues["AMOUNT"])
        assertEquals("13549.5", resolved.extractedValues["TOTAL_DUE"])
        assertEquals("5035.61", resolved.extractedValues["AVAILABLE_BALANCE"])
    }

    @Test
    fun templateIdentifierIsCreditCardLast4Only() {
        val pattern = approvedPattern()
        val resolved = TemplateResolutionService.resolve("AlRajhi", posBody, null, listOf(pattern))
            as TemplateResolutionResult.Matched
        assertEquals("3478", resolved.parsed.accountOrCardLastFourDigits)
        assertTrue(
            resolved.parsed.identifierEvidence.any {
                it.type == AccountIdentifierType.CREDIT_CARD_LAST4 && it.lastFour == "3478"
            },
        )
        // Due/balance digits must not appear as identifiers.
        assertTrue(resolved.parsed.identifierEvidence.none { it.lastFour == "5035" })
        assertTrue(resolved.parsed.identifierEvidence.none { it.lastFour == "1354" })
    }

    @Test
    fun templateTypeIsNotReclassifiedByGenericHeuristics() {
        val pattern = approvedPattern(type = TransactionType.PURCHASE.name)
        val resolved = TemplateResolutionService.resolve("AlRajhi", posBody, null, listOf(pattern))
            as TemplateResolutionResult.Matched
        assertEquals(TransactionType.PURCHASE, resolved.parsed.transactionType)
        assertTrue(resolved.parsed.matchedRules.any { it.startsWith("approved_template:") })
        assertEquals("Template:1", resolved.parsed.parserName)
    }

    @Test
    fun walletVariantsShareCanonicalTemplate() {
        val pattern = approvedPattern()
        val google = posBody.replace("Apple Pay", "Google Pay").replace("3478", "1111")
        val samsung = posBody.replace("Apple Pay", "Samsung Pay").replace("3478", "2222")
        val a = TemplateResolutionService.resolve("AlRajhi", google, null, listOf(pattern))
        val b = TemplateResolutionService.resolve("AlRajhi", samsung, null, listOf(pattern))
        assertTrue(a is TemplateResolutionResult.Matched)
        assertTrue(b is TemplateResolutionResult.Matched)
        assertEquals(
            (a as TemplateResolutionResult.Matched).pattern.definition.id,
            (b as TemplateResolutionResult.Matched).pattern.definition.id,
        )
    }

    @Test
    fun optionalDueAbsentStillMatchesRichestTemplate() {
        val bodyNoDue = """
            POS Purchase (Apple Pay)
            Credit Card: 3478
            at: MAREA DHAIATNA Co
            of: 202.50 SAR
            on: 2026-07-26 16:43
            Available Balance: 5035.61 SAR
        """.trimIndent()
        val resolved = TemplateResolutionService.resolve(
            "AlRajhi",
            bodyNoDue,
            null,
            listOf(approvedPattern()),
        ) as TemplateResolutionResult.Matched
        assertEquals(0, BigDecimal("202.50").compareTo(resolved.parsed.amount))
        assertFalse(resolved.extractedValues.containsKey("TOTAL_DUE"))
    }

    @Test
    fun noMatchingTemplateGoesToUnmatched() {
        val body = """
            حوالة صادرة
            من حساب: 3001
            مبلغ: 100 SAR
        """.trimIndent()
        val outcome = TemplateResolutionService.resolve(
            "AlRajhi",
            body,
            null,
            listOf(approvedPattern()),
        )
        assertEquals(TemplateResolutionResult.Unmatched(), outcome)
    }

    @Test
    fun inactiveAndDeprecatedTemplatesNeverImport_butApprovalAppliesToHistoricalSms() {
        val base = approvedPattern()
        val inactive = base.copy(definition = base.definition.copy(isActive = false))
        val deprecated = base.copy(
            definition = base.definition.copy(
                status = MessagePatternStatus.DEPRECATED,
                isActive = true,
            ),
        )
        val future = base.copy(definition = base.definition.copy(activeFrom = 2_000L))

        listOf(inactive, deprecated).forEach { pattern ->
            val outcome = TemplateResolutionService.resolve("AlRajhi", posBody, 1_000L, listOf(pattern))
            assertEquals(
                TemplateResolutionResult.Unmatched(
                    TemplateResolutionResult.Unmatched.Reason.NO_APPROVED_TEMPLATES,
                ),
                outcome,
            )
        }
        // activeFrom is approval metadata, not the SMS event date. A template
        // approved today must process historical inbox messages imported today.
        assertTrue(
            TemplateResolutionService.resolve("AlRajhi", posBody, 1_000L, listOf(future)) is
                TemplateResolutionResult.Matched,
        )
    }

    @Test
    fun signatureOnlyRowsMatchBySignatureLookup() {
        // Legacy signature-only rows (no templateText) are matched by
        // signature equality with the runtime canonical signature. Field
        // extraction needs a template; the signature hit returns the matched
        // pattern so the import flow can route the message through account
        // matching and review instead of dropping it.
        val signatureOnly = approvedPattern().copy(
            definition = approvedPattern().definition.copy(
                templateText = null,
                normalizedSignature = SmsStructureNormalizer.signatureFromBody(posBody),
            ),
        )
        val outcome = TemplateResolutionService.resolve("AlRajhi", posBody, 1L, listOf(signatureOnly))
        assertTrue(outcome is TemplateResolutionResult.Matched)
    }

    @Test
    fun invalidTemplateTypeIsExplicit() {
        val invalid = approvedPattern().copy(
            definition = approvedPattern().definition.copy(transactionType = "LEGACY_UNKNOWN"),
        )
        assertEquals(
            TemplateResolutionResult.Unmatched(TemplateResolutionResult.Unmatched.Reason.INVALID_TEMPLATE),
            TemplateResolutionService.resolve("AlRajhi", posBody, 1L, listOf(invalid)),
        )
    }

    @Test
    fun semanticTypeSelectsPurchaseInsteadOfConflictingStrictTemplate() {
        val purchase = approvedPattern(
            id = 1,
            canonicalKey = "TYPE:POS_PURCHASE|CREDIT_CARD_LAST4|MERCHANT|AMOUNT|DATETIME",
        )
        // A second approved template that also matches the same body structure
        // but has a different family key (simulates conflicting saved rows).
        val other = approvedPattern(
            id = 2,
            template = posTemplate,
            type = TransactionType.TRANSFER_OUT.name,
            canonicalKey = "TYPE:TRANSFER_OUT|CREDIT_CARD_LAST4|MERCHANT|AMOUNT|DATETIME",
        )
        val outcome = TemplateResolutionService.resolve("AlRajhi", posBody, null, listOf(purchase, other))
        assertTrue(outcome is TemplateResolutionResult.Matched)
        outcome as TemplateResolutionResult.Matched
        assertEquals(TransactionType.PURCHASE, outcome.parsed.transactionType)
        assertEquals(PatternMatchTier.SEMANTIC_SCHEMA, outcome.matchTier)
    }

    @Test
    fun equallyMatchingVariantsAreAmbiguous() {
        val a = approvedPattern(id = 1, canonicalKey = "VARIANT-A")
        val b = approvedPattern(id = 2, canonicalKey = "VARIANT-B", template = posTemplate)
        val outcome = TemplateResolutionService.resolve("AlRajhi", posBody, null, listOf(a, b))
        assertTrue(outcome is TemplateResolutionResult.Ambiguous)
    }

    @Test
    fun merchantComesFromTemplatePlaceholder() {
        val resolved = TemplateResolutionService.resolve(
            "AlRajhi",
            posBody,
            null,
            listOf(approvedPattern()),
        ) as TemplateResolutionResult.Matched
        assertEquals("MAREA DHAIATNA Co", resolved.parsed.merchant)
    }

    @Test
    fun matcherCapturesPlaceholdersDeterministically() {
        val m = TemplateMatcher.match(posTemplate, posBody)
        assertTrue(m.matched)
        assertEquals("202.50", m.values["AMOUNT"])
        assertEquals("3478", m.values["CREDIT_CARD_LAST4"])
        assertEquals("MAREA DHAIATNA Co", m.values["MERCHANT"])
        assertEquals("13549.5", m.values["TOTAL_DUE"])
        assertEquals("5035.61", m.values["AVAILABLE_BALANCE"])
    }

    @Test
    fun changingOnlyCardMerchantAmountAndDateAlwaysMatchesSameTemplate() {
        val messages = listOf(
            """
                POS Purchase
                Credit Card: 3478
                at: Store A
                of: 10.00 SAR
                on: 2026-07-26 16:43
            """.trimIndent(),
            """
                POS Purchase
                Credit Card: 7271
                at: Store B
                of: 999.50 SAR
                on: 2026-07-27 09:01
            """.trimIndent(),
            """
                POS Purchase
                Credit Card: 1234
                at: Another Merchant 24
                of: 7 SAR
                on: 28-07-2026 20:00
            """.trimIndent(),
        )
        val template = posTemplate
            .lineSequence()
            .filterNot {
                it.startsWith("Available Balance") || it.startsWith("Due Amount")
            }
            .joinToString("\n")
        messages.forEach { sms ->
            val match = TemplateMatcher.match(template, sms)
            assertTrue("expected structural match, got ${match.failureReason}: ${match.trace}", match.matched)
        }
    }

    @Test
    fun wrongAtmStructureDoesNotMatchPurchaseTemplate() {
        val sms = """
            ATM Withdrawal
            Debit Card: 1234
            Amount: 100 SAR
            at: ATM 22
        """.trimIndent()
        val match = TemplateMatcher.match(posTemplate, sms)
        assertFalse(match.matched)
        assertEquals(TemplateMatcher.FailureReason.STATIC_TEXT_MISMATCH, match.failureReason)
    }

    @Test
    fun matcherNormalizesWhitespaceColonCaseAndArabicDigitsInVariables() {
        val sms = """
            pos purchase (Google Pay)
            Credit Card ： ٣٤٧٨
            at : MAREA DHAIATNA Co
            of: ٢٠٢٫٥٠ sar
            on : ٢٠٢٦-٠٧-٢٦ ١٦:٤٣
            Available Balance : ٥٠٣٥٫٦١ SAR
        """.trimIndent()
        val match = TemplateMatcher.match(posTemplate, sms)
        assertTrue("failure=${match.failureReason} trace=${match.trace}", match.matched)
        assertEquals("3478", match.values["CREDIT_CARD_LAST4"])
        assertEquals("202.50", match.values["AMOUNT"])
    }

    @Test
    fun diagnosticsExplainExactRejectedComparison() {
        val wrong = posBody.replace("Credit Card:", "Account:")
        val diagnostics = TemplateResolutionService.diagnose(wrong, listOf(approvedPattern()))
        val attempt = diagnostics.attempts.single()
        assertTrue(attempt.eligible)
        val match = requireNotNull(attempt.match)
        assertFalse(match.matched)
        assertEquals(TemplateMatcher.FailureReason.LABEL_MISMATCH, match.failureReason)
        assertEquals("Credit Card: {CREDIT_CARD_LAST4}", match.failedTemplateLine)
        assertEquals("Account: 3478", match.failedBodyLine)
    }
}

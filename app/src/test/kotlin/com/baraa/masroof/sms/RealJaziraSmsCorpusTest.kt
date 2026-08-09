package com.baraa.masroof.sms

import com.baraa.masroof.data.db.PatternCanonicalField
import com.baraa.masroof.transaction.MoneyFlowDirection
import com.baraa.masroof.transaction.TransactionType
import com.baraa.masroof.transaction.TransactionTypeTaxonomy
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/**
 * Phase 3 — Corpus analysis test (BASELINE).
 *
 * Runs the PRODUCTION analysis path on every Jazira corpus case and asserts
 * the canonical contract: financial, transaction type, direction, amount,
 * currency, identifiers. Failures print `case=… stage=… expected=… actual=…`
 * so the failing layer is visible, not hidden behind a generic assertion.
 *
 * This is a stabilization baseline: failures are EXPECTED and recorded in the
 * Phase 8 matrix. Do NOT fix until the matrix is known.
 */
class RealJaziraSmsCorpusTest {

    private data class CorpusAnalysis(
        val financial: Boolean,
        val transactionType: TransactionType?,
        val direction: MoneyFlowDirection,
        val amount: BigDecimal?,
        val currency: String?,
        val sourceAccountLast4: String?,
        val destinationAccountLast4: String?,
        val creditCardLast4: String?,
        val debitCardLast4: String?,
        val ibanLast4: String?,
        val sourceIbanLast4: String?,
        val destinationIbanLast4: String?,
        val semanticKey: String?,
    )

    private fun analyze(body: String): CorpusAnalysis {
        val cue = MessageTypeCueCatalog.detect(body)
        val built = MessageTemplateEngine.buildFromSms(body)
        val otp = SmsStructureNormalizer.looksLikeOtpOrMarketing(body)
        val nonFinancial = cue.transactionType == TransactionType.NON_FINANCIAL ||
            MessageTypeCueCatalog.isNonFinancialCue(body)
        val financial = body.isNotBlank() && !otp && !nonFinancial
        val type = built.transactionType ?: cue.transactionType
        val direction = TransactionTypeTaxonomy.parseDirection(built.direction ?: cue.direction, type)
        val fields = CanonicalSmsFieldExtractor.extract(body)
        val values = fields.values
        val semantic = SemanticPatternSchemaNormalizer.fromBody(body)
        val semanticKey = (semantic as? SemanticSchemaResult.Safe)?.key
        return CorpusAnalysis(
            financial = financial,
            transactionType = type,
            direction = direction,
            amount = fields.amount,
            currency = fields.currency?.name,
            sourceAccountLast4 = values[PatternCanonicalField.SOURCE_ACCOUNT_LAST4],
            destinationAccountLast4 = values[PatternCanonicalField.DESTINATION_ACCOUNT_LAST4],
            creditCardLast4 = values[PatternCanonicalField.CREDIT_CARD_LAST4],
            debitCardLast4 = values[PatternCanonicalField.DEBIT_CARD_LAST4],
            ibanLast4 = values[PatternCanonicalField.IBAN_LAST4],
            sourceIbanLast4 = values[PatternCanonicalField.SOURCE_IBAN_LAST4],
            destinationIbanLast4 = values[PatternCanonicalField.DESTINATION_IBAN_LAST4],
            semanticKey = semanticKey,
        )
    }

    private fun assertCase(id: String) {
        val case = RealJaziraCorpus.load().first { it.id == id }
        val a = analyze(case.body)
        val e = case.expected
        assertEquals("$id stage=financial expected=${e.financial} actual=${a.financial}", e.financial, a.financial)
        assertEquals(
            "$id stage=classification expected=${e.transactionType} actual=${a.transactionType?.name}",
            e.transactionType, a.transactionType?.name,
        )
        assertEquals(
            "$id stage=direction expected=${e.direction} actual=${a.direction.name}",
            e.direction, a.direction.name,
        )
        val expectedAmount = BigDecimal(e.amount)
        if (a.amount == null || a.amount.compareTo(expectedAmount) != 0) {
            fail("$id stage=amount expected=${e.amount} actual=${a.amount?.toPlainString()}")
        }
        assertEquals("$id stage=currency expected=${e.currency} actual=${a.currency}", e.currency, a.currency)
        e.sourceAccountLast4?.let {
            assertEquals("$id stage=identifier_sourceAccount expected=$it actual=${a.sourceAccountLast4}", it, a.sourceAccountLast4)
        }
        e.destinationAccountLast4?.let {
            assertEquals("$id stage=identifier_destinationAccount expected=$it actual=${a.destinationAccountLast4}", it, a.destinationAccountLast4)
        }
        e.creditCardLast4?.let {
            assertEquals("$id stage=identifier_creditCard expected=$it actual=${a.creditCardLast4}", it, a.creditCardLast4)
        }
        e.debitCardLast4?.let {
            assertEquals("$id stage=identifier_debitCard expected=$it actual=${a.debitCardLast4}", it, a.debitCardLast4)
        }
        e.ibanLast4?.let {
            assertEquals("$id stage=identifier_iban expected=$it actual=${a.ibanLast4}", it, a.ibanLast4)
        }
        e.sourceIbanLast4?.let {
            assertEquals("$id stage=identifier_sourceIban expected=$it actual=${a.sourceIbanLast4}", it, a.sourceIbanLast4)
        }
        e.destinationIbanLast4?.let {
            assertEquals("$id stage=identifier_destinationIban expected=$it actual=${a.destinationIbanLast4}", it, a.destinationIbanLast4)
        }
    }

    @Test fun case1_internal_outgoing_transfer() = assertCase("case1_internal_outgoing_transfer")
    @Test fun case2_internal_incoming_transfer() = assertCase("case2_internal_incoming_transfer")
    @Test fun case3_local_incoming_transfer() = assertCase("case3_local_incoming_transfer")
    @Test fun case4_english_inline_online_credit_card_purchase() = assertCase("case4_english_inline_online_credit_card_purchase")
    @Test fun case5_english_inline_apple_pay_online_purchase() = assertCase("case5_english_inline_apple_pay_online_purchase")
    @Test fun case6_outgoing_external_transfer() = assertCase("case6_outgoing_external_transfer")
    @Test fun case7_pos_samsung_pay_credit_card_purchase() = assertCase("case7_pos_samsung_pay_credit_card_purchase")
    @Test fun case8_outgoing_external_transfer_variant() = assertCase("case8_outgoing_external_transfer_variant")
    @Test fun case9_salary() = assertCase("case9_salary")
    @Test fun case10_financing_installment() = assertCase("case10_financing_installment")
}
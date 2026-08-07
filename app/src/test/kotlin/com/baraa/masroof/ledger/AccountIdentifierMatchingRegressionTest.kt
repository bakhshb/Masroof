package com.baraa.masroof.ledger

import com.baraa.masroof.data.db.AccountIdentifierType
import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.rules.AccountIdentifierSnapshot
import com.baraa.masroof.rules.AccountMatching
import com.baraa.masroof.rules.RuleContext
import com.baraa.masroof.rules.RuleInput
import com.baraa.masroof.rules.rules.InternalTransferRule
import com.baraa.masroof.transaction.AccountNature
import com.baraa.masroof.transaction.AccountType
import com.baraa.masroof.transaction.Currency
import com.baraa.masroof.transaction.FinancialTreatment
import com.baraa.masroof.transaction.IdentifierRole
import com.baraa.masroof.transaction.ParsedIdentifierEvidence
import com.baraa.masroof.transaction.ParsedTransaction
import com.baraa.masroof.transaction.TransactionStatus
import com.baraa.masroof.transaction.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class AccountIdentifierMatchingRegressionTest {

    private fun account(
        id: Long,
        name: String,
        type: AccountType = AccountType.BANK_ACCOUNT,
        institution: String? = "Bank",
    ) = FinancialAccount(
        id = id,
        displayName = name,
        institutionName = institution,
        accountType = type,
        accountNature = AccountNature.defaultNatureFor(type),
        currency = Currency.SAR,
        openingBalance = BigDecimal.ZERO,
        openingBalanceDate = 0L,
        includeInNetWorth = true,
        includeInLiquidity = true,
        isOwnedByUser = true,
        systemAccountKey = null,
        isActive = true,
        notes = null,
    )

    @Test
    fun matchByNameReturnsNullWhenMultipleAccountsShareNameTokens() {
        val a = account(1, "Checking Al Rajhi")
        val b = account(2, "Savings Al Rajhi")
        assertNull(AccountMatching.matchByName("transfer Al Rajhi", null, listOf(a, b)))
    }

    @Test
    fun matchByNameReturnsSingleCompatibleAccount() {
        val a = account(1, "My Checking Card")
        val b = account(2, "Savings")
        assertEquals(a, AccountMatching.matchByName("تحويل إلى حساب My Checking Card", null, listOf(a, b)))
    }

    @Test
    fun internalTransferMatchesDestinationByTypedEvidenceValue() {
        val source = account(1, "Source")
        val destination = account(2, "Dest")
        val decoy = account(3, "Decoy", institution = "Bank")
        val evidence = listOf(
            ParsedIdentifierEvidence(
                AccountIdentifierType.ACCOUNT_LAST4,
                "2222",
                IdentifierRole.DESTINATION,
                90,
                "label:ACCOUNT_LAST4"
            )
        )
        val parsed = ParsedTransaction(
            originalSender = "bank",
            originalMessage = null,
            transactionType = TransactionType.TRANSFER_OUT,
            amount = BigDecimal.TEN,
            currency = Currency.SAR,
            merchant = null,
            accountOrCardLastFourDigits = "2222",
            transactionDate = null,
            transactionTime = null,
            status = TransactionStatus.COMPLETED,
            confidence = 80,
            parsingNotes = emptyList(),
            identifierEvidence = evidence
        )
        val input = RuleInput(
            sender = "bank",
            body = "حوالة إلى حساب ****2222",
            amount = BigDecimal.TEN,
            currency = Currency.SAR,
            type = TransactionType.TRANSFER_OUT,
            status = TransactionStatus.COMPLETED,
            date = null,
            time = null,
            normalizedMerchantKey = null,
            parsed = parsed
        )
        val context = RuleContext(
            ownedAccounts = listOf(source, destination, decoy),
            merchantMemories = emptyList(),
            categories = emptyList(),
            accountIdentifiers = listOf(
                AccountIdentifierSnapshot(1, AccountIdentifierType.ACCOUNT_LAST4, "1111"),
                AccountIdentifierSnapshot(2, AccountIdentifierType.ACCOUNT_LAST4, "2222"),
                AccountIdentifierSnapshot(3, AccountIdentifierType.ACCOUNT_LAST4, "9999"),
            ),
            accountsBySenderKey = mapOf("bank" to setOf(1L)),
        )
        val result = InternalTransferRule().evaluate(input, context)
        assertEquals(FinancialTreatment.INTERNAL_TRANSFER, result?.financialTreatment)
        assertTrue(result!!.reason.contains("Dest"))
    }

    @Test
    fun internalTransferDoesNotPickFirstCompatibleTypeIgnoringValue() {
        val source = account(1, "Source")
        val wrong = account(2, "Wrong", institution = "Bank")
        val evidence = listOf(
            ParsedIdentifierEvidence(
                AccountIdentifierType.ACCOUNT_LAST4,
                "2222",
                IdentifierRole.DESTINATION,
                90,
                "label:ACCOUNT_LAST4"
            )
        )
        val parsed = ParsedTransaction(
            originalSender = "bank",
            originalMessage = null,
            transactionType = TransactionType.TRANSFER_OUT,
            amount = BigDecimal.TEN,
            currency = Currency.SAR,
            merchant = null,
            accountOrCardLastFourDigits = "2222",
            transactionDate = null,
            transactionTime = null,
            status = TransactionStatus.COMPLETED,
            confidence = 80,
            parsingNotes = emptyList(),
            identifierEvidence = evidence
        )
        val input = RuleInput(
            sender = "bank",
            body = "حوالة",
            amount = BigDecimal.TEN,
            currency = Currency.SAR,
            type = TransactionType.TRANSFER_OUT,
            status = TransactionStatus.COMPLETED,
            date = null,
            time = null,
            normalizedMerchantKey = null,
            parsed = parsed
        )
        val context = RuleContext(
            ownedAccounts = listOf(source, wrong),
            merchantMemories = emptyList(),
            categories = emptyList(),
            accountIdentifiers = listOf(
                AccountIdentifierSnapshot(2, AccountIdentifierType.ACCOUNT_LAST4, "9999"),
                AccountIdentifierSnapshot(1, AccountIdentifierType.ACCOUNT_LAST4, "9999")
            )
        )
        assertNull(InternalTransferRule().evaluate(input, context))
    }

    @Test
    fun discoveredIdentifierProposerUsesCompatibleEvidence() {
        val account = account(1, "Card", type = AccountType.CREDIT_CARD)
        val tx = TransactionEntity(
            id = 1,
            uniqueFingerprint = "x",
            smsTimestamp = 1,
            originalSender = "bank",
            transactionType = TransactionType.PURCHASE,
            amount = BigDecimal.ONE,
            currency = Currency.SAR,
            merchantOrBeneficiary = null,
            accountOrCardLastFourDigits = "7271",
            transactionDate = null,
            transactionTime = null,
            status = TransactionStatus.COMPLETED,
            confidence = 90,
            parsingNotes = emptyList(),
            dateSource = com.baraa.masroof.data.db.DateSource.FROM_BODY,
            createdAt = 1,
            updatedAt = 1
        )
        val evidence = listOf(
            ParsedIdentifierEvidence(
                AccountIdentifierType.CREDIT_CARD_LAST4,
                "7271",
                IdentifierRole.SOURCE,
                90,
                "label"
            )
        )
        val candidate = DiscoveredIdentifierProposer.propose(tx, account, evidence)
        assertEquals(AccountIdentifierType.CREDIT_CARD_LAST4, candidate?.identifierType)
        assertEquals("7271", candidate?.normalizedLastFour)
    }

    @Test
    fun lastFourFromValueRejectsBareAmountDigits() {
        assertNull(com.baraa.masroof.transaction.LineBasedFieldParser.lastFourFromValue("150.00"))
        assertNull(com.baraa.masroof.transaction.LineBasedFieldParser.lastFourFromValue("REF20241201"))
        assertEquals("7271", com.baraa.masroof.transaction.LineBasedFieldParser.lastFourFromValue("****7271"))
        assertEquals("7271", com.baraa.masroof.transaction.LineBasedFieldParser.lastFourFromValue("7271"))
    }
}

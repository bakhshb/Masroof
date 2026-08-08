package com.baraa.masroof.ledger

import com.baraa.masroof.data.db.AccountIdentifierType
import com.baraa.masroof.data.db.DateSource
import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.transaction.AccountNature
import com.baraa.masroof.transaction.AccountType
import com.baraa.masroof.transaction.Currency
import com.baraa.masroof.transaction.FinancialTreatment
import com.baraa.masroof.transaction.IdentifierRole
import com.baraa.masroof.transaction.ParsedIdentifierEvidence
import com.baraa.masroof.transaction.TransactionStatus
import com.baraa.masroof.transaction.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class DiscoveredIdentifierProposerTest {

    private fun bank() = FinancialAccount(
        id = 1,
        displayName = "راتب",
        institutionName = "أهلي",
        accountType = AccountType.BANK_ACCOUNT,
        accountNature = AccountNature.ASSET,
        currency = Currency.SAR,
        openingBalance = BigDecimal.ZERO,
        openingBalanceDate = 0,
        includeInNetWorth = true,
        includeInLiquidity = true,
        isOwnedByUser = true,
        isActive = true,
        notes = null,
    )

    private fun tx(last4: String? = "3001") = TransactionEntity(
        id = 1,
        uniqueFingerprint = "fp",
        smsTimestamp = 1,
        originalSender = "bank",
        transactionType = TransactionType.TRANSFER_OUT,
        amount = BigDecimal("1789"),
        currency = Currency.SAR,
        merchantOrBeneficiary = null,
        accountOrCardLastFourDigits = last4,
        transactionDate = null,
        transactionTime = null,
        status = TransactionStatus.COMPLETED,
        confidence = 90,
        parsingNotes = emptyList(),
        dateSource = DateSource.FROM_BODY,
        createdAt = 1,
        updatedAt = 1,
        financialTreatment = FinancialTreatment.EXPENSE,
    )

    @Test
    fun prefersSourceAccountLast4OverDestinationIban() {
        val evidence = listOf(
            ParsedIdentifierEvidence(
                type = AccountIdentifierType.IBAN_LAST4,
                lastFour = "6810",
                role = IdentifierRole.DESTINATION,
                extractionRule = "المعرف البديل الايبان",
                confidence = 95,
            ),
            ParsedIdentifierEvidence(
                type = AccountIdentifierType.ACCOUNT_LAST4,
                lastFour = "3001",
                role = IdentifierRole.SOURCE,
                extractionRule = "خصمت من حساب",
                confidence = 95,
            ),
        )
        val proposed = DiscoveredIdentifierProposer.propose(tx(), bank(), evidence)
        assertNotNull(proposed)
        assertEquals(AccountIdentifierType.ACCOUNT_LAST4, proposed!!.identifierType)
        assertEquals("3001", proposed.normalizedLastFour)
        assertEquals(IdentifierTransactionRole.SOURCE, proposed.transactionRole)
    }

    @Test
    fun fallsBackToTransactionLastFourWhenNoEvidence() {
        val proposed = DiscoveredIdentifierProposer.propose(tx("3001"), bank(), emptyList())
        assertNotNull(proposed)
        assertEquals("3001", proposed!!.normalizedLastFour)
        assertEquals(AccountIdentifierType.ACCOUNT_LAST4, proposed.identifierType)
    }

    @Test
    fun returnsNullWhenNoLastFour() {
        assertNull(DiscoveredIdentifierProposer.propose(tx(null), bank(), emptyList()))
    }
}

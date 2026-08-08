package com.baraa.masroof.transaction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionTypeTaxonomyTest {

    @Test
    fun salaryProducesInflow() {
        assertEquals(MoneyFlowDirection.INFLOW, TransactionTypeTaxonomy.directionOf(TransactionType.SALARY))
    }

    @Test
    fun transferOutProducesOutflow() {
        assertEquals(MoneyFlowDirection.OUTFLOW, TransactionTypeTaxonomy.directionOf(TransactionType.TRANSFER_OUT))
    }

    @Test
    fun transferInProducesInflow() {
        assertEquals(MoneyFlowDirection.INFLOW, TransactionTypeTaxonomy.directionOf(TransactionType.TRANSFER_IN))
    }

    @Test
    fun internalTransferProducesTransfer() {
        assertEquals(
            MoneyFlowDirection.TRANSFER,
            TransactionTypeTaxonomy.directionOf(TransactionType.INTERNAL_TRANSFER),
        )
    }

    @Test
    fun nonFinancialProducesNone() {
        assertEquals(MoneyFlowDirection.NONE, TransactionTypeTaxonomy.directionOf(TransactionType.NON_FINANCIAL))
    }

    @Test
    fun feeProducesOutflow() {
        assertEquals(MoneyFlowDirection.OUTFLOW, TransactionTypeTaxonomy.directionOf(TransactionType.FEE))
    }

    @Test
    fun purchaseAndOnlinePurchaseSharePurchasesFamily() {
        assertEquals(
            TransactionTypeFamily.PURCHASES,
            TransactionTypeTaxonomy.familyOf(TransactionType.PURCHASE),
        )
        assertEquals(
            TransactionTypeFamily.PURCHASES,
            TransactionTypeTaxonomy.familyOf(TransactionType.ONLINE_PURCHASE),
        )
    }

    @Test
    fun allTransferTypesShareTransfersFamily() {
        assertEquals(TransactionTypeFamily.TRANSFERS, TransactionTypeTaxonomy.familyOf(TransactionType.TRANSFER_OUT))
        assertEquals(TransactionTypeFamily.TRANSFERS, TransactionTypeTaxonomy.familyOf(TransactionType.TRANSFER_IN))
        assertEquals(TransactionTypeFamily.TRANSFERS, TransactionTypeTaxonomy.familyOf(TransactionType.INTERNAL_TRANSFER))
    }

    @Test
    fun financialRequiresAmountExceptNonFinancial() {
        assertTrue(TransactionTypeTaxonomy.requiresAmount(TransactionType.PURCHASE))
        assertTrue(TransactionTypeTaxonomy.requiresAmount(TransactionType.SALARY))
        assertFalse(TransactionTypeTaxonomy.requiresAmount(TransactionType.NON_FINANCIAL))
    }

    @Test
    fun parseDirectionReadsCanonicalStrings() {
        assertEquals(MoneyFlowDirection.INFLOW, TransactionTypeTaxonomy.parseDirection("INFLOW"))
        assertEquals(MoneyFlowDirection.OUTFLOW, TransactionTypeTaxonomy.parseDirection("OUTFLOW"))
        assertEquals(MoneyFlowDirection.TRANSFER, TransactionTypeTaxonomy.parseDirection("TRANSFER"))
    }

    @Test
    fun discoveryConfidenceIncreasesWithOccurrences() {
        assertTrue(TransactionTypeTaxonomy.discoveryConfidence(1) < TransactionTypeTaxonomy.discoveryConfidence(2))
        assertTrue(TransactionTypeTaxonomy.discoveryConfidence(2) < TransactionTypeTaxonomy.discoveryConfidence(5))
    }
}

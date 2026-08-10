package com.baraa.masroof.domain.rules

import com.baraa.masroof.domain.model.Account
import com.baraa.masroof.domain.model.AccountType
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.BankNetworkType
import com.baraa.masroof.domain.model.Card
import com.baraa.masroof.domain.model.CardType
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.model.PurchaseChannel
import com.baraa.masroof.domain.model.TransferOwnershipType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferOwnershipResolverTest {

    @Test
    fun ownedToOwned_isSelfTransfer() {
        assertEquals(
            TransferOwnershipType.SELF_TRANSFER,
            TransferOwnershipResolver.resolve(OwnershipStatus.OWNED, OwnershipStatus.OWNED),
        )
    }

    @Test
    fun externalToOwned_isExternalIncoming() {
        assertEquals(
            TransferOwnershipType.EXTERNAL_INCOMING,
            TransferOwnershipResolver.resolve(OwnershipStatus.EXTERNAL, OwnershipStatus.OWNED),
        )
    }

    @Test
    fun ownedToExternal_isExternalOutgoing() {
        assertEquals(
            TransferOwnershipType.EXTERNAL_OUTGOING,
            TransferOwnershipResolver.resolve(OwnershipStatus.OWNED, OwnershipStatus.EXTERNAL),
        )
    }

    @Test
    fun unknownOwnership_isUnknown_noGuess() {
        assertEquals(
            TransferOwnershipType.UNKNOWN,
            TransferOwnershipResolver.resolve(OwnershipStatus.OWNED, OwnershipStatus.UNKNOWN),
        )
        assertEquals(
            TransferOwnershipType.UNKNOWN,
            TransferOwnershipResolver.resolve(OwnershipStatus.UNKNOWN, OwnershipStatus.OWNED),
        )
        assertEquals(
            TransferOwnershipType.UNKNOWN,
            TransferOwnershipResolver.resolve(OwnershipStatus.UNKNOWN, OwnershipStatus.UNKNOWN),
        )
    }
}

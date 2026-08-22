package com.baraa.masroof.data.repository

import com.baraa.masroof.data.room.dao.CardRegistryDao
import com.baraa.masroof.data.room.entity.CardRegistryEntity
import com.baraa.masroof.data.room.mapper.RegistryMapper
import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.CardNetwork
import com.baraa.masroof.domain.model.CardReference
import com.baraa.masroof.domain.model.CardRegistryEntry
import com.baraa.masroof.domain.model.CardRole
import com.baraa.masroof.domain.model.CardType
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.ownership.RegistryIdentity
import com.baraa.masroof.domain.repository.CardRegistryRepository

class RoomCardRegistryRepository(
    private val dao: CardRegistryDao,
) : CardRegistryRepository {
    override suspend fun observe(reference: CardReference, rawSmsId: String) {
        if (!RegistryIdentity.isKnownBank(reference.bank)) return
        val last4 = reference.last4?.trim().orEmpty()
        if (last4.isEmpty()) return

        dao.observeAtomic(
            entity = CardRegistryEntity(
                bankId = reference.bank.id,
                last4 = last4,
                ownershipStatus = OwnershipStatus.UNKNOWN.name,
                firstSeenRawSmsId = rawSmsId,
                lastSeenRawSmsId = rawSmsId,
            ),
            rawSmsId = rawSmsId,
        )
    }

    override suspend fun setOwnership(reference: CardReference, status: OwnershipStatus) {
        RegistryIdentity.requireKnownBank(reference.bank, "CardRegistry.setOwnership")
        val last4 = reference.last4?.trim().orEmpty()
        require(last4.isNotEmpty()) { "last4 required to set ownership" }

        dao.setOwnershipAtomic(
            entity = CardRegistryEntity(
                bankId = reference.bank.id,
                last4 = last4,
                ownershipStatus = status.name,
                firstSeenRawSmsId = null,
                lastSeenRawSmsId = null,
            ),
            ownershipStatus = status.name,
        )
    }

    override suspend fun resolve(reference: CardReference): OwnershipStatus {
        if (!RegistryIdentity.isKnownBank(reference.bank)) return OwnershipStatus.UNKNOWN
        val last4 = reference.last4?.trim().orEmpty()
        if (last4.isEmpty()) return OwnershipStatus.UNKNOWN
        val entry = dao.get(reference.bank.id, last4) ?: return OwnershipStatus.UNKNOWN
        return OwnershipStatus.valueOf(entry.ownershipStatus)
    }

    override suspend fun get(reference: CardReference): CardRegistryEntry? {
        if (!RegistryIdentity.isKnownBank(reference.bank)) return null
        val last4 = reference.last4?.trim().orEmpty()
        if (last4.isEmpty()) return null
        return dao.get(reference.bank.id, last4)?.let(RegistryMapper::toCardEntry)
    }

    override suspend fun listAll(): List<CardRegistryEntry> =
        dao.listAll().map(RegistryMapper::toCardEntry)

    override suspend fun updateDisplayName(reference: CardReference, displayName: String?) {
        val last4 = requireLast4(reference)
        dao.updateDisplayName(reference.bank.id, last4, displayName?.trim()?.ifEmpty { null })
    }

    override suspend fun updateCardNetwork(reference: CardReference, network: CardNetwork?) {
        val last4 = requireLast4(reference)
        dao.updateCardNetwork(reference.bank.id, last4, network?.name)
    }

    override suspend fun updateCardType(reference: CardReference, cardType: CardType?) {
        val last4 = requireLast4(reference)
        dao.updateCardType(reference.bank.id, last4, cardType?.name)
    }

    override suspend fun linkDebitToAccount(card: CardReference, account: AccountReference) {
        val last4 = requireLast4(card)
        RegistryIdentity.requireKnownBank(account.bank, "CardRegistry.linkDebitToAccount")
        dao.updateLinkedAccount(
            bankId = card.bank.id,
            last4 = last4,
            linkedAccountBankId = account.bank.id,
            linkedAccountMaskedNumber = account.maskedNumber,
        )
        dao.updateCardType(card.bank.id, last4, CardType.DEBIT.name)
        if (dao.get(card.bank.id, last4)?.cardNetwork == null) {
            dao.updateCardNetwork(card.bank.id, last4, CardNetwork.MADA.name)
        }
    }

    override suspend fun setPrimaryCard(reference: CardReference) {
        val last4 = requireLast4(reference)
        dao.updateCardRole(
            bankId = reference.bank.id,
            last4 = last4,
            cardRole = CardRole.PRIMARY.name,
            parentCardLast4 = null,
        )
        dao.updateCardType(reference.bank.id, last4, CardType.CREDIT.name)
    }

    override suspend fun setSupplementaryCard(reference: CardReference, primaryLast4: String) {
        val last4 = requireLast4(reference)
        dao.updateCardRole(
            bankId = reference.bank.id,
            last4 = last4,
            cardRole = CardRole.SUPPLEMENTARY.name,
            parentCardLast4 = primaryLast4.trim(),
        )
        dao.updateCardType(reference.bank.id, last4, CardType.CREDIT.name)
    }

    override suspend fun clearCardRole(reference: CardReference) {
        val last4 = requireLast4(reference)
        dao.clearSupplementaryRole(reference.bank.id, last4)
        dao.updateCardRole(
            bankId = reference.bank.id,
            last4 = last4,
            cardRole = CardRole.STANDALONE.name,
            parentCardLast4 = null,
        )
    }

    private fun requireLast4(reference: CardReference): String {
        RegistryIdentity.requireKnownBank(reference.bank, "CardRegistry")
        val last4 = reference.last4?.trim().orEmpty()
        require(last4.isNotEmpty()) { "last4 required" }
        return last4
    }
}

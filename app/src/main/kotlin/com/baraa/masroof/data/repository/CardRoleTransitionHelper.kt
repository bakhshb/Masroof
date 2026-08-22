package com.baraa.masroof.data.repository

import com.baraa.masroof.data.room.dao.CardRegistryDao
import com.baraa.masroof.domain.model.CardRole

/**
 * Centralizes credit-facility role transitions so supplementaries are never orphaned.
 */
internal class CardRoleTransitionHelper(
    private val dao: CardRegistryDao,
) {
    suspend fun detachChildrenOfPrimary(bankId: String, primaryLast4: String) {
        dao.detachSupplementariesOfPrimary(bankId, primaryLast4)
    }

    suspend fun detachChildrenOfOtherPrimaries(bankId: String, exceptLast4: String) {
        dao.listAll()
            .filter {
                it.bankId == bankId &&
                    it.cardRole == CardRole.PRIMARY.name &&
                    it.last4 != exceptLast4
            }
            .forEach { detachChildrenOfPrimary(bankId, it.last4) }
    }

    suspend fun clearFacilityRole(bankId: String, last4: String) {
        val existing = dao.get(bankId, last4)
        if (existing?.cardRole == CardRole.PRIMARY.name) {
            detachChildrenOfPrimary(bankId, last4)
        }
        dao.clearSupplementaryRole(bankId, last4)
        dao.updateCardRole(
            bankId = bankId,
            last4 = last4,
            cardRole = CardRole.STANDALONE.name,
            parentCardLast4 = null,
        )
    }
}

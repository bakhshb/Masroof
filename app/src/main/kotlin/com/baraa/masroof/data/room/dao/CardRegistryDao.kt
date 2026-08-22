package com.baraa.masroof.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.baraa.masroof.data.room.entity.CardRegistryEntity

@Dao
interface CardRegistryDao {
    /** Atomic create-if-absent. Returns -1 when the composite key already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: CardRegistryEntity): Long

    /**
     * Observation metadata only — never touches [CardRegistryEntity.ownershipStatus].
     * Preserves existing [firstSeenRawSmsId] when already set.
     */
    @Query(
        """
        UPDATE card_registry
        SET lastSeenRawSmsId = :rawSmsId,
            firstSeenRawSmsId = COALESCE(firstSeenRawSmsId, :rawSmsId)
        WHERE bankId = :bankId AND last4 = :last4
        """,
    )
    suspend fun touchObservation(bankId: String, last4: String, rawSmsId: String): Int

    @Query(
        """
        UPDATE card_registry
        SET ownershipStatus = :ownershipStatus
        WHERE bankId = :bankId AND last4 = :last4
        """,
    )
    suspend fun updateOwnership(
        bankId: String,
        last4: String,
        ownershipStatus: String,
    ): Int

    /**
     * API-26-safe ownership write: IGNORE-insert then explicit UPDATE in one transaction.
     * Never uses SQLite UPSERT (`ON CONFLICT ... DO UPDATE`).
     */
    @Transaction
    suspend fun setOwnershipAtomic(
        entity: CardRegistryEntity,
        ownershipStatus: String,
    ) {
        insertIfAbsent(entity)
        updateOwnership(
            bankId = entity.bankId,
            last4 = entity.last4,
            ownershipStatus = ownershipStatus,
        )
    }

    /**
     * Observation create-if-absent + metadata touch in one transaction.
     * Does not modify ownershipStatus when the row already exists.
     */
    @Transaction
    suspend fun observeAtomic(
        entity: CardRegistryEntity,
        rawSmsId: String,
    ) {
        insertIfAbsent(entity)
        touchObservation(entity.bankId, entity.last4, rawSmsId)
    }

    @Query(
        """
        SELECT * FROM card_registry
        WHERE bankId = :bankId AND last4 = :last4
        LIMIT 1
        """,
    )
    suspend fun get(bankId: String, last4: String): CardRegistryEntity?

    @Query("SELECT * FROM card_registry ORDER BY bankId, last4")
    suspend fun listAll(): List<CardRegistryEntity>

    @Query(
        """
        UPDATE card_registry
        SET displayName = :displayName
        WHERE bankId = :bankId AND last4 = :last4
        """,
    )
    suspend fun updateDisplayName(bankId: String, last4: String, displayName: String?): Int

    @Query(
        """
        UPDATE card_registry
        SET cardNetwork = :cardNetwork
        WHERE bankId = :bankId AND last4 = :last4
        """,
    )
    suspend fun updateCardNetwork(bankId: String, last4: String, cardNetwork: String?): Int

    @Query(
        """
        UPDATE card_registry
        SET cardType = :cardType
        WHERE bankId = :bankId AND last4 = :last4
        """,
    )
    suspend fun updateCardType(bankId: String, last4: String, cardType: String?): Int

    @Query(
        """
        UPDATE card_registry
        SET linkedAccountBankId = :linkedAccountBankId,
            linkedAccountMaskedNumber = :linkedAccountMaskedNumber
        WHERE bankId = :bankId AND last4 = :last4
        """,
    )
    suspend fun updateLinkedAccount(
        bankId: String,
        last4: String,
        linkedAccountBankId: String?,
        linkedAccountMaskedNumber: String?,
    ): Int

    @Query(
        """
        UPDATE card_registry
        SET cardRole = :cardRole,
            parentCardLast4 = :parentCardLast4
        WHERE bankId = :bankId AND last4 = :last4
        """,
    )
    suspend fun updateCardRole(
        bankId: String,
        last4: String,
        cardRole: String?,
        parentCardLast4: String?,
    ): Int

    @Query(
        """
        UPDATE card_registry
        SET cardRole = 'STANDALONE',
            parentCardLast4 = NULL
        WHERE bankId = :bankId AND parentCardLast4 = :primaryLast4
        """,
    )
    suspend fun detachSupplementariesOfPrimary(bankId: String, primaryLast4: String): Int

    @Query(
        """
        UPDATE card_registry
        SET cardRole = 'STANDALONE',
            parentCardLast4 = NULL
        WHERE bankId = :bankId
          AND cardRole = 'PRIMARY'
          AND last4 != :exceptLast4
        """,
    )
    suspend fun demoteOtherPrimaryCards(bankId: String, exceptLast4: String): Int

    @Query(
        """
        UPDATE card_registry
        SET cardRole = 'STANDALONE',
            parentCardLast4 = NULL
        WHERE bankId = :bankId AND last4 = :last4
        """,
    )
    suspend fun clearSupplementaryRole(bankId: String, last4: String): Int

    @Query(
        """
        SELECT last4 FROM card_registry
        WHERE bankId = :bankId AND cardRole = 'PRIMARY' AND last4 != :exceptLast4
        """,
    )
    suspend fun listOtherPrimaryLast4s(bankId: String, exceptLast4: String): List<String>

    @Transaction
    suspend fun promoteToPrimaryAtomic(bankId: String, last4: String) {
        listOtherPrimaryLast4s(bankId, last4).forEach { primaryLast4 ->
            detachSupplementariesOfPrimary(bankId, primaryLast4)
        }
        demoteOtherPrimaryCards(bankId, last4)
        updateCardRole(
            bankId = bankId,
            last4 = last4,
            cardRole = "PRIMARY",
            parentCardLast4 = null,
        )
        updateCardType(bankId, last4, "CREDIT")
    }

    @Transaction
    suspend fun setSupplementaryAtomic(bankId: String, last4: String, parentLast4: String) {
        val existing = get(bankId, last4)
        if (existing?.cardRole == "PRIMARY") {
            detachSupplementariesOfPrimary(bankId, last4)
        }
        updateCardRole(
            bankId = bankId,
            last4 = last4,
            cardRole = "SUPPLEMENTARY",
            parentCardLast4 = parentLast4,
        )
        updateCardType(bankId, last4, "CREDIT")
    }

    @Transaction
    suspend fun clearFacilityRoleAtomic(bankId: String, last4: String) {
        val existing = get(bankId, last4)
        if (existing?.cardRole == "PRIMARY") {
            detachSupplementariesOfPrimary(bankId, last4)
        }
        clearSupplementaryRole(bankId, last4)
        updateCardRole(
            bankId = bankId,
            last4 = last4,
            cardRole = "STANDALONE",
            parentCardLast4 = null,
        )
    }

    @Transaction
    suspend fun linkDebitAtomic(
        bankId: String,
        last4: String,
        linkedAccountBankId: String,
        linkedAccountMaskedNumber: String,
        defaultMadaWhenNetworkMissing: Boolean,
    ) {
        clearFacilityRoleAtomic(bankId, last4)
        updateLinkedAccount(
            bankId = bankId,
            last4 = last4,
            linkedAccountBankId = linkedAccountBankId,
            linkedAccountMaskedNumber = linkedAccountMaskedNumber,
        )
        updateCardType(bankId, last4, "DEBIT")
        if (defaultMadaWhenNetworkMissing && get(bankId, last4)?.cardNetwork == null) {
            updateCardNetwork(bankId, last4, "MADA")
        }
    }
}

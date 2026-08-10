package com.baraa.masroof.data.room.mapper

import com.baraa.masroof.data.room.entity.AccountRegistryEntity
import com.baraa.masroof.data.room.entity.CardRegistryEntity
import com.baraa.masroof.domain.model.AccountRegistryEntry
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.CardRegistryEntry
import com.baraa.masroof.domain.model.OwnershipStatus

object RegistryMapper {
    fun toAccountEntry(entity: AccountRegistryEntity): AccountRegistryEntry =
        AccountRegistryEntry(
            bank = Bank(entity.bankId),
            maskedNumber = entity.maskedNumber,
            ownership = OwnershipStatus.valueOf(entity.ownershipStatus),
            firstSeenRawSmsId = entity.firstSeenRawSmsId,
            lastSeenRawSmsId = entity.lastSeenRawSmsId,
        )

    fun toCardEntry(entity: CardRegistryEntity): CardRegistryEntry =
        CardRegistryEntry(
            bank = Bank(entity.bankId),
            last4 = entity.last4,
            ownership = OwnershipStatus.valueOf(entity.ownershipStatus),
            firstSeenRawSmsId = entity.firstSeenRawSmsId,
            lastSeenRawSmsId = entity.lastSeenRawSmsId,
        )
}

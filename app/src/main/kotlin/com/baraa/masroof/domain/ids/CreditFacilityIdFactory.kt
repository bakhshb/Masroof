package com.baraa.masroof.domain.ids

import com.baraa.masroof.domain.model.Bank

object CreditFacilityIdFactory {
    fun facilityId(bank: Bank, primaryLast4: String): String =
        RegistryEntityIdFactory.stableCreditFacilityId(bank.id, primaryLast4.trim())
}

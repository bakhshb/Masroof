package com.baraa.masroof.domain.ids

import com.baraa.masroof.domain.model.Bank

object CreditFacilityIdFactory {
    fun facilityId(bank: Bank, primaryLast4: String): String {
        require(bank != Bank.UNKNOWN) {
            "Bank.UNKNOWN is not a durable credit-facility identity"
        }
        return "facility:${bank.id}:${primaryLast4.trim()}"
    }
}

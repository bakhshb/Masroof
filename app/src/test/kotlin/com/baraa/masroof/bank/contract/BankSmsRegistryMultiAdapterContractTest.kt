package com.baraa.masroof.bank.contract

import com.baraa.masroof.bank.BankSmsRegistry
import com.baraa.masroof.bank.aljazira.AlJaziraSmsAdapter
import org.junit.Assert.assertTrue
import org.junit.Test

class BankSmsRegistryMultiAdapterContractTest {
    @Test
    fun registry_routesStubAndProductionAdaptersIndependently() {
        val registry = BankSmsRegistry(
            adapters = listOf(
                AlJaziraSmsAdapter(),
                StubBankSmsAdapter(),
            ),
        )

        val alJazira = registry.route("AlJazira", "purchase body")
        val stub = registry.route("StubBank", "any body")

        assertTrue(alJazira is com.baraa.masroof.bank.BankRoutingResult.Matched)
        assertTrue(stub is com.baraa.masroof.bank.BankRoutingResult.Matched)
    }
}

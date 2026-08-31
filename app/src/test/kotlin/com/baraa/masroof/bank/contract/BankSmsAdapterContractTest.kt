package com.baraa.masroof.bank.contract

import com.baraa.masroof.bank.BankSmsAdapter
import com.baraa.masroof.bank.aljazira.AlJaziraSmsAdapter
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class BankSmsAdapterContractTest(
    private val adapter: BankSmsAdapter,
) {
    @Test
    fun adapter_satisfiesSharedContract() {
        BankSmsAdapterContract.verify(adapter)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun adapters(): List<Array<Any>> =
            listOf(
                arrayOf(AlJaziraSmsAdapter()),
                arrayOf(StubBankSmsAdapter()),
            )
    }
}

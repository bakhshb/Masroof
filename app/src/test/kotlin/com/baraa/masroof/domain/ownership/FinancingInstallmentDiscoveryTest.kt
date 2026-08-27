package com.baraa.masroof.domain.ownership

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.data.repository.RoomAccountRegistryRepository
import com.baraa.masroof.data.repository.RoomCardRegistryRepository
import com.baraa.masroof.data.repository.RoomLoanRegistryRepository
import com.baraa.masroof.data.room.MasroofDatabase
import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.Confidence
import com.baraa.masroof.domain.model.LoanType
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.MoneyDirection
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.model.ParseStatus
import com.baraa.masroof.domain.model.ParsedEvent
import com.baraa.masroof.domain.model.LoanReference
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FinancingInstallmentDiscoveryTest {
    private lateinit var db: MasroofDatabase
    private lateinit var discovery: OwnershipDiscoveryService

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MasroofDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        discovery = OwnershipDiscoveryService(
            accountRegistry = RoomAccountRegistryRepository.from(db),
            cardRegistry = RoomCardRegistryRepository.from(db),
            loanRegistry = RoomLoanRegistryRepository.from(db),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun financingInstallment_discoversSourceAccountAndPersonalLoan() = runBlocking {
        val source = AccountReference(Bank.BANK_ALJAZIRA, "3001")
        discovery.observe(
            ParsedEvent(
                id = "evt-loan",
                rawSmsId = "sms-loan",
                bank = Bank.BANK_ALJAZIRA,
                messageFamily = MessageFamily.FINANCING_INSTALLMENT,
                direction = MoneyDirection.OUTGOING,
                amount = Money.of(BigDecimal("3036.11"), Currency.SAR),
                purchaseChannel = null,
                sourceAccountRef = source,
                destinationAccountRef = null,
                cardRef = null,
                merchant = null,
                counterparty = "تمويل شخصي",
                occurredAt = null,
                bankNetworkType = null,
                confidence = Confidence(0.95, listOf("financing_installment")),
                parseStatus = ParseStatus.SUCCESS,
            ),
        )

        assertEquals(OwnershipStatus.UNKNOWN, RoomAccountRegistryRepository.from(db).resolve(source))
        assertEquals(
            OwnershipStatus.UNKNOWN,
            RoomLoanRegistryRepository.from(db).resolve(LoanReference(Bank.BANK_ALJAZIRA, LoanType.PERSONAL)),
        )
        assertEquals(1, RoomLoanRegistryRepository.from(db).listAll().size)
    }
}

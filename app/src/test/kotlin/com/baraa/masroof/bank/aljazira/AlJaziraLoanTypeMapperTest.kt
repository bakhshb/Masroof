package com.baraa.masroof.bank.aljazira

import com.baraa.masroof.domain.model.LoanType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlJaziraLoanTypeMapperTest {
    @Test
    fun personalFinancingLabel_mapsToPersonal() {
        assertEquals(LoanType.PERSONAL, AlJaziraLoanTypeMapper.fromFinancingLabel("تمويل شخصي"))
    }

    @Test
    fun autoFinancingLabel_mapsToAuto() {
        assertEquals(LoanType.AUTO, AlJaziraLoanTypeMapper.fromFinancingLabel("تمويل سيارة"))
    }

    @Test
    fun mortgageFinancingLabel_mapsToMortgage() {
        assertEquals(LoanType.MORTGAGE, AlJaziraLoanTypeMapper.fromFinancingLabel("تمويل عقاري"))
    }

    @Test
    fun blankLabel_returnsNull() {
        assertNull(AlJaziraLoanTypeMapper.fromFinancingLabel(" "))
    }
}

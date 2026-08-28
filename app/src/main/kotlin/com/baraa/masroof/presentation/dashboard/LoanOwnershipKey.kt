package com.baraa.masroof.presentation.dashboard

import com.baraa.masroof.application.dashboard.LoanOverview
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.LoanType

object LoanOwnershipKey {
    fun of(loan: LoanOverview): String = of(loan.bank, loan.loanType)

    fun of(bank: Bank, loanType: LoanType): String = "${bank.id}:${loanType.name}"
}

package com.baraa.masroof.application.dashboard

/** Display totals for UI layers — alias of [AccountFlowSummary]. */
typealias AccountFlowTotals = AccountFlowSummary

fun CurrentAccountSummary.externalMovement(): AccountFlowTotals =
    accountFlow().externalSummary().toTotals()

fun CurrentAccountSummary.cashPosition(): AccountFlowTotals =
    accountFlow().accountSummary().toTotals()

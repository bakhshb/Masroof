package com.baraa.masroof.parsing.fixtures

import kotlinx.serialization.Serializable

/**
 * On-disk Bank AlJazira fixture schema for parser contract tests.
 *
 * Must not encode ownership or SELF_TRANSFER expectations.
 *
 * Extra extracted fields ([transactionReference], balances, [biller], …) are
 * asserted via parsing-layer [com.baraa.masroof.parsing.model.ParsedEventDetails].
 */
@Serializable
data class AlJaziraFixture(
    val id: String,
    val sender: String,
    val body: String,
    val expected: AlJaziraFixtureExpected,
    val notes: String? = null,
) {
    override fun toString(): String = id
}

@Serializable
data class AlJaziraFixtureExpected(
    val bank: String,
    val messageFamily: String,
    val direction: String? = null,
    val amount: String? = null,
    val currency: String? = null,
    val purchaseChannel: String? = null,
    val bankNetworkType: String? = null,
    val sourceAccountLast4: String? = null,
    val destinationAccountLast4: String? = null,
    val cardLast4: String? = null,
    val merchant: String? = null,
    val counterparty: String? = null,
    val biller: String? = null,
    val billerCode: String? = null,
    val transactionReference: String? = null,
    val availableBalance: String? = null,
    val outstandingBalance: String? = null,
    val occurredAt: String? = null,
    val parseStatus: String,
    val cardSmsChannel: String? = null,
    /** ISO-8601 local date. */
    val paymentDueDate: String? = null,
    val exchangeRate: String? = null,
    val internationalFee: String? = null,
    val labeledForeignAmount: String? = null,
    val labeledForeignCurrency: String? = null,
    val loanType: String? = null,
    val debitSourceAccountLast4: String? = null,
    val salaryIncomeWording: Boolean? = null,
)

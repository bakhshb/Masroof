package com.baraa.masroof.parsing.fixtures

import kotlinx.serialization.Serializable

/**
 * On-disk Bank AlJazira fixture schema for future P4 parser tests.
 *
 * Must not encode ownership or SELF_TRANSFER expectations.
 */
@Serializable
data class AlJaziraFixture(
    val id: String,
    val sender: String,
    val body: String,
    val expected: AlJaziraFixtureExpected,
    val notes: String? = null,
)

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
    val occurredAt: String? = null,
    val parseStatus: String,
)

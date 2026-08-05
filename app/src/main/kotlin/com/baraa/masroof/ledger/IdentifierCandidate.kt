package com.baraa.masroof.ledger

import com.baraa.masroof.data.db.AccountIdentifierType

enum class IdentifierTransactionRole { SOURCE, DESTINATION, PAYMENT_INSTRUMENT, RECEIVING_ACCOUNT, UNKNOWN }

data class IdentifierCandidate(
    val identifierType: AccountIdentifierType,
    val normalizedLastFour: String,
    val transactionRole: IdentifierTransactionRole,
    val sourceField: String,
    val confidence: Int = 100,
)

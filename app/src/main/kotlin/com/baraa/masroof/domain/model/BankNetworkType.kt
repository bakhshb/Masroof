package com.baraa.masroof.domain.model

/**
 * How the bank describes the transfer route.
 *
 * [INTRA_BANK] means "inside the same bank". It does **not** mean the transfer
 * is between accounts owned by the user.
 */
enum class BankNetworkType {
    INTRA_BANK,
    INTER_BANK,
    UNKNOWN,
}

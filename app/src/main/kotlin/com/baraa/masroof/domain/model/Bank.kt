package com.baraa.masroof.domain.model

/**
 * Financial institution recognized by Masroof.
 *
 * Only [BANK_ALJAZIRA] is in scope for the first parser. Other values exist so
 * cross-bank ownership scenarios can be expressed in the domain without
 * collapsing bank identity into free-form strings.
 */
enum class Bank {
    BANK_ALJAZIRA,
    D360,
    SNB,
    STC_BANK,
    ALRAJHI,
    UNKNOWN,
}

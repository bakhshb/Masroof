package com.baraa.masroof.transaction.banks

import com.baraa.masroof.transaction.GenericBankSmsParser

/**
 * Dedicated parser templates for the major Saudi banks + a few digital wallets.
 *
 * **Important**: real Saudi bank SMS samples are still required to add
 * bank-specific patterns on top of the shared base. The current concrete
 * parsers simply declare their sender aliases and inherit the generic
 * extraction logic — they will be enriched with real patterns as samples
 * become available. Adding a bank-specific pattern set should be a matter
 * of overriding the relevant protected hook on [GenericBankSmsParser] (or
 * adding new keyword / pattern lists and calling them from the base).
 *
 * Per the spec we deliberately do NOT add separate parsers for `mada`,
 * `Visa`, or `Mastercard`: those are card-network names that almost
 * always appear *inside* a bank message, not as the sender. If a real
 * sample proves otherwise, add them.
 */

// -- Priority 100 — dedicated bank parsers ---------------------------------

/** Al Rajhi Bank. */
class AlRajhiParser : GenericBankSmsParser() {
    override val name: String = "AlRajhi"
    override val priority: Int = 100
    override val senderAliases: List<String> = listOf("alrajhi", "rajhi", "al rajhi bank", "مصرف الراجحي")
}

/** Alinma Bank. */
class AlinmaParser : GenericBankSmsParser() {
    override val name: String = "Alinma"
    override val priority: Int = 100
    override val senderAliases: List<String> = listOf("alinma", "alinma bank", "مصرف الإنماء")
}

/** Saudi National Bank (SNB). */
class SNBParser : GenericBankSmsParser() {
    override val name: String = "SNB"
    override val priority: Int = 100
    override val senderAliases: List<String> = listOf("snb", "saudi national bank", "البنك الأهلي السعودي")
}

/** Riyad Bank. */
class RiyadBankParser : GenericBankSmsParser() {
    override val name: String = "RiyadBank"
    override val priority: Int = 100
    override val senderAliases: List<String> = listOf("riyadbank", "riyad bank", "بنك الرياض")
}

/** Bank Albilad. */
class BankAlbiladParser : GenericBankSmsParser() {
    override val name: String = "BankAlbilad"
    override val priority: Int = 100
    override val senderAliases: List<String> = listOf("bankalbilad", "albilad", "bank albilad", "بنك البلاد")
}

/** Banque Saudi Fransi (BSF). */
class BSFParser : GenericBankSmsParser() {
    override val name: String = "BSF"
    override val priority: Int = 100
    override val senderAliases: List<String> = listOf("bsf", "banque saudi fransi", "البنك السعودي الفرنسي")
}

/** Saudi Awwal Bank (SAB, formerly Saudi British Bank). */
class SABParser : GenericBankSmsParser() {
    override val name: String = "SAB"
    override val priority: Int = 100
    override val senderAliases: List<String> = listOf("sab", "saudi awwal bank", "saudi british bank", "بنك ساب")
}

/** Saudi Investment Bank (SAIB). */
class SAIBParser : GenericBankSmsParser() {
    override val name: String = "SAIB"
    override val priority: Int = 100
    override val senderAliases: List<String> = listOf("saib", "saudi investment bank", "البنك السعودي للاستثمار")
}

/** Bank AlJazira. */
class BankAlJaziraParser : GenericBankSmsParser() {
    override val name: String = "AlJazira"
    override val priority: Int = 100
    override val senderAliases: List<String> = listOf("aljazira", "bank aljazira", "بنك الجزيرة")
}

/** meem — SNB's digital bank. */
class MeemParser : GenericBankSmsParser() {
    override val name: String = "meem"
    override val priority: Int = 100
    override val senderAliases: List<String> = listOf("meem", "meem by snb", "مصرف ميم")
}

/** D360 — D360 Bank. */
class D360Parser : GenericBankSmsParser() {
    override val name: String = "D360"
    override val priority: Int = 100
    override val senderAliases: List<String> = listOf("d360", "d360 bank", "بنك د360")
}

/** STC Bank (Saudi Telecom Company banking arm). */
class STCBankParser : GenericBankSmsParser() {
    override val name: String = "STCBank"
    override val priority: Int = 100
    override val senderAliases: List<String> = listOf("stcbank", "stc bank", "بنك إس تي سي")
}

/** urpay — digital wallet. */
class UrpayParser : GenericBankSmsParser() {
    override val name: String = "urpay"
    override val priority: Int = 100
    override val senderAliases: List<String> = listOf("urpay", "ur pay", "أور باي")
}

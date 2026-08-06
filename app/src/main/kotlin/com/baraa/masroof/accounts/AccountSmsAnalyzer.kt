package com.baraa.masroof.accounts

import com.baraa.masroof.data.db.AccountIdentifierType
import com.baraa.masroof.sms.SenderNormalizer
import com.baraa.masroof.sms.SmsMessage
import com.baraa.masroof.transaction.AccountType
import com.baraa.masroof.transaction.BankParserRegistry
import com.baraa.masroof.transaction.ParsedTransaction

/** Pure local analysis of one user-selected SMS. It never persists the raw body. */
data class AccountSmsAnalysis(
    val senderDisplay: String,
    val senderKey: String,
    val parserName: String,
    val transactionTypeLabel: String,
    val confidence: Int,
    val identifierType: AccountIdentifierType?,
    val lastFour: String?,
    val warning: String?,
)

object AccountSmsAnalyzer {
    fun analyze(message: SmsMessage, accountType: AccountType): AccountSmsAnalysis? {
        val sender = message.sender?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val parsed = BankParserRegistry.parse(sender, message.body, message.timestamp.takeIf { it > 0L })
        val identifier = (parsed.accountOrCardLastFourDigits ?: labeledLastFour(message.body))
            ?.takeIf { it.length == 4 && it.all(Char::isDigit) }
        val type = identifier?.let { identifierType(parsed, accountType) }
        val incompatible = type != null && !compatible(accountType, type)
        return AccountSmsAnalysis(
            senderDisplay = sender,
            senderKey = SenderNormalizer.normalize(sender) ?: return null,
            parserName = parsed.parserName,
            transactionTypeLabel = transactionLabel(parsed),
            confidence = parsed.confidence,
            identifierType = type?.takeUnless { incompatible },
            lastFour = identifier?.takeUnless { incompatible },
            warning = when {
                identifier == null -> "لم تتضمن الرسالة رقمًا يحدد الحساب. يمكن ربط اسم المرسل فقط بعد التأكيد."
                incompatible -> "المعرف الظاهر لا يناسب نوع الحساب الذي اخترته. لن يتم حفظه."
                else -> null
            },
        )
    }

    private fun labeledLastFour(body: String?): String? = body?.let {
        Regex("(?:بطاقة\\s+(?:ائتمانية|مدى)|حساب|الآيبان|آيبان|iban)\\s*(?:رقم)?\\s*[:：]?\\s*([0-9٠-٩]{4})", RegexOption.IGNORE_CASE)
            .find(it)?.groupValues?.getOrNull(1)?.map { c -> if (c in '٠'..'٩') ('0' + (c - '٠')) else c }?.joinToString("")
    }

    private fun identifierType(parsed: ParsedTransaction, accountType: AccountType): AccountIdentifierType = when {
        parsed.originalMessage.orEmpty().contains("مدى") -> AccountIdentifierType.DEBIT_CARD_LAST4
        parsed.originalMessage.orEmpty().contains("ائتمان") || parsed.originalMessage.orEmpty().contains("credit", true) -> AccountIdentifierType.CREDIT_CARD_LAST4
        accountType == AccountType.CREDIT_CARD -> AccountIdentifierType.CREDIT_CARD_LAST4
        accountType in setOf(AccountType.DIGITAL_WALLET, AccountType.WALLET) -> AccountIdentifierType.WALLET_LAST4
        else -> AccountIdentifierType.ACCOUNT_LAST4
    }

    private fun compatible(accountType: AccountType, type: AccountIdentifierType): Boolean = when (accountType) {
        AccountType.BANK_ACCOUNT -> type in setOf(AccountIdentifierType.ACCOUNT_LAST4, AccountIdentifierType.DEBIT_CARD_LAST4, AccountIdentifierType.IBAN_LAST4)
        AccountType.CREDIT_CARD -> type == AccountIdentifierType.CREDIT_CARD_LAST4
        AccountType.DIGITAL_WALLET, AccountType.WALLET -> type in setOf(AccountIdentifierType.WALLET_LAST4, AccountIdentifierType.ACCOUNT_LAST4)
        AccountType.INVESTMENT_ACCOUNT, AccountType.SUKUK_ACCOUNT -> type in setOf(AccountIdentifierType.ACCOUNT_LAST4, AccountIdentifierType.IBAN_LAST4)
        else -> false
    }

    private fun transactionLabel(parsed: ParsedTransaction): String = when (parsed.transactionType) {
        com.baraa.masroof.transaction.TransactionType.ONLINE_PURCHASE -> "شراء عبر الإنترنت"
        com.baraa.masroof.transaction.TransactionType.PURCHASE -> "شراء"
        com.baraa.masroof.transaction.TransactionType.SALARY -> "راتب"
        com.baraa.masroof.transaction.TransactionType.TRANSFER_IN -> "حوالة واردة"
        com.baraa.masroof.transaction.TransactionType.TRANSFER_OUT -> "حوالة صادرة"
        else -> "رسالة مالية"
    }
}

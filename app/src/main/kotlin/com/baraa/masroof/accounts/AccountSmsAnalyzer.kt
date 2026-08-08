package com.baraa.masroof.accounts

import com.baraa.masroof.data.db.AccountIdentifierType
import com.baraa.masroof.ledger.AccountIdentifierCompatibility
import com.baraa.masroof.sms.SenderNormalizer
import com.baraa.masroof.sms.SmsMessage
import com.baraa.masroof.sms.TemplateResolutionResult
import com.baraa.masroof.sms.TemplateResolutionService
import com.baraa.masroof.data.repository.MessagePattern
import com.baraa.masroof.transaction.AccountType
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
    /**
     * Collapsed picker preview: omits OTP/balance lines and masks long digit
     * runs while keeping the last four visible (e.g. `••••7271`) so the user
     * can pick the right account SMS.
     *
     * @param maxChars truncate length; use a larger value for pattern teaching samples.
     * @param preserveNewlines keep line breaks (pattern detail) instead of collapsing to one line.
     */
    fun sanitizedPreview(
        body: String?,
        maxChars: Int = 110,
        preserveNewlines: Boolean = false,
    ): String {
        val lines = body.orEmpty().lineSequence()
            .filterNot {
                it.contains("otp", true) ||
                    it.contains("رمز التحقق") ||
                    it.contains("الرصيد") ||
                    it.contains("balance", true)
            }
            .map { line ->
                line.replace(Regex("\\d{4,}")) { match ->
                    val digits = match.value
                    "••••" + digits.takeLast(4)
                }.trim()
            }
            .filter { it.isNotEmpty() }
        val safe = if (preserveNewlines) {
            lines.joinToString("\n")
        } else {
            lines.joinToString(" ").replace(Regex("\\s+"), " ").trim()
        }
        return if (maxChars <= 0 || safe.length <= maxChars) safe else safe.take(maxChars)
    }

    fun analyze(
        message: SmsMessage,
        accountType: AccountType,
        patterns: List<MessagePattern> = emptyList(),
    ): AccountSmsAnalysis? {
        val sender = message.sender?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val outcome = TemplateResolutionService.resolve(
            sender,
            message.body,
            message.timestamp.takeIf { it > 0L },
            patterns,
        )
        val parsed = (outcome as? TemplateResolutionResult.Matched)?.parsed
            ?: return AccountSmsAnalysis(
                senderDisplay = sender,
                senderKey = SenderNormalizer.normalize(sender) ?: return null,
                parserName = "CanonicalTemplateResolver",
                transactionTypeLabel = when (outcome) {
                    is TemplateResolutionResult.Ambiguous -> "أكثر من قالب مطابق"
                    is TemplateResolutionResult.Unmatched -> "لا يوجد قالب معتمد مطابق"
                    else -> "رسالة غير مطابقة"
                },
                confidence = 0,
                identifierType = null,
                lastFour = null,
                warning = "يجب اعتماد قالب مطابق قبل استخراج معرف الحساب.",
            )
        val evidence = parsed.identifierEvidence.firstOrNull {
            AccountIdentifierCompatibility.isCompatibleTyped(accountType, it.type)
        }
        val identifier = evidence?.lastFour?.takeIf { it.length == 4 && it.all(Char::isDigit) }
        val type = evidence?.type
        val incompatible = type != null && !AccountIdentifierCompatibility.isCompatibleTyped(accountType, type)
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

    private fun transactionLabel(parsed: ParsedTransaction): String = when (parsed.transactionType) {
        com.baraa.masroof.transaction.TransactionType.ONLINE_PURCHASE -> "شراء عبر الإنترنت"
        com.baraa.masroof.transaction.TransactionType.PURCHASE -> "شراء"
        com.baraa.masroof.transaction.TransactionType.SALARY -> "راتب"
        com.baraa.masroof.transaction.TransactionType.TRANSFER_IN -> "حوالة واردة"
        com.baraa.masroof.transaction.TransactionType.TRANSFER_OUT -> "حوالة صادرة"
        else -> "رسالة مالية"
    }
}

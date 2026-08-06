package com.baraa.masroof.ledger

import com.baraa.masroof.data.db.AccountIdentifierEntity
import com.baraa.masroof.data.db.AccountIdentifierType
import com.baraa.masroof.data.db.FinancialAccountEntity
import com.baraa.masroof.data.db.SenderInstitutionMappingDao
import kotlinx.coroutines.runBlocking
import java.text.Normalizer
import java.util.Locale

/** Where the institution display name ultimately came from. */
enum class InstitutionIdentificationSource {
    /** Resolved through a sender alias that is registered on a financial account. */
    ACCOUNT_SENDER_ALIAS,
    /** The parser emitted a known bank name. */
    PARSED_INSTITUTION,
    /** A user previously confirmed a sender → institution mapping. */
    USER_CONFIRMED_MAPPING,
    /** Identified via a bank-specific parser identity (e.g. AlRajhi parser). */
    PARSER_MATCH,
    /** Could not identify a financial institution. */
    UNKNOWN,
}

/**
 * Result of institution resolution. Always non-null; unknown senders
 * return [InstitutionResolution.Unknown]. Identifier evidence is
 * carried through but does **not** change the institution report.
 */
data class InstitutionResolution(
    val institutionId: Long? = null,
    val institutionDisplayName: String,
    val source: InstitutionIdentificationSource,
    val confidence: Int,
    val requiresReview: Boolean,
    val sourceInstitutionName: String? = null,
    val destinationInstitutionName: String? = null,
    val senderKey: String?,
) {
    val isUnknown: Boolean get() = source == InstitutionIdentificationSource.UNKNOWN

    companion object {
        val Unknown = InstitutionResolution(
            institutionDisplayName = "مرسل مالي غير معروف",
            source = InstitutionIdentificationSource.UNKNOWN,
            confidence = 0,
            requiresReview = true,
            senderKey = null,
        )
    }
}

/**
 * Pure-ish resolver that turns a sender / parsed bank name / institution
 * hint into a friendly display label. It must never:
 *
 *  - override typed account-identifier evidence when picking an account
 *  - prefer the first database row when multiple compatible accounts exist
 *  - invent a bank name from weak evidence
 *
 * The resolver is intentionally conservative: when uncertain it returns
 * [InstitutionResolution.Unknown] with `requiresReview = true`.
 */
class FinancialInstitutionResolver(
    private val senderMappingDao: SenderInstitutionMappingDao,
) {
    suspend fun resolve(
        sender: String?,
        parsedInstitution: String? = null,
        destinationParsedInstitution: String? = null,
        parserIdentity: String? = null,
        knownInstitutionNames: Set<String> = emptySet(),
    ): InstitutionResolution {
        val key = normalizeSender(sender)
        if (key == null) {
            return InstitutionResolution(
                institutionDisplayName = parsedInstitution ?: "مرسل مالي غير معروف",
                source = if (parsedInstitution != null) InstitutionIdentificationSource.PARSED_INSTITUTION else InstitutionIdentificationSource.UNKNOWN,
                confidence = if (parsedInstitution != null) 70 else 0,
                requiresReview = parsedInstitution == null,
                sourceInstitutionName = parsedInstitution,
                destinationInstitutionName = destinationParsedInstitution,
                senderKey = null,
            )
        }
        // 1. User-confirmed mapping wins.
        // Old v12 rows may contain punctuation in senderKey. Keep this
        // read fallback while all new writes use the canonical key.
        val saved = senderMappingDao.findByKey(key)
            ?: sender?.trim()?.lowercase(Locale.ROOT)?.let { senderMappingDao.findByKey(it) }
        if (saved != null && saved.isActive) {
            return InstitutionResolution(
                institutionId = saved.id,
                institutionDisplayName = saved.institutionName,
                source = InstitutionIdentificationSource.USER_CONFIRMED_MAPPING,
                confidence = 95,
                requiresReview = false,
                sourceInstitutionName = saved.institutionName,
                destinationInstitutionName = destinationParsedInstitution,
                senderKey = key,
            )
        }
        // 2. Parser-detected institution name (only if not generic).
        parsedInstitution
            ?.takeIf { it.isNotBlank() }
            ?.let { return InstitutionResolution(
                institutionDisplayName = it,
                source = InstitutionIdentificationSource.PARSED_INSTITUTION,
                confidence = 80,
                requiresReview = false,
                sourceInstitutionName = it,
                destinationInstitutionName = destinationParsedInstitution,
                senderKey = key,
            ) }
        // 3. Saved known institution names (e.g. from AccountInstitutionSeed).
        knownInstitutionNames.firstOrNull { it.equals(parserIdentity, ignoreCase = true) }
            ?.let { return InstitutionResolution(
                institutionDisplayName = it,
                source = InstitutionIdentificationSource.PARSER_MATCH,
                confidence = 75,
                requiresReview = false,
                sourceInstitutionName = it,
                destinationInstitutionName = destinationParsedInstitution,
                senderKey = key,
            ) }
        return InstitutionResolution.Unknown.copy(
            senderKey = key,
            sourceInstitutionName = parsedInstitution,
            destinationInstitutionName = destinationParsedInstitution,
        )
    }

    suspend fun confirm(sender: String?, institutionName: String): Boolean {
        val key = normalizeSender(sender) ?: return false
        if (institutionName.isBlank()) return false
        val now = System.currentTimeMillis()
        val existing = senderMappingDao.findByKey(key)
        if (existing != null) {
            senderMappingDao.insert(
                existing.copy(
                    institutionName = institutionName,
                    isActive = true,
                    confirmationCount = existing.confirmationCount + 1,
                    lastConfirmedAt = now,
                ),
            )
        } else {
            senderMappingDao.insert(
                SenderInstitutionMappingEntityStub(
                    senderKey = key,
                    institutionName = institutionName,
                    lastConfirmedAt = now,
                    createdAt = now,
                ),
            )
        }
        return true
    }

    private fun normalizeSender(sender: String?): String? = senderKey(sender)

    private fun SenderInstitutionMappingEntityStub(
        senderKey: String,
        institutionName: String,
        lastConfirmedAt: Long,
        createdAt: Long,
    ) = com.baraa.masroof.data.db.SenderInstitutionMappingEntity(
        senderKey = senderKey,
        institutionName = institutionName,
        isActive = true,
        confirmationCount = 1,
        lastConfirmedAt = lastConfirmedAt,
        createdAt = createdAt,
    )

    companion object {
        /** Friendly institution seed labels (used for picker dropdowns). */
        val WELL_KNOWN_INSTITUTIONS: List<String> = listOf(
            "البنك الأهلي السعودي",
            "بنك الجزيرة",
            "مصرف الراجحي",
            "مصرف الإنماء",
            "بنك الرياض",
            "D360",
            "STC Bank",
        )

        /** Account-level institution extraction. Identifies "Sender alias identity" but
         * does NOT override typed identifier evidence. */
        fun pickInstitutionNameFromAccount(account: FinancialAccountEntity): String? =
            account.institutionName?.takeIf { it.isNotBlank() }

        /** True if the typed identifier claim matches the given account type. */
        fun typedIdentifierMatchesAccountType(
            account: FinancialAccountEntity,
            identifierType: AccountIdentifierType,
        ): Boolean = when (identifierType) {
            AccountIdentifierType.CREDIT_CARD_LAST4 -> account.accountType == com.baraa.masroof.transaction.AccountType.CREDIT_CARD
            AccountIdentifierType.DEBIT_CARD_LAST4, AccountIdentifierType.WALLET_LAST4, AccountIdentifierType.ACCOUNT_LAST4, AccountIdentifierType.IBAN_LAST4 -> true
            AccountIdentifierType.SENDER_ALIAS -> true
        }

        /** Stable sender key for identifier storage, mirrors [normalizeSender]. */
        fun senderKey(sender: String?): String? {
            if (sender.isNullOrBlank()) return null
            val normalized = Normalizer.normalize(sender, Normalizer.Form.NFKC)
                .lowercase(Locale.ROOT)
                .map { ch -> when (ch) {
                    '\u0660' -> '0'; '\u0661' -> '1'; '\u0662' -> '2'; '\u0663' -> '3'; '\u0664' -> '4'
                    '\u0665' -> '5'; '\u0666' -> '6'; '\u0667' -> '7'; '\u0668' -> '8'; '\u0669' -> '9'
                    else -> ch
                } }
                .filter { it.isLetterOrDigit() }
                .joinToString("")
            return normalized.takeIf { it.isNotBlank() }?.take(64)
        }
    }
}

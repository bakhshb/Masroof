package com.baraa.masroof.ledger

import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.FinancialAccountEntity
import com.baraa.masroof.data.db.JournalWithPostings
import com.baraa.masroof.data.db.LedgerPostingEntity
import com.baraa.masroof.data.db.OpeningBalanceKind
import com.baraa.masroof.transaction.AccountNature
import com.baraa.masroof.transaction.AccountType
import com.baraa.masroof.transaction.Currency
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Per-account reconciliation result driven **only** by POSTED journals
 * and the active journal-derived postings.  Drafts / needs-review / reversed
 * / voided entries are never counted.
 */
data class AccountSummary(
    val accountId: Long,
    val accountDisplayLabel: String,
    val accountType: AccountType,
    val accountNature: AccountNature,
    val currency: Currency,
    val openingBalance: BigDecimal,
    val openingBalanceDate: LocalDate,
    val totalDebits: BigDecimal,
    val totalCredits: BigDecimal,
    val calculatedBalance: BigDecimal,
    val includedPostings: Int,
    val excludedPostings: Int,
    val lastRecalculationAt: Long,
) {
    /** True when this is a credit-card account — uses outstanding/available logic. */
    val isCreditCard: Boolean get() = accountType == AccountType.CREDIT_CARD

    /**
     * Signed change in the account's reported balance since opening:
     *  - ASSET (bank/wallet): debits − credits (expenses reduce the balance)
     *  - LIABILITY (card): credits − debits (purchases raise outstanding)
     */
    val balanceDelta: BigDecimal
        get() = when (accountNature) {
            AccountNature.ASSET -> totalDebits.subtract(totalCredits)
            AccountNature.LIABILITY -> totalCredits.subtract(totalDebits)
        }

    /** @deprecated Use [balanceDelta]; kept for callers that assumed credit−debit. */
    val netMovement: BigDecimal get() = balanceDelta

    /** Cash-in (asset) or new charges (liability). */
    val moneyIn: BigDecimal
        get() = when (accountNature) {
            AccountNature.ASSET -> totalDebits
            AccountNature.LIABILITY -> totalCredits
        }

    /** Cash-out (asset) or payments reducing debt (liability). */
    val moneyOut: BigDecimal
        get() = when (accountNature) {
            AccountNature.ASSET -> totalCredits
            AccountNature.LIABILITY -> totalDebits
        }

    /**
     * For credit cards:
     *  outstanding = openingOutstanding
     *              + purchases + fees + interest      (in: purchases-as-debit on clearing)
     *              - payments - refunds              (out: debits repay the liability)
     *
     *  available_credit = creditLimit - outstanding
     *
     * The opening balance for a credit card stored as a positive liability
     * is interpreted as "amount owed on day one".
     */
    val outstandingBalance: BigDecimal? get() = if (isCreditCard) calculatedBalance else null
}

/**
 * Pure, deterministic calculator for account reconciliation. Single source
 * of truth for "what is the account balance right now" so the dashboard and
 * account screens cannot drift.
 *
 * Algorithm:
 * 1. Start from [FinancialAccount.openingBalance] (start-of-day position).
 * 2. Include only POSTED journals whose effectiveDate >= openingBalanceDate.
 * 3. Exclude REVERSED / VOIDED / unposted. Drafts are filtered out.
 * 4. Filter postings that belong to the account AND to the account's currency.
 * 5. Apply posting signs based on the nature of the account:
 *    - ASSET and BANK / WALLET / CASH:
 *        debit  => balance increases
 *        credit => balance decreases
 *    - LIABILITY and CREDIT CARD:
 *        debit  => balance decreases (you pay it down)
 *        credit => balance increases (a new purchase adds to what you owe)
 *
 * For internal transfers, the source account is debited and the
 * destination account is credited; the journal generator already produces
 * two balanced postings, so the resulting balances reflect both sides
 * without affecting income or spending totals.
 *
 * For credit-card purchases, the generator debits the EXPENSE clearing
 * account and credits the credit card itself — i.e. the credit-card
 * outstanding balance **grows** via the CREDIT posting.
 *
 * For credit-card payments, the generator debits the credit card
 * (paying it down) and credits the bank account. The credit-card
 * outstanding balance shrinks via that DEBIT.
 */
object AccountBalanceCalculator {
    fun calculate(
        account: FinancialAccountEntity,
        journals: List<JournalWithPostings>,
        zoneId: ZoneId = ZoneId.systemDefault(),
        nowProvider: () -> Long = { System.currentTimeMillis() },
    ): AccountSummary {
        val openingDate = account.openingBalanceDate.toLocalDateOrMin(zoneId)
        val effectiveOpening = effectiveOpeningBalance(account)
        val postings = journals
            .asSequence()
            .filter { it.journal.postingStatus == JournalPostingStatus.POSTED }
            .filter { it.journal.effectiveDate >= openingDate }
            .flatMap { journal -> journal.postings.map { posting -> journal to posting } }
            .toList()
        val ours = postings.filter { it.second.accountId == account.id }
        val included = ours.filter { it.second.currency == account.currency }
        val excluded = ours.size - included.size
        val totalDebits = included.filter { it.second.postingSide == PostingSide.DEBIT }
            .fold(BigDecimal.ZERO) { acc, p -> acc.add(p.second.amount) }
        val totalCredits = included.filter { it.second.postingSide == PostingSide.CREDIT }
            .fold(BigDecimal.ZERO) { acc, p -> acc.add(p.second.amount) }
        // Apply based on account nature and type:
        //  - normal ASSET (bank/wallet/cash): DEBIT increases, CREDIT decreases
        //  - normal LIABILITY:                DEBIT decreases, CREDIT increases
        //  - credit card (always LIABILITY in our model): same as above.
        //  - for assets that act like "your money", we follow the standard
        //    accounting convention used by the ledger generator.
        val calculated = when (account.accountNature) {
            AccountNature.ASSET -> effectiveOpening.add(totalDebits).subtract(totalCredits)
            AccountNature.LIABILITY -> effectiveOpening.subtract(totalDebits).add(totalCredits)
        }
        return AccountSummary(
            accountId = account.id,
            accountDisplayLabel = account.displayName,
            accountType = account.accountType,
            accountNature = account.accountNature,
            currency = account.currency,
            openingBalance = effectiveOpening,
            openingBalanceDate = openingDate,
            totalDebits = totalDebits,
            totalCredits = totalCredits,
            calculatedBalance = calculated,
            includedPostings = included.size,
            excludedPostings = excluded,
            lastRecalculationAt = nowProvider(),
        )
    }

    fun calculateMany(
        accounts: List<FinancialAccountEntity>,
        journals: List<JournalWithPostings>,
        zoneId: ZoneId = ZoneId.systemDefault(),
        nowProvider: () -> Long = { System.currentTimeMillis() },
    ): Map<Long, AccountSummary> = accounts
        .filter { it.isActive && it.systemAccountKey == null }
        .associate { account -> account.id to calculate(account, journals, zoneId, nowProvider) }
}

/** Converts a millis-based opening balance date to [LocalDate] in [zoneId]; empty when not set. */
fun Long.toLocalDateOrMin(zoneId: ZoneId): LocalDate =
    if (this <= 0L) LocalDate.MIN else Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()

/** Type-classification helper for a financial account. Returns true for accounts
 * whose outstanding balance should be displayed as "المستحق". */
fun FinancialAccount.isCreditCardAccount(): Boolean =
    accountType == AccountType.CREDIT_CARD

/**
 * Maps a credit-card account's stored [openingBalance] from
 * "opening available" to "opening outstanding":
 *
 *     openingOutstanding = creditLimit - openingAvailable
 *
 * Available only when [openingBalance] is non-negative and a positive
 * credit limit is known.
 *
 * If [creditLimit] is unknown (zero/null), we assume the stored value
 * already represents outstanding and return it as-is. This preserves
 * backwards compatibility with users who entered "outstanding" directly.
 */
fun openingOutstanding(
    account: FinancialAccount,
    creditLimit: BigDecimal?,
): BigDecimal {
    if (!account.isCreditCardAccount()) return account.openingBalance
    val limit = creditLimit ?: BigDecimal.ZERO
    if (limit.signum() <= 0) return account.openingBalance
    // If the stored opening is small relative to limit, treat it as "available"
    // and convert; otherwise treat as outstanding.
    // We use the user-set "openingAvailableCredit" flag if present:
    val openingAvailable = if (account.notes?.contains("opening_available") == true) true else false
    return if (openingAvailable) limit.subtract(account.openingBalance).coerceAtLeast(BigDecimal.ZERO)
    else account.openingBalance
}

/**
 * Returns the account's effective opening balance in BigDecimal terms of
 * "outstanding" (i.e. amount owed for credit cards, balance for assets).
 *
 *  - For non-credit-card accounts: returns [FinancialAccountEntity.openingBalance].
 *  - For credit-card accounts:
 *      - If [FinancialAccountEntity.openingBalanceKind] is [OpeningBalanceKind.OUTSTANDING],
 *        stored value is used directly.
 *      - If [OpeningBalanceKind.AVAILABLE], converts to
 *        outstanding = creditLimit - openingAvailable (when limit > 0).
 */
private fun effectiveOpeningBalance(account: FinancialAccountEntity): BigDecimal {
    if (account.accountType != AccountType.CREDIT_CARD) return account.openingBalance
    return when (account.openingBalanceKind) {
        OpeningBalanceKind.OUTSTANDING -> account.openingBalance
        OpeningBalanceKind.AVAILABLE -> {
            val limit = account.creditLimit ?: BigDecimal.ZERO
            if (limit.signum() <= 0) account.openingBalance
            else limit.subtract(account.openingBalance).coerceAtLeast(BigDecimal.ZERO)
        }
    }
}

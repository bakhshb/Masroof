package com.baraa.masroof.bank.aljazira

import com.baraa.masroof.bank.aljazira.classification.AlJaziraMessageClassifier
import com.baraa.masroof.bank.aljazira.extraction.AccountExtractor
import com.baraa.masroof.bank.aljazira.extraction.AmountExtractor
import com.baraa.masroof.bank.aljazira.extraction.BalanceExtractor
import com.baraa.masroof.bank.aljazira.extraction.BillerExtractor
import com.baraa.masroof.bank.aljazira.extraction.CardExtractor
import com.baraa.masroof.bank.aljazira.extraction.CounterpartyExtractor
import com.baraa.masroof.bank.aljazira.extraction.DateTimeExtractor
import com.baraa.masroof.bank.aljazira.extraction.LoanLabelExtractor
import com.baraa.masroof.bank.aljazira.extraction.MerchantExtractor
import com.baraa.masroof.bank.aljazira.extraction.ReferenceExtractor
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.Confidence
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.ParseStatus
import com.baraa.masroof.parsing.finalize.ParseFinalizer
import com.baraa.masroof.parsing.model.AmountSourceKind
import com.baraa.masroof.parsing.model.NormalizedSms
import com.baraa.masroof.parsing.model.ParseResult
import com.baraa.masroof.parsing.model.ParsedEventDetails
import com.baraa.masroof.parsing.model.ParsedEventDraft
import com.baraa.masroof.parsing.model.SmsParseInput
import com.baraa.masroof.parsing.parser.BankMessageParser
import com.baraa.masroof.parsing.validator.DefaultParsedEventValidator

/**
 * Production Bank AlJazira SMS parser.
 *
 * Pipeline: detect → classify → extract → draft → validate → finalize.
 * Stops at parse facts. Never decides ownership, SELF_TRANSFER, or financial type.
 */
class AlJaziraMessageParser(
    private val detector: AlJaziraBankDetector = AlJaziraBankDetector(),
    private val classifier: AlJaziraMessageClassifier = AlJaziraMessageClassifier(),
    private val amountExtractor: AmountExtractor = AmountExtractor(),
    private val balanceExtractor: BalanceExtractor = BalanceExtractor(),
    private val cardExtractor: CardExtractor = CardExtractor(),
    private val accountExtractor: AccountExtractor = AccountExtractor(),
    private val merchantExtractor: MerchantExtractor = MerchantExtractor(),
    private val counterpartyExtractor: CounterpartyExtractor = CounterpartyExtractor(),
    private val billerExtractor: BillerExtractor = BillerExtractor(),
    private val loanLabelExtractor: LoanLabelExtractor = LoanLabelExtractor(),
    private val referenceExtractor: ReferenceExtractor = ReferenceExtractor(),
    private val dateTimeExtractor: DateTimeExtractor = DateTimeExtractor(),
    private val finalizer: ParseFinalizer = ParseFinalizer(DefaultParsedEventValidator()),
) : BankMessageParser {

    override val bank: Bank = Bank.BANK_ALJAZIRA

    override fun canHandle(message: NormalizedSms, sender: String): Boolean =
        detector.detect(sender, message.originalBody) is com.baraa.masroof.parsing.model.BankDetectionResult.Detected

    override fun parse(input: SmsParseInput, normalized: NormalizedSms): ParseResult {
        val detection = detector.detect(input.sender, input.body)
        if (detection !is com.baraa.masroof.parsing.model.BankDetectionResult.Detected) {
            return ParseResult.Unsupported(reason = "not_bank_aljazira")
        }

        val classification = classifier.classify(normalized)
        val amountCandidates = amountExtractor.extract(normalized)
        val txnAmounts = amountCandidates.filter { it.sourceKind == AmountSourceKind.TRANSACTION_AMOUNT }
        val distinctTxnValues = txnAmounts.map { it.value }.distinct()
        val selectedAmount = when {
            classification.family.isNonFinancialFamily() -> null
            distinctTxnValues.isEmpty() -> null
            distinctTxnValues.size == 1 -> txnAmounts.first { it.value == distinctTxnValues.single() }
            else -> null // multiple distinct Money values → finalize as review via V-007
        }
        val balances = balanceExtractor.extract(normalized)
        val card = cardExtractor.extract(normalized, bank)
        val accounts = accountExtractor.extract(
            sms = normalized,
            localBank = bank,
            family = classification.family,
            networkType = classification.bankNetworkType,
        )
        val biller = billerExtractor.extract(normalized)
        val reference = referenceExtractor.extract(normalized)
        val occurredLocal = dateTimeExtractor.extract(normalized)

        val merchant = when (classification.family) {
            MessageFamily.PURCHASE, MessageFamily.REFUND -> merchantExtractor.extract(normalized)
            else -> null
        }
        val counterparty = when (classification.family) {
            MessageFamily.TRANSFER_IN, MessageFamily.TRANSFER_OUT -> counterpartyExtractor.extract(normalized)
            MessageFamily.FINANCING_INSTALLMENT -> loanLabelExtractor.extract(normalized)
            else -> null
        }

        val details = ParsedEventDetails(
            transactionReference = reference,
            availableBalance = balances.availableBalance,
            outstandingBalance = balances.outstandingBalance,
            biller = biller.biller,
            billerCode = biller.billerCode,
            occurredAtLocal = occurredLocal,
        )

        val confidence = Confidence(
            score = classification.confidence.coerceIn(0.0, 1.0),
            reasons = classification.evidence +
                listOfNotNull(
                    selectedAmount?.let { "labeled_amount:${it.evidenceLabel}" },
                    card?.let { "card_last4" },
                    detection.evidence.firstOrNull(),
                ),
        )

        val draft = ParsedEventDraft(
            rawSmsId = input.rawSmsId,
            bank = bank,
            messageFamily = classification.family,
            direction = classification.direction,
            amount = selectedAmount?.value,
            purchaseChannel = classification.purchaseChannel,
            sourceAccountRef = accounts.source,
            destinationAccountRef = accounts.destination,
            cardRef = card,
            merchant = merchant,
            counterparty = counterparty,
            occurredAt = null, // local SMS time lives in details.occurredAtLocal
            bankNetworkType = classification.bankNetworkType,
            confidence = confidence,
            parseStatus = provisionalStatus(classification.family),
            details = details,
            amountCandidates = amountCandidates,
            selectedAmount = selectedAmount,
        )

        return finalizer.finalize(draft, eventId = "evt-${input.rawSmsId}")
    }

    private fun provisionalStatus(family: MessageFamily): ParseStatus = when (family) {
        MessageFamily.OTP, MessageFamily.NON_FINANCIAL, MessageFamily.BALANCE_NOTICE ->
            ParseStatus.NON_FINANCIAL
        MessageFamily.UNKNOWN -> ParseStatus.REVIEW_REQUIRED
        else -> ParseStatus.SUCCESS
    }

    private fun MessageFamily.isNonFinancialFamily(): Boolean = when (this) {
        MessageFamily.OTP,
        MessageFamily.NON_FINANCIAL,
        MessageFamily.BALANCE_NOTICE,
        -> true

        else -> false
    }
}

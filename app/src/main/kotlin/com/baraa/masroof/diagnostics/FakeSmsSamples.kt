package com.baraa.masroof.diagnostics

/**
 * Bundled fake SMS samples used by the developer "test data mode".
 *
 * **No real user messages appear here.** Each sample is constructed
 * from placeholder merchant names and amount ranges so it can be used
 * to exercise the parser pipeline without revealing any user data.
 */
object FakeSmsSamples {

    /**
     * One fake SMS sample: sender + body.
     *
     * The "channel" hint is used to pick a sensible default account
     * type for testing.
     */
    data class Sample(
        val id: String,
        val sender: String,
        val body: String,
        val channel: String,
        val label: String,
    )

    val samples: List<Sample> = listOf(
        // Purchase
        Sample(
            id = "purchase-arabic",
            sender = "AlRajhi",
            body = "تم الشراء من [MERCHANT] بمبلغ [AMOUNT] ريال. الرصيد المتاح [BALANCE] ر.س",
            channel = "POS",
            label = "شراء (POS)",
        ),
        Sample(
            id = "purchase-english",
            sender = "SNB",
            body = "Purchase at [MERCHANT] for [AMOUNT] SAR. Available balance [BALANCE] SAR. Card ****[CARD_LAST4]",
            channel = "POS",
            label = "Purchase (POS)",
        ),
        // Online purchase
        Sample(
            id = "online-arabic",
            sender = "STC Pay",
            body = "تم الدفع الإلكتروني لمبلغ [AMOUNT] ر.س عبر [MERCHANT]",
            channel = "ONLINE",
            label = "شراء عبر الإنترنت",
        ),
        Sample(
            id = "online-english",
            sender = "Visa",
            body = "Online purchase: [AMOUNT] USD at [MERCHANT]. Ref: [REFERENCE]",
            channel = "ONLINE",
            label = "Online purchase",
        ),
        // Refund
        Sample(
            id = "refund-arabic",
            sender = "AlRajhi",
            body = "تم استرداد مبلغ [AMOUNT] ر.س إلى حسابك",
            channel = "POS",
            label = "استرداد",
        ),
        Sample(
            id = "refund-english",
            sender = "Visa",
            body = "Refund processed: [AMOUNT] SAR to your card. Ref: [REFERENCE]",
            channel = "POS",
            label = "Refund",
        ),
        // Card payment (credit card bill)
        Sample(
            id = "card-payment-arabic",
            sender = "SAB",
            body = "تم سداد مبلغ [AMOUNT] ر.س لبطاقة الائتمان ****[CARD_LAST4]",
            channel = "CARD_PAYMENT",
            label = "سداد بطاقة",
        ),
        Sample(
            id = "card-payment-english",
            sender = "Visa",
            body = "Credit card payment [AMOUNT] SAR posted. Card ****[CARD_LAST4]",
            channel = "CARD_PAYMENT",
            label = "Card payment",
        ),
        // Outgoing transfer
        Sample(
            id = "transfer-out-arabic",
            sender = "AlRajhi",
            body = "تم تحويل مبلغ [AMOUNT] ر.س إلى حساب ****[ACCOUNT_LAST4]. الرصيد [BALANCE] ر.س",
            channel = "TRANSFER",
            label = "تحويل صادر",
        ),
        // Incoming transfer
        Sample(
            id = "transfer-in-arabic",
            sender = "AlRajhi",
            body = "وصلت تحويله بمبلغ [AMOUNT] ر.س من [NAME]، رصيدك [BALANCE] ر.س",
            channel = "TRANSFER",
            label = "تحويل وارد",
        ),
        // Internal wallet top-up
        Sample(
            id = "wallet-topup-arabic",
            sender = "STC Pay",
            body = "تم شحن المحفظة بمبلغ [AMOUNT] ر.س من بطاقتك ****[CARD_LAST4]",
            channel = "WALLET",
            label = "شحن محفظة",
        ),
        // Investment transfer
        Sample(
            id = "investment-arabic",
            sender = "Derayah",
            body = "تم تحويل [AMOUNT] ر.س إلى محفظتك الاستثمارية",
            channel = "INVESTMENT",
            label = "تحويل استثماري",
        ),
        // Salary
        Sample(
            id = "salary-arabic",
            sender = "Mudad",
            body = "تم إيداع راتبك الشهري بمبلغ [AMOUNT] ر.س في حسابك",
            channel = "DEPOSIT",
            label = "راتب",
        ),
        // Bank fee
        Sample(
            id = "bank-fee-arabic",
            sender = "AlRajhi",
            body = "تم خصم رسوم بنكية [AMOUNT] ر.س من حسابك",
            channel = "FEE",
            label = "رسوم بنكية",
        ),
        Sample(
            id = "bank-fee-english",
            sender = "SNB",
            body = "Bank fee [AMOUNT] SAR charged to your account",
            channel = "FEE",
            label = "Bank fee",
        ),
        // Declined
        Sample(
            id = "declined-arabic",
            sender = "AlRajhi",
            body = "تم رفض عملية شراء بمبلغ [AMOUNT] ر.س",
            channel = "POS",
            label = "عملية مرفوضة",
        ),
        Sample(
            id = "declined-english",
            sender = "Visa",
            body = "Transaction DECLINED. Insufficient funds.",
            channel = "POS",
            label = "Declined",
        ),
        // Pending
        Sample(
            id = "pending-arabic",
            sender = "AlRajhi",
            body = "عملية قيد المعالجة بمبلغ [AMOUNT] ر.س",
            channel = "POS",
            label = "قيد المعالجة",
        ),
        // Malformed
        Sample(
            id = "malformed-arabic",
            sender = "Bank",
            body = "اشعار من البنك: يتوجب عليك مراجعة الفرع",
            channel = "UNKNOWN",
            label = "رسالة غير صالحة",
        ),
        // Amount + balance (rich body)
        Sample(
            id = "amount-balance-arabic",
            sender = "AlRajhi",
            body = "شراء من [MERCHANT] بمبلغ [AMOUNT] ر.س بتاريخ 2024-01-15. الرصيد المتاح [BALANCE] ر.س. رقم العملية [REFERENCE]. رقم الحساب [IBAN]",
            channel = "POS",
            label = "شراء مع رصيد",
        ),
    )

    /** Placeholders that should remain in sanitized text. */
    val placeholders: Set<String> = setOf(
        "[MERCHANT]", "[AMOUNT]", "[BALANCE]", "[REFERENCE]",
        "[CARD_LAST4]", "[ACCOUNT_LAST4]", "[IBAN]", "[NAME]",
    )
}
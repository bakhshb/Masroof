package com.baraa.masroof.domain.model

/**
 * Provenance for a USD→SAR rate applied to a foreign-currency transaction.
 */
enum class ExchangeRateSource {
    /** سعر الصرف مذكور في رسالة البنك. */
    SMS,

    /** سعر مستمد من مشترى سابق لنفس التاجر. */
    HISTORICAL_MERCHANT,

    /** سعر سوق من الإنترنت (Frankfurter v2) لتاريخ العملية أو أقرب يوم متاح. */
    MARKET,
}

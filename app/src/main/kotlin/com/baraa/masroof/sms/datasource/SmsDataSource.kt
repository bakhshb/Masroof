package com.baraa.masroof.sms.datasource

import com.baraa.masroof.sms.model.ProviderSmsRecord
import java.time.Instant

/**
 * Abstraction over Android SMS inbox access for testability.
 *
 * Implementations must yield rows in deterministic oldest→newest order.
 */
interface SmsDataSource {
    /**
     * @param receivedAfter inclusive lower bound on DATE when non-null
     * @return inbox records ordered by DATE ASC
     * @throws SmsPermissionException when READ_SMS is denied
     * @throws SmsProviderException on provider/cursor failures that abort the scan
     */
    fun queryInbox(receivedAfter: Instant? = null): Sequence<ProviderSmsRecord>
}

/** READ_SMS not granted. */
class SmsPermissionException(
    message: String = "READ_SMS permission denied",
    cause: Throwable? = null,
) : Exception(message, cause)

/** Fatal SMS ContentProvider failure. */
class SmsProviderException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

package com.baraa.masroof.application.sms

import com.baraa.masroof.application.ingestion.ProcessRawSmsUseCase
import com.baraa.masroof.application.logging.AppLogCategories
import com.baraa.masroof.application.logging.AppLogFormatting
import com.baraa.masroof.application.logging.AppLogService
import com.baraa.masroof.domain.model.RawSms

/**
 * Application boundary for live SMS received from [com.baraa.masroof.sms.receiver.IncomingSmsReceiver].
 */
class LiveSmsIntake(
    private val processRawSms: ProcessRawSmsUseCase,
    private val appLogService: AppLogService,
) {
    suspend fun ingest(rawSms: RawSms) {
        appLogService.info(
            AppLogCategories.SMS,
            "Live SMS received from ${AppLogFormatting.maskSender(rawSms.sender)}",
        )
        processRawSms.ingest(rawSms)
    }
}

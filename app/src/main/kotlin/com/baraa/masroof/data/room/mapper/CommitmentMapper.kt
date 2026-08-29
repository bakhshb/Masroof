package com.baraa.masroof.data.room.mapper

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.data.room.entity.CommitmentEntity
import com.baraa.masroof.domain.model.Commitment
import com.baraa.masroof.domain.model.CommitmentRecurrence
import java.time.Instant
import java.time.LocalDate

object CommitmentMapper {
    fun toDomain(entity: CommitmentEntity): Commitment =
        Commitment(
            id = entity.id,
            name = entity.name,
            amount = Money(
                amount = entity.amountDecimal.toBigDecimal(),
                currency = Currency.valueOf(entity.amountCurrency),
            ),
            transactionDate = LocalDate.parse(entity.transactionDateIso),
            recurrence = entity.recurrence?.let(CommitmentRecurrence::valueOf),
            dueDate = entity.dueDateIso?.let(LocalDate::parse),
            active = entity.active,
            sourceTransactionId = entity.sourceTransactionId,
            createdAt = Instant.ofEpochMilli(entity.createdAtEpochMillis),
            updatedAt = Instant.ofEpochMilli(entity.updatedAtEpochMillis),
        )

    fun toEntity(domain: Commitment): CommitmentEntity =
        CommitmentEntity(
            id = domain.id,
            name = domain.name.trim(),
            amountDecimal = domain.amount.amount.toPlainString(),
            amountCurrency = domain.amount.currency.name,
            transactionDateIso = domain.transactionDate.toString(),
            recurrence = domain.recurrence?.name,
            dueDateIso = domain.dueDate?.toString(),
            active = domain.active,
            sourceTransactionId = domain.sourceTransactionId,
            createdAtEpochMillis = domain.createdAt.toEpochMilli(),
            updatedAtEpochMillis = domain.updatedAt.toEpochMilli(),
        )
}

package com.baraa.masroof.data.room.mapper

import com.baraa.masroof.data.room.entity.ReviewItemEntity
import com.baraa.masroof.domain.model.ReviewItem
import com.baraa.masroof.domain.model.ReviewKind
import com.baraa.masroof.domain.model.ReviewResolutionKind
import com.baraa.masroof.domain.model.ReviewStatus
import java.time.Instant

object ReviewItemMapper {
    fun toEntity(item: ReviewItem): ReviewItemEntity =
        ReviewItemEntity(
            id = item.id,
            rawSmsId = item.rawSmsId,
            kind = item.kind.name,
            status = item.status.name,
            reasons = encodeReasons(item.reasons),
            createdAtEpochMillis = item.createdAt.toEpochMilli(),
            updatedAtEpochMillis = item.updatedAt.toEpochMilli(),
            resolvedAtEpochMillis = item.resolvedAt?.toEpochMilli(),
            resolutionKind = item.resolutionKind?.name,
            resolvedTransactionId = item.resolvedTransactionId,
        )

    fun toDomain(entity: ReviewItemEntity): ReviewItem =
        ReviewItem(
            id = entity.id,
            rawSmsId = entity.rawSmsId,
            kind = ReviewKind.valueOf(entity.kind),
            status = ReviewStatus.valueOf(entity.status),
            reasons = decodeReasons(entity.reasons),
            createdAt = Instant.ofEpochMilli(entity.createdAtEpochMillis),
            updatedAt = Instant.ofEpochMilli(entity.updatedAtEpochMillis),
            resolvedAt = entity.resolvedAtEpochMillis?.let(Instant::ofEpochMilli),
            resolutionKind = entity.resolutionKind?.let(ReviewResolutionKind::valueOf),
            resolvedTransactionId = entity.resolvedTransactionId,
        )

    fun encodeReasons(reasons: List<String>): String {
        require(reasons.none { ReviewItemEntity.REASON_SEPARATOR in it }) {
            "Review reason must not contain the persistence separator"
        }
        return reasons.joinToString(ReviewItemEntity.REASON_SEPARATOR.toString())
    }

    fun decodeReasons(encoded: String): List<String> {
        if (encoded.isEmpty()) return emptyList()
        return encoded.split(ReviewItemEntity.REASON_SEPARATOR)
    }
}

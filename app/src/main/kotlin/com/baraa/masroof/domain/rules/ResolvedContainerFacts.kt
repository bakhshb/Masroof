package com.baraa.masroof.domain.rules

import com.baraa.masroof.domain.model.Account
import com.baraa.masroof.domain.model.Card
import com.baraa.masroof.domain.model.CardType
import com.baraa.masroof.domain.model.FinancialContainer
import com.baraa.masroof.domain.model.OwnershipStatus

/**
 * Lightweight container identity for classification: kind + ownership, and
 * [knownCardType] only when that type is a genuine domain fact (not invented).
 */
enum class ContainerKind {
    ACCOUNT,
    CARD,
}

data class ResolvedContainerFacts(
    val kind: ContainerKind,
    val ownership: OwnershipStatus,
    val knownCardType: CardType? = null,
) {
    init {
        require(kind == ContainerKind.CARD || knownCardType == null) {
            "knownCardType is only meaningful for CARD containers"
        }
    }

    companion object {
        fun from(container: FinancialContainer): ResolvedContainerFacts =
            when (container) {
                is Account ->
                    ResolvedContainerFacts(
                        kind = ContainerKind.ACCOUNT,
                        ownership = container.ownership,
                        knownCardType = null,
                    )

                is Card ->
                    ResolvedContainerFacts(
                        kind = ContainerKind.CARD,
                        ownership = container.ownership,
                        knownCardType = container.type,
                    )
            }
    }
}

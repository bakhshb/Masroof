package com.baraa.masroof.application.dashboard

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface DashboardLayoutPreferencesRepository {
    fun load(): DashboardLayoutSnapshot
    fun save(snapshot: DashboardLayoutSnapshot)
}

@Serializable
enum class DashboardSectionId {
    @SerialName("hero")
    HERO,

    @SerialName("quick")
    QUICK,

    @SerialName("accounts")
    ACCOUNTS,

    @SerialName("cards")
    CARDS,

    @SerialName("loans")
    LOANS,

    @SerialName("transactions")
    TRANSACTIONS,
}

@Serializable
enum class DashboardSectionSize {
    @SerialName("sm")
    SMALL,

    @SerialName("md")
    MEDIUM,

    @SerialName("lg")
    LARGE,
}

@Serializable
data class DashboardSectionEntry(
    val id: DashboardSectionId,
    val visible: Boolean = true,
    val size: DashboardSectionSize = DashboardSectionSize.MEDIUM,
)

@Serializable
data class DashboardLayoutSnapshot(
    val sections: List<DashboardSectionEntry>,
    val quickExpenseVisible: Boolean = true,
    val quickIncomeVisible: Boolean = true,
) {
    fun orderedVisibleSections(): List<DashboardSectionEntry> =
        sections.filter { it.visible }

    fun entry(id: DashboardSectionId): DashboardSectionEntry? =
        sections.firstOrNull { it.id == id }

    /** Appends sections introduced after a saved layout was stored (e.g. LOANS). */
    fun withMergedSections(): DashboardLayoutSnapshot {
        val defaults = default()
        val byId = sections.associateBy { it.id }.toMutableMap()
        defaults.sections.forEach { entry ->
            if (entry.id !in byId) {
                byId[entry.id] = entry
            }
        }
        val ordered = defaults.sections.mapNotNull { byId[it.id] }
        val extras = byId.values.filter { entry -> defaults.sections.none { it.id == entry.id } }
        return copy(sections = ordered + extras)
    }

    companion object {
        fun default(): DashboardLayoutSnapshot =
            DashboardLayoutSnapshot(
                sections = listOf(
                    DashboardSectionEntry(DashboardSectionId.HERO, size = DashboardSectionSize.LARGE),
                    DashboardSectionEntry(DashboardSectionId.QUICK, size = DashboardSectionSize.MEDIUM),
                    DashboardSectionEntry(DashboardSectionId.ACCOUNTS, size = DashboardSectionSize.MEDIUM),
                    DashboardSectionEntry(DashboardSectionId.CARDS, size = DashboardSectionSize.LARGE),
                    DashboardSectionEntry(DashboardSectionId.LOANS, size = DashboardSectionSize.MEDIUM),
                    DashboardSectionEntry(DashboardSectionId.TRANSACTIONS, size = DashboardSectionSize.MEDIUM),
                ),
            )
    }
}

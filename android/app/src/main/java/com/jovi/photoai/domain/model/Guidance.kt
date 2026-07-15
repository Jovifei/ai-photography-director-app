package com.jovi.photoai.domain.model

enum class GuidePanel {
    NONE,
    ENVIRONMENT,
    SUBJECT,
}

enum class GuidancePriority(val rank: Int) {
    SAFETY(6),
    FRAMING(5),
    POSITION(4),
    BODY(3),
    HAND(2),
    HEAD(1),
    EMOTION(0),
}

data class GuidanceItem(
    val id: String,
    val panel: GuidePanel,
    val priority: GuidancePriority,
    val title: String,
    val instruction: String,
) {
    init {
        require(id.isNotBlank()) { "Guidance id must not be blank" }
        require(title.isNotBlank()) { "Guidance title must not be blank" }
        require(instruction.isNotBlank()) { "Guidance instruction must not be blank" }
    }
}

/**
 * Returns the most urgent guidance while preserving input order for equal priorities.
 * An optional panel filter lets the UI select a panel-specific headline without duplicating
 * priority rules in a composable.
 */
fun selectHighestPriorityGuidance(
    items: Iterable<GuidanceItem>,
    panel: GuidePanel? = null,
): GuidanceItem? {
    var selected: GuidanceItem? = null
    for (item in items) {
        if (panel != null && item.panel != panel) continue
        if (selected == null || item.priority.rank > selected.priority.rank) {
            selected = item
        }
    }
    return selected
}

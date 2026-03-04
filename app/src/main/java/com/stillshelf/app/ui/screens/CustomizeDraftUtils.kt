package com.stillshelf.app.ui.screens

internal fun ensureListenAgainSection(rows: List<ToggleSectionItem>): List<ToggleSectionItem> {
    return if (rows.any { it.id == HomeSectionIds.LISTEN_AGAIN }) {
        rows
    } else {
        rows + ToggleSectionItem(
            id = HomeSectionIds.LISTEN_AGAIN,
            title = "Listen Again"
        )
    }
}

internal fun moveSectionRow(
    source: List<ToggleSectionItem>,
    from: Int,
    to: Int
): List<ToggleSectionItem> {
    if (source.isEmpty() || from !in source.indices || to !in source.indices || from == to) {
        return source
    }
    val mutable = source.toMutableList()
    val item = mutable.removeAt(from)
    mutable.add(to, item)
    return mutable
}

internal fun toggleHiddenSection(
    hidden: Set<String>,
    id: String
): Set<String> {
    val updated = hidden.toMutableSet()
    if (!updated.add(id)) {
        updated.remove(id)
    }
    return updated
}

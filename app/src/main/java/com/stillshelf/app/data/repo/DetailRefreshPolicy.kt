package com.stillshelf.app.data.repo

enum class DetailRefreshPolicy {
    IfMissing,
    IfStale,
    Force
}

internal fun shouldRefreshDetail(
    policy: DetailRefreshPolicy,
    localExists: Boolean,
    lastSuccessfulSyncAtMs: Long?,
    maxAgeMs: Long,
    nowMs: Long = System.currentTimeMillis()
): Boolean {
    return when (policy) {
        DetailRefreshPolicy.Force -> true
        DetailRefreshPolicy.IfMissing -> !localExists
        DetailRefreshPolicy.IfStale -> {
            !localExists ||
                lastSuccessfulSyncAtMs == null ||
                (nowMs - lastSuccessfulSyncAtMs) > maxAgeMs
        }
    }
}

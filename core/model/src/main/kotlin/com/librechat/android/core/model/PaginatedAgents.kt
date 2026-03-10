package com.librechat.android.core.model

/** Wrapper for a single page of agent marketplace results. */
data class PaginatedAgents(
    val agents: List<Agent>,
    val hasMore: Boolean,
    val total: Int,
)

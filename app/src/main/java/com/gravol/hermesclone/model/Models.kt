package com.gravol.hermesclone.model

import kotlinx.serialization.Serializable

/** A saved gateway entry shown in the gateway switcher. */
@Serializable
data class SavedGateway(
    val id: String,
    val baseUrl: String,
    val username: String,
    val password: String = "",
) {
    val host: String
        get() = baseUrl.substringAfter("://").substringBefore("/")
}

/** A gateway profile ("agent") — a group in the session tree. */
data class ProfileInfo(
    val name: String,
    val description: String = "",
    val isDefault: Boolean = false,
)

/** One persisted session row from GET /api/sessions (long ids like 20261122_211856_012d96). */
data class SessionRow(
    val id: String,
    val title: String,
    val updatedAt: Long?,
    val archived: Boolean = false,
    val hidden: Boolean = false,
    /** every id-ish field the gateway returned, for short↔long id matching */
    val idCandidates: Set<String> = setOf(id),
)

class SessionPage(val sessions: List<SessionRow>, val total: Int?)

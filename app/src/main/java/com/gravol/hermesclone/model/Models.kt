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
    /** roster `pinned` flag from the server (0/1 int in SQLite-JSON) */
    val serverPinned: Boolean = false,
    /** profile this row was loaded under (archived screen + delete/archive calls need it) */
    val profile: String = "",
    /** roster message_count, shown on the Archived screen ("19 messages") */
    val messageCount: Int? = null,
)

class SessionPage(val sessions: List<SessionRow>, val total: Int?)

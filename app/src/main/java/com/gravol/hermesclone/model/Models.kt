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
/** One cron routine from GET /api/cron/jobs (shape verified live 2026-11-24).
 *  Official Routines page sorts by next_run_at asc with paused (null next) first. */
data class CronJob(
    val id: String,
    val name: String,
    val script: String?,
    val prompt: String?,
    val scheduleKind: String,      // interval | cron | once
    val scheduleMinutes: Int?,
    val scheduleCron: String?,     // raw cron expr or server display for once
    val nextRunAt: Long?,          // epoch ms; null when paused/completed
    val state: String,             // scheduled | paused | completed
    val enabled: Boolean,
    val lastStatus: String?,       // ok | error | null
)

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
    /** roster cwd / git_repo_root — Project view groups by basename of these (official parity) */
    val cwd: String = "",
    val gitRepoRoot: String = "",
    /** roster source tag (routine runs / kanban workers classification — values still being learned) */
    val source: String = "",
    val unread: Boolean = false,
) {
    val project: String
        get() {
            val root = gitRepoRoot.ifBlank { cwd }
            if (root.isBlank()) return "No project"
            val base = root.trimEnd('/').substringAfterLast('/')
            return when {
                base.isBlank() || base == "/" -> "Home"
                base.equals("jeff", true) -> "Home"
                else -> base.replaceFirstChar { it.uppercase() }
            }
        }
}

class SessionPage(val sessions: List<SessionRow>, val total: Int?)

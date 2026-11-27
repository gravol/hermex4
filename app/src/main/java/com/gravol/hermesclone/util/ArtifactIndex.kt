package com.gravol.hermesclone.util

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import java.io.File

/**
 * Persistent per-session artifact index — the official keeps
 * no_backup/artifact-index/<sha256>.json holding the FULL artifact list ever
 * observed for a session (id = "<profile>:<session>:<source>"). Server-side
 * compaction rewrites old tool results to one-line summaries, so a REST-only
 * scan shrinks after every compaction; the official does not shrink because
 * its index is cumulative. This mirrors that behaviour.
 */
object ArtifactIndex {

    private val json = Json { ignoreUnknownKeys = true }

    private fun indexFile(ctx: Context, profile: String, session: String): File {
        val dir = File(ctx.noBackupFilesDir, "artifact-index").apply { mkdirs() }
        return File(dir, (profile + "__" + session).sanitize())
    }

    private fun String.sanitize() = map {
        if (it.isLetterOrDigit() || it == '.' || it == '-' || it == '_') it else '_'
    }.joinToString("")

    fun load(ctx: Context, profile: String, session: String): List<Artifact> = runCatching {
        val f = indexFile(ctx, profile, session)
        if (!f.exists()) return emptyList()
        val root = json.parseToJsonElement(f.readText()) as? JsonObject ?: return emptyList()
        (root["artifacts"] as? JsonArray).orEmpty().mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val value = (o["source"] as? JsonPrimitive)?.contentOrNullSafe() ?: return@mapNotNull null
            val kindName = (o["kind"] as? JsonPrimitive)?.contentOrNullSafe()
            Artifact(
                id = (o["id"] as? JsonPrimitive)?.contentOrNullSafe()
                    ?: "$profile:$session:$value",
                kind3 = when (kindName) {
                    "Image" -> ArtifactKind3.IMAGE
                    "File" -> ArtifactKind3.FILE
                    else -> ArtifactKind3.LINK
                },
                kind = ArtifactKind.valueOf((o["uiKind"] as? JsonPrimitive)?.contentOrNullSafe() ?: "Other"),
                value = value,
                label = (o["label"] as? JsonPrimitive)?.contentOrNullSafe() ?: value,
                sessionId = (o["sessionId"] as? JsonPrimitive)?.contentOrNullSafe() ?: session,
                sessionTitle = (o["sessionTitle"] as? JsonPrimitive)?.contentOrNullSafe() ?: "",
                timestampMs = ((o["createdAt"] as? JsonPrimitive)?.doubleOrNull ?: 0.0).toLong() * 1000L,
                profile = profile,
            )
        }
    }.getOrDefault(emptyList())

    fun save(ctx: Context, profile: String, session: String, list: List<Artifact>) = runCatching {
        val body = buildJsonObject {
            put("revision", java.util.UUID.randomUUID().toString())
            put("profile", profile)
            put("session", session)
            put("artifacts", buildJsonArray {
                for (a in list) add(buildJsonObject {
                    put("id", a.id)
                    put("kind", when (a.kind3) {
                        ArtifactKind3.IMAGE -> "Image"
                        ArtifactKind3.FILE -> "File"
                        ArtifactKind3.LINK -> "Link"
                    })
                    put("uiKind", a.kind.name)
                    put("source", a.value)
                    put("label", a.label)
                    put("sessionId", a.sessionId)
                    put("sessionTitle", a.sessionTitle)
                    put("createdAt", a.timestampMs / 1000.0)
                })
            })
        }
        indexFile(ctx, profile, session).writeText(body.toString())
    }

    private fun JsonPrimitive.contentOrNullSafe(): String? =
        if (this is kotlinx.serialization.json.JsonNull) null else content
}

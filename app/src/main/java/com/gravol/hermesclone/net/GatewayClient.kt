package com.gravol.hermesclone.net

import com.gravol.hermesclone.model.ChatMsg
import com.gravol.hermesclone.model.ProfileInfo
import com.gravol.hermesclone.model.ToolCard
import com.gravol.hermesclone.model.SessionPage
import com.gravol.hermesclone.model.SessionRow
import java.time.Instant
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * REST client for a Hermes gateway, per CONTRACT.md:
 *  - login: basic auth (username + password)
 *  - session cookie: hermes_session_rt via Set-Cookie (kept in an in-memory jar)
 *  - observed endpoints: /auth/password-login, /api/auth/ws-ticket, /api/status,
 *    /api/profiles, /api/sessions?archived=include&limit=100&offset=N&order=recent&profile=<p>
 *
 * JSON result shapes other than the wire names are not in CONTRACT.md; parsing is
 * deliberately lenient and every guess is listed in NOTES.md.
 */
class GatewayClient(rawBaseUrl: String) {

    val baseUrl: String = normalizeBase(rawBaseUrl)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                if (cookies.isEmpty()) return
                cookieStore.getOrPut(url.host) { mutableListOf() }.let { store ->
                    for (c in cookies) {
                        store.removeAll { it.name == c.name && it.path == c.path }
                        store.add(c)
                    }
                }
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                val now = System.currentTimeMillis()
                val store = cookieStore[url.host] ?: return emptyList()
                store.removeAll { it.expiresAt < now }
                return store.filter { it.matches(url) }
            }
        })
        .build()

    companion object {
        const val SESSION_COOKIE = "hermes_session_rt" // CONTRACT.md (observed)
        const val PAGE_SIZE = 100                      // CONTRACT.md (observed)
        const val DEFAULT_PORT = 9119                  // CONTRACT.md + DEX string "https://hermes.local:9119"

        fun normalizeBase(raw: String): String {
            var u = raw.trim().trimEnd('/')
            if (!u.contains("://")) u = "http://$u"
            return u
        }
    }

    val hasSessionCookie: Boolean
        get() = cookieStore.values.any { list -> list.any { it.name == SESSION_COOKIE } }

    fun clearCookies() = cookieStore.clear()

    /**
     * POST /auth/password-login — official Lzaf;->e sends a JSON body {username,password}
     * with Content-Type application/json and NO Authorization header (cookie jar handles
     * the session; success sets hermes_session_rt via Set-Cookie).
     */
    suspend fun login(username: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val bodyJson = JsonObject(mapOf(
                "provider" to JsonPrimitive("basic"),   // VERIFIED live 2026-11-24: server 422s without this
                "username" to JsonPrimitive(username),
                "password" to JsonPrimitive(password),
            )).toString()
            val req = Request.Builder()
                .url("$baseUrl/auth/password-login")
                .post(bodyJson.toRequestBody("application/json".toMediaType()))
                .build()
            http.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) Unit
                else error("login-http-${resp.code}")
            }
        }
    }

    /** GET /api/status — observed endpoint; used as a post-login liveness check. */
    suspend fun status(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            get("$baseUrl/api/status")?.let { true } ?: false
        }.getOrDefault(false)
    }

    /** GET /api/profiles — profiles are the groups of the session tree. */
    suspend fun profiles(): Result<List<ProfileInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            val root = get("$baseUrl/api/profiles") ?: error("empty response")
            val arr = when {
                root is JsonArray -> root
                root is JsonObject && root["profiles"] is JsonArray -> root["profiles"] as JsonArray
                root is JsonObject && root["profiles"] is JsonObject ->
                    (root["profiles"] as JsonObject).entries.map { (k, v) ->
                        JsonObject(v.jsonObject.toMutableMap().also { it.putIfAbsent("name", JsonPrimitive(k)) })
                    }
                else -> JsonArray(emptyList())
            }
            arr.mapNotNull { el ->
                if (el !is JsonObject) {
                    val s = el.jsonPrimitive.contentOrNull
                    if (!s.isNullOrBlank()) ProfileInfo(s) else null
                } else {
                    val name = firstString(el, "name", "profile", "id", "key") ?: return@mapNotNull null
                    ProfileInfo(
                        name = name,
                        description = firstString(el, "description", "desc", "about", "persona") ?: "",
                        isDefault = (el["is_default"] as? JsonPrimitive)?.booleanOrNull == true ||
                            (el["is_current"] as? JsonPrimitive)?.booleanOrNull == true,
                        )
                }
            }
        }
    }

    /**
     * GET /api/sessions?archived=include&limit=100&offset=N&order=recent&profile=<p>
     * (exact query order per CONTRACT.md; paging offset += 100)
     */
    suspend fun sessions(profile: String, offset: Int): Result<SessionPage> = withContext(Dispatchers.IO) {
        runCatching {
            val url = buildString {
                append(baseUrl).append("/api/sessions?archived=include&limit=").append(PAGE_SIZE)
                .append("&offset=").append(offset).append("&order=recent&profile=").append(profile)
            }
            val root = get(url) ?: error("empty response")
            val arr = when {
                root is JsonArray -> root
                root is JsonObject -> (root["sessions"] ?: root["items"] ?: root["results"]) as? JsonArray
                    ?: JsonArray(emptyList())
                else -> JsonArray(emptyList())
            }
            val total = (root as? JsonObject)?.let {
                longOf(it, "total", "total_count", "count")?.toInt()
            }
            val rows = arr.mapNotNull { el ->
                if (el !is JsonObject) {
                    val s = el.jsonPrimitive.contentOrNull
                    if (!s.isNullOrBlank()) SessionRow(s, s, null) else null
                } else parseSession(el, profile)
            }
            SessionPage(rows, total)
        }
    }

    /**
     * GET /api/sessions/<id>/messages?limit=24&order=latest
     * VERIFIED live 2026-11-24: {session_id, profile, messages:[{id:int, session_id, role,
     * content, tool_call_id, tool_calls:[{id, function:{name, arguments}}]}], pagination}.
     * tool rows are folded away (assistant tool_calls pills carry the display); system skipped.
     */
    suspend fun messages(sessionId: String, limit: Int = 24): Result<List<ChatMsg>> = withContext(Dispatchers.IO) {
        runCatching {
            val root = get("$baseUrl/api/sessions/$sessionId/messages?limit=$limit&order=latest")
                ?: error("empty response")
            val arr = (root as? JsonObject)?.get("messages") as? JsonArray ?: JsonArray(emptyList())
            // Verified live 2026-11-24 (curl with the app's exact limit=24&order=latest):
            // rows arrive ASCENDING (oldest first) — no reversal needed.
            arr.mapNotNull { el ->
                if (el !is JsonObject) return@mapNotNull null
                val role = firstString(el, "role") ?: return@mapNotNull null
                if (role == "tool" || role == "system") return@mapNotNull null
                // server marks rows invisible to chat UI (interrupted turns etc.)
                if (firstString(el, "display_kind") == "hidden") return@mapNotNull null
                val content = firstString(el, "content") ?: ""
                val cards = (el["tool_calls"] as? JsonArray)?.mapNotNull { tc ->
                    if (tc !is JsonObject) return@mapNotNull null
                    val fn = tc["function"] as? JsonObject ?: return@mapNotNull null
                    val name = firstString(fn, "name") ?: return@mapNotNull null
                    ToolCard(
                        toolId = firstString(tc, "id", "call_id") ?: name,
                        name = name,
                        context = "",
                        done = true,
                    )
                }.orEmpty()
                if (role == "assistant" && content.isBlank() && cards.isEmpty()) return@mapNotNull null
                val ts = (el["timestamp"] as? JsonPrimitive)?.content?.toDoubleOrNull()
                ChatMsg(
                    key = "h" + (firstString(el, "id") ?: System.nanoTime().toString()),
                    role = role,
                    content = content,
                    tools = cards,
                    ts = ts?.toLong(),
                )
            }
        }
    }

    /**
     * GET /api/auth/ws-ticket (official Lpx3;->d): the response key is exactly "ticket";
     * the sig for `hermes-gateway-ticket.<sig>` IS the ticket (Lco6;->W @0x4b–0x53).
     * Error copy mirrors the official: HTTP 401/403 → "WebSocket ticket request returned
     * HTTP <n>"; any other non-success → "The agent is asleep."
     */
    suspend fun wsTicket(): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        runCatching {
            // VERIFIED live 2026-11-24: GET 404s, POST {} returns {ticket,ttl_seconds}
            val req = Request.Builder()
                .url("$baseUrl/api/auth/ws-ticket")
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
            val root = http.newCall(req).execute().use { resp ->
                when {
                    resp.code == 401 || resp.code == 403 ->
                        error("WebSocket ticket request returned HTTP ${resp.code}")
                    !resp.isSuccessful -> error("The agent is asleep. (HTTP ${resp.code} ${resp.message} <- ${resp.request.method} ${resp.request.url})")
                    else -> resp.body?.string()?.takeIf { it.isNotBlank() }
                        ?.let { json.parseToJsonElement(it) } ?: error("Ticket response has no ticket")
                }
            }
            val ticket = when (root) {
                is JsonPrimitive -> root.contentOrNull
                is JsonObject -> firstString(root, "ticket")
                else -> null
            } ?: error("Ticket response has no ticket")
            if (ticket.isBlank()) error("Empty WebSocket ticket")
            ticket to ticket // sig == ticket (Lco6;->W)
        }
    }

    private suspend fun get(url: String): JsonElement? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).get().build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("http-${resp.code}")
            val text = resp.body?.string() ?: return@use null
            if (text.isBlank()) null else json.parseToJsonElement(text)
        }
    }

    private fun parseSession(el: JsonObject, profile: String = ""): SessionRow? {
        val id = firstString(el, "id", "session_id", "sessionid", "sid") ?: return null
        val title = firstString(el, "title", "name", "label", "summary")
            ?: getString(el, "first_message")?.take(80)
            ?: "Untitled session"
        val updatedAt = longOf(el, "last_active", "updated_at", "updated", "last_active_at",
            "last_message_at", "last_activity_at", "modified_at", "created_at")
        // roster rows are SQLite-JSON: booleans arrive as 0/1 ints, not true/false
        // (verified against the official app's row model cnc.java 2026-11-24)
        val archived = truthy(el["archived"] as? JsonPrimitive)
        val hidden = truthy(el["hidden"] as? JsonPrimitive)
        val pinnedSrv = truthy(el["pinned"] as? JsonPrimitive)
        val candidates = mutableSetOf(id)
        for (k in listOf("session_id", "live_id", "short_id", "sid", "id")) {
            (el[k] as? JsonPrimitive)?.contentOrNull?.let { if (it.isNotBlank()) candidates.add(it) }
        }
        val msgCount = (el["message_count"] as? JsonPrimitive)?.intOrNull
            ?: (el["messages"] as? JsonPrimitive)?.intOrNull
        return SessionRow(id, title, updatedAt, archived, hidden, candidates,
            serverPinned = pinnedSrv, profile = profile, messageCount = msgCount)
    }

    private fun truthy(p: JsonPrimitive?): Boolean =
        p != null && p.content.lowercase() in setOf("true", "1")

    private fun firstString(o: JsonObject, vararg keys: String): String? {
        for (k in keys) {
            val v = (o[k] as? JsonPrimitive)?.contentOrNull
            if (!v.isNullOrBlank()) return v
        }
        return null
    }

    private fun getString(o: JsonObject, key: String): String? =
        (o[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    /** lenient timestamp: epoch seconds, epoch millis, or ISO-8601 string */
    private fun longOf(o: JsonObject, vararg keys: String): Long? {
        for (k in keys) {
            val p = o[k] as? JsonPrimitive ?: continue
            p.longOrNull?.let { n ->
                return if (n > 1_000_000_000_000L) n / 1000 else n // millis → seconds
            }
            p.contentOrNull?.toDoubleOrNull()?.let { d ->
                val n = d.toLong()
                return if (n > 1_000_000_000_000L) n / 1000 else n
            }
            p.contentOrNull?.let { s ->
                runCatching { return Instant.parse(s).epochSecond }
                runCatching { return OffsetDateTime.parse(s).toEpochSecond() }
            }
        }
        return null
    }
}

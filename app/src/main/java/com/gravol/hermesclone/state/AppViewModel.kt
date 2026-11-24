package com.gravol.hermesclone.state

import android.app.Application
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gravol.hermesclone.model.ChatMsg
import com.gravol.hermesclone.model.ClarifyQ
import com.gravol.hermesclone.model.ModelProviderRow
import com.gravol.hermesclone.model.ModelSheetState
import com.gravol.hermesclone.model.PendingSrq
import com.gravol.hermesclone.model.ProfileInfo
import com.gravol.hermesclone.model.ToolCard
import com.gravol.hermesclone.model.SavedGateway
import com.gravol.hermesclone.model.SessionRow
import com.gravol.hermesclone.net.GatewayClient
import com.gravol.hermesclone.net.WsClient
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

enum class ConnPhase { DISCONNECTED, CONNECTING, LIVE, RECONNECTING }

data class ProfileGroup(
    val profile: String,
    val sessions: List<SessionRow> = emptyList(),
    val total: Int? = null,
    val loadedPages: Int = 0,
    val expanded: Boolean = false,
    val expandedByUser: Boolean = false,
    val loading: Boolean = false,
)


/** Phase 2 chat surface state (one open session). Wire flow VERIFIED 2026-11-24. */
data class ChatState(
    val open: Boolean = false,
    val longId: String = "",
    val shortId: String? = null,
    val profile: String = "",
    val title: String = "",
    val model: String? = null,
    val loading: Boolean = false,
    val messages: List<ChatMsg> = emptyList(),
    val streaming: ChatMsg? = null,
    val spinner: String = "",
    val running: Boolean = false,
    val sending: Boolean = false,
    val error: String? = null,
)

data class UiState(
    val gateways: List<SavedGateway> = emptyList(),
    val activeGateway: SavedGateway? = null,
    val phase: ConnPhase = ConnPhase.DISCONNECTED,
    val statusText: String = "",
    val error: String? = null,
    val profiles: List<ProfileInfo> = emptyList(),
    val groups: Map<String, ProfileGroup> = emptyMap(),
    val working: Set<String> = emptySet(),
    val selectedProfile: String? = null, // null = All
    val query: String = "",
    val refreshing: Boolean = false,
    val avatars: Map<String, ImageBitmap> = emptyMap(),
    val profileOrder: List<String> = emptyList(),
    val chat: ChatState = ChatState(),
    /** unanswered server->client requests (approval/clarify cards), phase 3 */
    val pendingSrqs: List<PendingSrq> = emptyList(),
    /** phase 4: model picker sheet */
    val modelSheet: ModelSheetState = ModelSheetState(),
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("gateways", Application.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var client: GatewayClient? = null
    private var ws: WsClient? = null
    // short live id -> long REST id, learned from session.active_list session_key (VERIFIED 2026-11-24)
    private val liveIdMap = java.util.concurrent.ConcurrentHashMap<String, String>()
    // per-session last event seq (VERIFIED: session.events.since takes {session_id:<short>, last_seen:<seq>})
    private val sessionSeq = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private var pingJob: Job? = null
    private var reconnectJob: Job? = null
    private var manualDisconnect = false

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // WS: no read timeout
        .pingInterval(0, TimeUnit.SECONDS)     // keepalive via gateway.ping RPC instead
        .build()

    init {
        _state.update { it.copy(gateways = loadGateways()) }
    }

    // ---------------------------------------------------------------- gateways

    fun saveGateway(url: String, username: String, password: String): SavedGateway {
        val gw = SavedGateway(
            id = UUID.randomUUID().toString().take(8),
            baseUrl = GatewayClient.normalizeBase(url),
            username = username,
            password = password,
        )
        _state.update { s ->
            val list = s.gateways.filterNot { it.baseUrl == gw.baseUrl } + gw
            persistGateways(list)
            s.copy(gateways = list)
        }
        return gw
    }

    fun deleteGateway(id: String) {
        _state.update { s ->
            val list = s.gateways.filterNot { it.id == id }
            persistGateways(list)
            s.copy(gateways = list)
        }
    }

    private fun loadGateways(): List<SavedGateway> {
        val raw = prefs.getString("gateways", null) ?: return emptyList()
        return runCatching {
            json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(SavedGateway.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    private fun persistGateways(list: List<SavedGateway>) {
        prefs.edit()
            .putString("gateways",
                json.encodeToString(kotlinx.serialization.builtins.ListSerializer(SavedGateway.serializer()), list))
            .apply()
    }

    // ---------------------------------------------------------------- connect

    fun connect(gw: SavedGateway) {
        manualDisconnect = false
        teardownWs()
        val c = GatewayClient(gw.baseUrl)
        client = c
        _state.update {
            it.copy(
                activeGateway = gw,
                phase = ConnPhase.CONNECTING,
                statusText = "Signing in…",
                error = null,
                profiles = emptyList(),
                groups = emptyMap(),
                working = emptySet(),
                selectedProfile = null,
                avatars = emptyMap(),
            )
        }
        viewModelScope.launch {
            // 1) basic-auth login → hermes_session_rt cookie
            c.login(gw.username, gw.password)
                .onFailure {
                    _state.update {
                        it.copy(
                            phase = ConnPhase.DISCONNECTED,
                            statusText = "",
                            error = "Hermes did not accept that username and password.",
                        )
                    }
                    return@launch
                }
            _state.update { it.copy(statusText = "Loading profiles…") }
            // 2) profiles (REST, CONTRACT.md)
            val profiles = c.profiles().getOrElse { emptyList() }
            _state.update { st ->
                st.copy(
                    profiles = profiles,
                    groups = profiles.associate { p ->
                        p.name to (st.groups[p.name] ?: ProfileGroup(p.name, expanded = p.isDefault))
                    },
                )
            }
            // 3) WS ticket + ticket WebSocket
            _state.update { it.copy(statusText = "Opening live connection…") }
            val ticketRes = c.wsTicket()
            if (ticketRes.isFailure) {
                val cause = ticketRes.exceptionOrNull()?.message ?: "?"
                _state.update {
                    it.copy(phase = ConnPhase.DISCONNECTED, statusText = "",
                        error = "Could not get a WebSocket ticket from the gateway. ($cause)")
                }
                return@launch
            }
            val (ticket, sig) = ticketRes.getOrThrow()
            openWs(c, gw, ticket, sig)
            // 4) sessions for every profile (first page)
            _state.update { it.copy(statusText = "Loading sessions…") }
            profiles.forEach { p -> loadPage(p.name, offset = 0, replace = true) }
            _state.update { it.copy(statusText = "", phase = ConnPhase.LIVE) }
            startPingLoop()
        }
    }

    fun disconnect() {
        manualDisconnect = true
        teardownWs()
        client?.clearCookies()
        _state.update {
            it.copy(phase = ConnPhase.DISCONNECTED, activeGateway = null, statusText = "",
                profiles = emptyList(), groups = emptyMap(), working = emptySet())
        }
    }

    private fun openWs(c: GatewayClient, gw: SavedGateway, ticket: String, sig: String) {
        val wsBase = c.baseUrl.replaceFirst("http", "ws") // http→ws, https→wss
        val url = "$wsBase/api/ws?ticket=${java.net.URLEncoder.encode(ticket, "UTF-8")}"
        // official Lco6;->W: subprotocol "hermes-gateway-ticket." + ticket (sig == ticket)
        val subprotocols = "${WsClient.SUBPROTOCOL_BASE}, ${WsClient.SUBPROTOCOL_TICKET_PREFIX}$sig"
        val w = WsClient(http, url, subprotocols)
        ws = w
        w.onEvent = { type, sessionId, payload, seq -> handleEvent(type, sessionId, payload, seq) }
        w.onServerRequest = { id, method, params -> handleServerRequest(w, id, method, params) }
        w.onClosed = { _, _ ->
            if (!manualDisconnect) {
                _state.update { it.copy(phase = ConnPhase.RECONNECTING, statusText = "Reconnecting…") }
                scheduleReconnect(c, gw)
            }
        }
        viewModelScope.launch {
            w.connect()
            try {
                w.awaitOpen()
            } catch (e: Exception) {
                if (!manualDisconnect) {
                    _state.update {
                        it.copy(phase = ConnPhase.RECONNECTING, statusText = "Reconnecting…")
                    }
                    scheduleReconnect(c, gw)
                }
                return@launch
            }
            handshake(w)
        }
    }

    private suspend fun handshake(w: WsClient) {
        // client.capabilities — CONTRACT.md (observed C2S method)
        runCatching { w.rpc("client.capabilities", buildJsonObject { put("server_requests", true) }) }
        // profiles.list (WS) — refresh group list
        runCatching {
            val res = w.rpc("profiles.list", buildJsonObject { })
            val names = parseNameList(res)
            if (names.isNotEmpty()) {
                _state.update { st ->
                    val groups = names.associateWith { n -> st.groups[n] ?: ProfileGroup(n) }
                    st.copy(profiles = names.map { n ->
                        st.profiles.firstOrNull { it.name == n } ?: ProfileInfo(n)
                    }, groups = groups)
                }
            }
        }
        // session.active_list per profile → live/working sessions (CONTRACT.md)
        refreshActiveLists(w)
        // profiles that arrived only via profiles.list still need their first page
        val known = _state.value.groups
        _state.value.profiles.map { it.name }
            .filter { known[it]?.sessions?.isEmpty() != false }
            .forEach { loadPage(it, 0, replace = true) }
        loadAvatars(_state.value.profiles.map { it.name })
        orderGroupsByRecency()
    }

    /**
     * Official order (verified 2026-11-24 vs official app on same gateway):
     * group with a live/working session first, then by most-recent session activity
     * descending. Auto-expand groups whose newest activity is within 24h
     * (official: live group + link-curator(13h) expanded; clerk(1w)/job-logger(23w) collapsed).
     */
    private fun orderGroupsByRecency() {
        _state.update { st ->
            val nowSec = System.currentTimeMillis() / 1000
            val liveProfiles = st.working.mapNotNull { id ->
                st.groups.entries.firstOrNull { (_, g) -> g.sessions.any { id in it.idCandidates } }?.key
            }.toSet()
            val sorted = st.profiles.map { it.name }.sortedWith(
                compareByDescending<String> { it in liveProfiles }
                    .thenByDescending { st.groups[it]?.sessions?.maxOfOrNull { s -> s.updatedAt ?: 0L } ?: 0L }
            )
            st.copy(
                profileOrder = sorted,
                groups = st.groups.mapValues { (name, g) ->
                    val newest = g.sessions.maxOfOrNull { s -> s.updatedAt ?: 0L } ?: 0L
                    if (g.expandedByUser) g
                    else g.copy(expanded = name in liveProfiles || (newest > 0 && nowSec - newest < 86400))
                },
            )
        }
    }

    private suspend fun refreshActiveLists(w: WsClient) {
        val profiles = _state.value.profiles.map { it.name }
        val working = mutableSetOf<String>()
        for (p in profiles) {
            runCatching {
                // VERIFIED live 2026-11-24: {sessions:[{id:<short>, session_key:<long REST id>,
                // status:"idle"|"working", ...}]} - profile param is IGNORED (global list).
                // Only status != "idle" is live; session_key is the short<->long mapping.
                val res = w.rpc("session.active_list", buildJsonObject { put("profile", p) })
                val sessions = (res as? JsonObject)?.get("sessions") as? JsonArray ?: return@runCatching
                for (s in sessions) {
                    val o = s as? JsonObject ?: continue
                    val status = (o["status"] as? JsonPrimitive)?.contentOrNull ?: continue
                    val shortId = (o["id"] as? JsonPrimitive)?.contentOrNull ?: continue
                    val longId = (o["session_key"] as? JsonPrimitive)?.contentOrNull
                    if (!longId.isNullOrBlank()) liveIdMap[shortId] = longId
                    if (!status.equals("idle", ignoreCase = true)) {
                        working.add(shortId)
                        if (!longId.isNullOrBlank()) working.add(longId)
                    }
                }
            }
        }
        _state.update { it.copy(working = working) }
    }

    private fun scheduleReconnect(c: GatewayClient, gw: SavedGateway) {
        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            var backoff = 1_000L
            while (!manualDisconnect) {
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(30_000L) // backoff cadence: estimated, see NOTES
                val ticket = c.wsTicket().getOrNull() ?: continue
                val w = WsClient(
                    http,
                    "${c.baseUrl.replaceFirst("http", "ws")}/api/ws?ticket=${java.net.URLEncoder.encode(ticket.first, "UTF-8")}",
                    "${WsClient.SUBPROTOCOL_BASE}, ${WsClient.SUBPROTOCOL_TICKET_PREFIX}${ticket.second ?: ticket.first}",
                )
                w.onEvent = { type, sessionId, payload, seq -> handleEvent(type, sessionId, payload, seq) }
                w.onServerRequest = { id, method, params -> handleServerRequest(w, id, method, params) }
                w.onClosed = { _, _ ->
                    if (!manualDisconnect) _state.update {
                        it.copy(phase = ConnPhase.RECONNECTING, statusText = "Reconnecting…")
                    }
                }
                ws = w
                w.connect()
                val opened = runCatching { w.awaitOpen() }.isSuccess
                if (!opened) continue
                // official Lco6;->q @0x58: after a reconnect the client waits 1000 ms, then
                // replays session.events.since per pending session (dropping any whose last
                // event is older than 90 s — 0xd18c2e2800 ns @0xa5).
                delay(1_000)
                // replay gap: session.events.since — VERIFIED params are per-session
                // {session_id:<short>, last_seen:<seq>} (NOT global epoch/seq — old call was wrong).
                // open_requests[] entries look exactly like live srq frames → re-arm the cards.
                val chatSid = _state.value.chat.shortId
                if (chatSid != null) runCatching {
                    val since = w.rpc("session.events.since", buildJsonObject {
                        put("session_id", chatSid)
                        put("last_seen", sessionSeq[chatSid] ?: 0L)
                    })
                    if (since is JsonObject) {
                        w.noteReplayState(
                            (since["latest_seq"] as? JsonPrimitive)?.content?.toLongOrNull(),
                            (since["epoch"] as? JsonPrimitive)?.contentOrNull,
                        )
                        if (since["truncated"]?.jsonPrimitive?.booleanOrNull == true) {
                            profilesSnapshot().forEach { p -> loadPage(p, 0, replace = true) }
                        }
                        (since["open_requests"] as? JsonArray)?.forEach { el ->
                            if (el is JsonObject) {
                                val rid = (el["id"] as? JsonPrimitive)?.contentOrNull ?: return@forEach
                                val rmethod = (el["method"] as? JsonPrimitive)?.contentOrNull ?: return@forEach
                                addPendingSrq(rid, rmethod, el["params"] as? JsonObject ?: JsonObject(emptyMap()))
                            }
                        }
                    }
                }
                handshake(w)
                profilesSnapshot().forEach { p -> loadPage(p, 0, replace = true) }
                _state.update { it.copy(phase = ConnPhase.LIVE, statusText = "") }
                startPingLoop()
                break
            }
        }
    }

    private fun profilesSnapshot(): List<String> = _state.value.profiles.map { it.name }

    private fun startPingLoop() {
        pingJob?.cancel()
        pingJob = viewModelScope.launch {
            // Official Lco6;->B (classes.dex) heartbeat loop, verbatim:
            //   threshold = max(1, Ldo6->j / Ldo6->i) = 45000/15000 = 3   (@0077-007e)
            //   every Ldo6->i = 15_000 ms tick (Lsh;->g0 @0098):
            //     if (ackFlag F) { F=false; misses=0; continue }          (@00a9-00b7)
            //     misses++
            //     if (misses >= 3) → Len6 path: Exception("No frame from Hermes for N heartbeat intervals")
            //                            (classes2 Len6;->invokeSuspend @0075) → Lco6;->k → reconnect
            //     else send heartbeat frame                                (@00df-014d)
            var misses = 0
            while (true) {
                delay(WsClient.HEARTBEAT_INTERVAL_MS)
                val s = ws ?: return@launch
                if (!s.isOpen) continue
                if (s.takeHeartbeatAck()) { misses = 0; continue }
                misses++
                if (misses >= WsClient.HEARTBEAT_MISS_LIMIT) {
                    // Dead link: close it; onClosed → RECONNECTING + scheduleReconnect.
                    s.close()
                    break
                }
                runCatching { s.sendHeartbeat() }
            }
        }
    }

    private fun teardownWs() {
        pingJob?.cancel(); pingJob = null
        reconnectJob?.cancel(); reconnectJob = null
        ws?.close(); ws = null
    }

    // ---------------------------------------------------------------- events

    private fun handleEvent(type: String, sessionId: String?, payload: JsonElement, seq: Long?) {
        if (sessionId != null && seq != null) sessionSeq.merge(sessionId, seq) { a, b -> if (b > a) b else a }
        handleChatEvent(type, sessionId, payload)
        when (type) {
            "gateway.ready" -> {
                // official Lco6;->y @0x34-0x64 reads payload.session_authority; @025c-025f
                // picks "gateway.ping" (flag true) vs "ping" (false), stored to Lco6;->H @0261.
                // Default from Lco6;-><init> @003d is "gateway.ping". No interval key exists.
                val pl = payload as? JsonObject
                val flag = (pl?.get("supports_gateway_ping") ?: pl?.get("session_authority"))
                    ?.let { (it as? JsonPrimitive)?.booleanOrNull }
                ws?.applyReady(flag)
            }
            "sessions.changed" -> {
                // payload shape not in CONTRACT.md (NOTES): treat as "tree dirty" → silent refresh
                viewModelScope.launch {
                    val sel = _state.value.selectedProfile
                    val targets = if (sel != null) listOf(sel) else profilesSnapshot()
                    targets.forEach { p ->
                        val pages = _state.value.groups[p]?.loadedPages ?: 0
                        if (pages > 0) reloadLoaded(p)
                    }
                }
            }
            "message.start", "tool.start" -> if (sessionId != null) {
                val mapped = liveIdMap[sessionId]
                _state.update { it.copy(working = it.working + sessionId + (mapped ?: "")) }
            }
            "message.complete" -> if (sessionId != null) {
                _state.update { it.copy(working = it.working - sessionId - (liveIdMap[sessionId] ?: "")) }
                val p = profileOfSession(sessionId)
                if (p != null) viewModelScope.launch { loadPage(p, 0, replace = true) }
            }
            "session.reclaimed" -> if (sessionId != null) {
                _state.update { st ->
                    st.copy(
                        pendingSrqs = st.pendingSrqs.filterNot { it.sessionId == sessionId },
                        working = st.working - sessionId - (liveIdMap[sessionId] ?: ""),
                        groups = st.groups.mapValues { (_, g) ->
                            g.copy(sessions = g.sessions.filterNot { s ->
                                s.idCandidates.any { it == sessionId }
                            })
                        },
                    )
                }
            }
            "session.title" -> if (sessionId != null) {
                val title = (payload as? JsonObject)
                    ?.let { (it["title"] as? JsonPrimitive)?.contentOrNull }
                if (!title.isNullOrBlank()) {
                    _state.update { st ->
                        st.copy(groups = st.groups.mapValues { (_, g) ->
                            g.copy(sessions = g.sessions.map { s ->
                                if (s.idCandidates.any { it == sessionId }) s.copy(title = title) else s
                            })
                        })
                    }
                }
            }
            // phase 1 ignores chat-stream events (thinking.delta, message.delta, tool.complete, …)
            else -> Unit
        }
    }

    private fun handleServerRequest(w: WsClient, id: String, method: String, params: JsonObject) {
        // Phase 3: approval/clarify render real cards (shapes VERIFIED, capture tour3/3c);
        // other capabilities still get method-not-found.
        when (method) {
            "approval", "clarify" -> addPendingSrq(id, method, params)
            else -> w.respondErrorToServerRequest(id, "capability '$method' not handled")
        }
    }

    private fun addPendingSrq(id: String, method: String, params: JsonObject) {
        val sid = (params["session_id"] as? JsonPrimitive)?.contentOrNull ?: ""
        val srq = when (method) {
            "approval" -> PendingSrq(
                id = id, method = method, sessionId = sid,
                command = (params["command"] as? JsonPrimitive)?.contentOrNull ?: "",
                description = (params["description"] as? JsonPrimitive)?.contentOrNull ?: "",
                // choices arrive from the SERVER (VERIFIED ["once","session","always","deny"])
                choices = (params["choices"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                    ?: listOf("once", "session", "always", "deny"),
                allowPermanent = (params["allow_permanent"] as? JsonPrimitive)?.booleanOrNull ?: true,
                timeoutS = (params["timeout_s"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 600,
            )
            "clarify" -> PendingSrq(
                id = id, method = method, sessionId = sid,
                questions = (params["questions"] as? JsonArray)?.mapNotNull { q ->
                    if (q !is JsonObject) return@mapNotNull null
                    ClarifyQ(
                        qid = (q["qid"] as? JsonPrimitive)?.contentOrNull ?: "q0",
                        question = (q["question"] as? JsonPrimitive)?.contentOrNull ?: "",
                        choices = (q["choices"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                            ?: emptyList(),
                        multiSelect = (q["multi_select"] as? JsonPrimitive)?.booleanOrNull ?: false,
                    )
                } ?: emptyList(),
            )
            else -> null
        } ?: return
        _state.update { st ->
            if (st.pendingSrqs.any { it.id == srq.id }) st
            else st.copy(pendingSrqs = st.pendingSrqs + srq)
        }
    }

    /** approval answer — VERIFIED: {id:"srq-…", result:{choice:<one of server choices>}} */
    fun answerApproval(srqId: String, choice: String) {
        val w = ws ?: return
        w.respondToServerRequest(srqId, buildJsonObject { put("choice", choice) })
        _state.update { it.copy(pendingSrqs = it.pendingSrqs.filterNot { s -> s.id == srqId }) }
    }

    private val clarifyAnswers = java.util.concurrent.ConcurrentHashMap<String, MutableMap<String, String>>()

    /**
     * clarify answer — VERIFIED two-phase (capture tour3c):
     *  1) clarify.lock{request_id, question_id, answer, profile} (int-id C2S req; claims the
     *     answer across devices — first lock wins)
     *  2) once every question has an answer: {id:"srq-…", result:{answers:{qid:text}}}
     */
    fun answerClarify(srqId: String, qid: String, answer: String) {
        val srq = _state.value.pendingSrqs.firstOrNull { it.id == srqId } ?: return
        val w = ws ?: return
        val profile = profileOfSession(srq.sessionId)
            ?: _state.value.profiles.firstOrNull()?.name ?: "default"
        w.sendRequestNoWait("clarify.lock", buildJsonObject {
            put("request_id", srqId)
            put("question_id", qid)
            put("answer", answer)
            put("profile", profile)
        })
        val answers = clarifyAnswers.getOrPut(srqId) { java.util.concurrent.ConcurrentHashMap() }
        answers[qid] = answer
        if (answers.keys.size >= srq.questions.size) {
            w.respondToServerRequest(srqId, buildJsonObject {
                put("answers", JsonObject(answers.toMap().mapValues { (_, v) -> JsonPrimitive(v) }))
            })
            clarifyAnswers.remove(srqId)
            _state.update { st -> st.copy(pendingSrqs = st.pendingSrqs.filterNot { s -> s.id == srqId }) }
        } else {
            // more questions pending — force recompose for sequential rendering
            _state.update { st -> st.copy(pendingSrqs = st.pendingSrqs.toList()) }
        }
    }

    fun dismissSrq(id: String) {
        _state.update { it.copy(pendingSrqs = it.pendingSrqs.filterNot { s -> s.id == id }) }
    }

    // ── phase 4: model sheet ─────────────────────────────────────────
    // VERIFIED (live probe + server source methods_complete.py): model.options{profile,session_id?}
    // -> {model, provider, providers:[{slug,name,source,models:[str],is_current,...}]};
    // switch = config.set{key:"model", value:"<model> --provider <slug|custom:slug> --session",
    // session_id:<short>} -> {value, warning?}; JSON-RPC errors (e.g. 5001 unauthenticated) surface
    // as failures with human-readable messages.
    fun openModelSheet() {
        val w = ws ?: return
        val chat = _state.value.chat
        _state.update { it.copy(modelSheet = ModelSheetState(open = true, loading = true)) }
        viewModelScope.launch {
            runCatching {
                w.rpc("model.options", buildJsonObject {
                    put("profile", chat.profile)
                    chat.shortId?.let { put("session_id", it) }
                }) as? JsonObject
            }.onSuccess { res ->
                if (res == null) {
                    _state.update { st -> st.copy(modelSheet = st.modelSheet.copy(loading = false, note = "no model data")) }
                    return@onSuccess
                }
                val provs = (res["providers"] as? JsonArray)?.mapNotNull { el ->
                    if (el !is JsonObject) return@mapNotNull null
                    ModelProviderRow(
                        slug = (el["slug"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null,
                        name = (el["name"] as? JsonPrimitive)?.contentOrNull ?: "",
                        source = (el["source"] as? JsonPrimitive)?.contentOrNull ?: "",
                        models = (el["models"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                            ?: emptyList(),
                        isCurrent = (el["is_current"] as? JsonPrimitive)?.booleanOrNull == true,
                    )
                } ?: emptyList()
                _state.update { st ->
                    st.copy(modelSheet = st.modelSheet.copy(
                        loading = false,
                        currentModel = (res["model"] as? JsonPrimitive)?.contentOrNull ?: "",
                        currentProvider = (res["provider"] as? JsonPrimitive)?.contentOrNull ?: "",
                        providers = provs,
                        expanded = provs.firstOrNull { it.isCurrent }?.slug,
                    ))
                }
            }.onFailure { e ->
                _state.update { st -> st.copy(modelSheet = st.modelSheet.copy(loading = false, note = e.message ?: "model.options failed")) }
            }
        }
    }

    fun closeModelSheet() = _state.update { it.copy(modelSheet = ModelSheetState()) }

    fun toggleProvider(slug: String) = _state.update {
        it.copy(modelSheet = it.modelSheet.copy(
            expanded = if (it.modelSheet.expanded == slug) null else slug,
        ))
    }

    fun pickModel(p: ModelProviderRow, model: String, confirmed: Boolean = false) {
        val w = ws ?: return
        val sid = _state.value.chat.shortId ?: return
        _state.update { st -> st.copy(modelSheet = st.modelSheet.copy(busy = true, note = null, pending = null)) }
        viewModelScope.launch {
            val provVal = if (p.source == "hermes") p.slug else "custom:" + p.slug
            runCatching {
                w.rpc("config.set", buildJsonObject {
                    put("key", "model")
                    put("value", "$model --provider $provVal --session")
                    put("session_id", sid)
                    if (confirmed) put("confirm_expensive_model", true)
                }) as? JsonObject
            }.onSuccess { res ->
                val newVal = (res?.get("value") as? JsonPrimitive)?.contentOrNull ?: model
                val warn = (res?.get("warning") as? JsonPrimitive)?.contentOrNull
                val needConfirm = (res?.get("confirm_required") as? JsonPrimitive)?.booleanOrNull == true
                val msg = (res?.get("confirm_message") as? JsonPrimitive)?.contentOrNull
                _state.update { st ->
                    val sheet = when {
                        needConfirm -> st.modelSheet.copy(
                            busy = false, note = msg, pending = p.slug to model,
                        )
                        !warn.isNullOrBlank() -> st.modelSheet.copy(
                            busy = false, note = warn, currentModel = newVal,
                        )
                        else -> st.modelSheet.copy(open = false, busy = false)
                    }
                    st.copy(chat = st.chat.copy(model = newVal), modelSheet = sheet)
                }
            }.onFailure { e ->
                _state.update { st ->
                    st.copy(modelSheet = st.modelSheet.copy(busy = false, note = e.message ?: "model switch failed"))
                }
            }
        }
    }

    fun confirmPendingModel() {
        val ms = _state.value.modelSheet
        val (slug, model) = ms.pending ?: return
        val p = ms.providers.firstOrNull { it.slug == slug } ?: return
        pickModel(p, model, confirmed = true)
    }

    // ---------------------------------------------------------------- sessions

    fun loadPage(profile: String, offset: Int, replace: Boolean) {
        val c = client ?: return
        _state.update { st ->
            val g = st.groups[profile] ?: ProfileGroup(profile)
            st.copy(groups = st.groups + (profile to g.copy(loading = true)))
        }
        viewModelScope.launch {
            c.sessions(profile, offset).onSuccess { page ->
                _state.update { st ->
                    val g = st.groups[profile] ?: ProfileGroup(profile)
                    val merged = if (replace) page.sessions
                        else (g.sessions + page.sessions).distinctBy { it.id }
                    st.copy(groups = st.groups + (profile to g.copy(
                        sessions = merged,
                        total = page.total ?: g.total,
                        loadedPages = if (replace) 1 else g.loadedPages + 1,
                        loading = false,
                    )))
                }
                orderGroupsByRecency() // re-sort once sessions land
            }.onFailure {
                _state.update { st ->
                    val g = st.groups[profile] ?: ProfileGroup(profile)
                    st.copy(groups = st.groups + (profile to g.copy(loading = false)))
                }
            }
        }
    }

    /** re-fetch every page already loaded for a profile (pull-to-refresh) */
    private suspend fun reloadLoaded(profile: String) {
        val c = client ?: return
        val pages = _state.value.groups[profile]?.loadedPages?.coerceAtLeast(1) ?: 1
        var acc: List<SessionRow> = emptyList()
        var total: Int? = null
        for (i in 0 until pages) {
            val page = c.sessions(profile, i * GatewayClient.PAGE_SIZE).getOrNull() ?: break
            acc = (acc + page.sessions).distinctBy { it.id }
            total = page.total ?: total
        }
        _state.update { st ->
            val g = st.groups[profile] ?: ProfileGroup(profile)
            st.copy(groups = st.groups + (profile to g.copy(
                sessions = acc, total = total,
                loadedPages = pages, loading = false,
            )))
        }
    }

    fun refreshAll() {
        val st = _state.value
        if (st.phase == ConnPhase.DISCONNECTED) return
        _state.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            val targets = st.selectedProfile?.let { listOf(it) } ?: profilesSnapshot()
            targets.forEach { reloadLoaded(it) }
            ws?.let { refreshActiveLists(it) }
            _state.update { it.copy(refreshing = false) }
        }
    }

    fun toggleExpand(profile: String) {
        _state.update { st ->
            val g = st.groups[profile] ?: return@update st
            val nowExpanded = !g.expanded
            st.copy(groups = st.groups + (profile to g.copy(expanded = nowExpanded, expandedByUser = true)))
        }
    }

    fun selectProfile(name: String?) = _state.update { it.copy(selectedProfile = name) }
    fun setQuery(q: String) = _state.update { it.copy(query = q) }

    fun newSession(profile: String) {
        val w = ws ?: return
        viewModelScope.launch {
            val res = runCatching {
                // session.create — VERIFIED params + result {session_id:<short>, stored_session_id:<long>}
                w.rpc("session.create", buildJsonObject {
                    put("profile", profile)
                    put("source", "gui")
                    put("close_on_disconnect", false)
                    put("hidden", false)
                    put("follow_profile_config", false)
                })
            }.getOrNull() as? JsonObject
            loadPage(profile, 0, replace = true)
            val short = res?.get("session_id")?.jsonPrimitive?.contentOrNull
            val longId = res?.get("stored_session_id")?.jsonPrimitive?.contentOrNull
            if (longId != null) {
                if (!short.isNullOrBlank()) liveIdMap[short] = longId
                openChat(SessionRow(longId, "New session", null), profile, short)
            }
        }
    }


    // ---------------------------------------------------------------- chat (phase 2)

    /**
     * Open a session in the chat surface.
     * History: GET /api/sessions/<long id>/messages?limit=24&order=latest (VERIFIED live 2026-11-24:
     *   {session_id, profile, messages:[{id:int, role, content, tool_call_id, tool_calls:[...]}], pagination})
     * Subscribe: session.resume with the LONG id (VERIFIED capture: session_id:"20261015_193455_75aad0",
     *   defer_history:true, omit_messages:true, close_on_disconnect:false) —
     *   result carries session_id (SHORT live id), session_key, running, status, info{model,title,...}.
     */
    fun openChat(row: SessionRow, profile: String, shortId: String? = null) {
        _state.update {
            it.copy(chat = ChatState(
                open = true, longId = row.id, profile = profile,
                title = row.title, shortId = shortId, loading = true,
            ))
        }
        val c = client ?: return
        viewModelScope.launch {
            c.messages(row.id).onSuccess { msgs ->
                _state.update { st -> st.copy(chat = st.chat.copy(messages = msgs, loading = false)) }
            }.onFailure { e ->
                // a brand-new session has no persisted history yet -> 404 is normal, not an error
                val is404 = e.message?.contains("404") == true
                _state.update { st -> st.copy(chat = st.chat.copy(
                    loading = false,
                    error = if (is404 && st.chat.messages.isEmpty()) null else "history: ${e.message}",
                )) }
            }
            val w = ws ?: run {
                _state.update { st -> st.copy(chat = st.chat.copy(error = "not connected")) }
                return@launch
            }
            runCatching {
                w.rpc("session.resume", buildJsonObject {
                    put("session_id", row.id)
                    put("profile", profile)
                    put("defer_history", true)
                    put("omit_messages", true)
                    put("close_on_disconnect", false)
                }) as? JsonObject
            }.onSuccess { res ->
                if (res == null) return@onSuccess
                val short = res["session_id"]?.jsonPrimitive?.contentOrNull
                val info = res["info"] as? JsonObject
                if (!short.isNullOrBlank()) liveIdMap[short] = row.id
                _state.update { st -> st.copy(chat = st.chat.copy(
                    shortId = short ?: st.chat.shortId,
                    model = (info?.get("model") as? JsonPrimitive)?.contentOrNull ?: st.chat.model,
                    title = (info?.get("title") as? JsonPrimitive)?.contentOrNull ?: st.chat.title,
                    running = (res["running"] as? JsonPrimitive)?.booleanOrNull == true || st.chat.running,
                )) }
                // Phase 3: re-arm unanswered approval/clarify cards. VERIFIED (server source
                // methods_session.py:2288 + server_requests.py): session.events.since returns
                // open_requests=[{id,method,params}] for still-unanswered srqs; approval/clarify
                // are replayable; params carry session_id (short) + the original fields.
                if (!short.isNullOrBlank()) runCatching {
                    val since = w.rpc("session.events.since", buildJsonObject {
                        put("session_id", short)
                        put("last_seen", 0L)
                    }) as? JsonObject
                    (since?.get("open_requests") as? JsonArray)?.forEach { el ->
                        if (el is JsonObject) {
                            val rid = (el["id"] as? JsonPrimitive)?.contentOrNull ?: return@forEach
                            val rmethod = (el["method"] as? JsonPrimitive)?.contentOrNull ?: return@forEach
                            addPendingSrq(rid, rmethod, el["params"] as? JsonObject ?: JsonObject(emptyMap()))
                        }
                    }
                }
            }.onFailure { e ->
                _state.update { st -> st.copy(chat = st.chat.copy(error = "resume: ${e.message}")) }
            }
        }
    }

    fun closeChat() = _state.update { it.copy(chat = ChatState()) }

    /** prompt.submit — VERIFIED capture: session_id is the SHORT live id, surface "hermes-android". */
    fun sendPrompt(text: String) {
        val chat = _state.value.chat
        val w = ws ?: return
        val short = chat.shortId ?: run {
            _state.update { it.copy(chat = it.chat.copy(error = "session not resumed yet")) }
            return
        }
        if (text.isBlank() || chat.sending) return
        _state.update { st -> st.copy(chat = st.chat.copy(
            sending = true,
            messages = st.chat.messages + ChatMsg("u${System.currentTimeMillis()}", "user", text),
        )) }
        viewModelScope.launch {
            runCatching {
                w.rpc("prompt.submit", buildJsonObject {
                    put("session_id", short)
                    put("profile", chat.profile)
                    put("text", text)
                    put("surface", "hermes-android")
                })
            }.onSuccess {
                _state.update { it.copy(chat = it.chat.copy(sending = false)) }
            }.onFailure { e ->
                _state.update { it.copy(chat = it.chat.copy(sending = false, error = e.message)) }
            }
        }
    }

    /** Route turn events into the open chat. Event shapes VERIFIED (capture + live). */
    private fun handleChatEvent(type: String, sid: String?, payload: JsonElement) {
        val chat = _state.value.chat
        if (!chat.open) return
        val mine = sid != null && (sid == chat.shortId || sid == chat.longId || liveIdMap[sid] == chat.longId)
        if (!mine) return
        val pl = payload as? JsonObject
        when (type) {
            "message.start" -> _state.update { st ->
                val turn = (pl?.get("turn_id") as? JsonPrimitive)?.contentOrNull ?: "${System.nanoTime()}"
                st.copy(chat = st.chat.copy(
                    streaming = ChatMsg("s${turn}", "assistant", "", streaming = true),
                    spinner = "", running = true, error = null,
                ))
            }
            "thinking.delta" -> {
                val t = (pl?.get("text") as? JsonPrimitive)?.contentOrNull ?: ""
                _state.update { st -> st.copy(chat = st.chat.copy(spinner = t)) }
            }
            "reasoning.delta" -> {
                val t = (pl?.get("text") as? JsonPrimitive)?.contentOrNull ?: ""
                _state.update { st ->
                    val s = st.chat.streaming ?: ChatMsg("s${System.nanoTime()}", "assistant", "", streaming = true)
                    st.copy(chat = st.chat.copy(streaming = s.copy(reasoning = s.reasoning + t)))
                }
            }
            "tool.start" -> {
                val card = ToolCard(
                    toolId = (pl?.get("tool_id") as? JsonPrimitive)?.contentOrNull ?: "t${System.nanoTime()}",
                    name = (pl?.get("name") as? JsonPrimitive)?.contentOrNull ?: "?",
                    context = (pl?.get("context") as? JsonPrimitive)?.contentOrNull ?: "",
                )
                _state.update { st ->
                    val s = st.chat.streaming ?: ChatMsg("s${System.nanoTime()}", "assistant", "", streaming = true)
                    st.copy(chat = st.chat.copy(streaming = s.copy(tools = s.tools + card)))
                }
            }
            "tool.complete" -> {
                val tid = (pl?.get("tool_id") as? JsonPrimitive)?.contentOrNull
                val dur = (pl?.get("duration_s") as? JsonPrimitive)?.content?.toDoubleOrNull()
                val exit = (pl?.get("result") as? JsonObject)?.get("exit_code")
                val ok = when {
                    exit is JsonPrimitive && exit.content == "null" -> true
                    exit is JsonPrimitive -> exit.content.toIntOrNull() == 0
                    else -> true
                }
                _state.update { st ->
                    val s = st.chat.streaming ?: return@update st
                    st.copy(chat = st.chat.copy(streaming = s.copy(tools = s.tools.map {
                        if (it.toolId == tid) it.copy(done = true, durationS = dur, ok = ok) else it
                    })))
                }
            }
            "message.delta" -> {
                val t = (pl?.get("text") as? JsonPrimitive)?.contentOrNull ?: ""
                val offset = (pl?.get("offset") as? JsonPrimitive)?.content?.toLongOrNull()
                _state.update { st ->
                    val s = st.chat.streaming ?: ChatMsg("s${System.nanoTime()}", "assistant", "", streaming = true)
                    val cur = s.content
                    val merged = when {
                        offset != null && offset != cur.length.toLong() -> cur // stale/dup/gap — trust nothing
                        else -> cur + t
                    }
                    st.copy(chat = st.chat.copy(streaming = s.copy(content = merged)))
                }
            }
            "message.complete" -> {
                val finalText = (pl?.get("text") as? JsonPrimitive)?.contentOrNull
                val usage = pl?.get("usage") as? JsonObject
                val line = usage?.let { u ->
                    val m = (u["model"] as? JsonPrimitive)?.contentOrNull ?: ""
                    val tot = (u["total"] as? JsonPrimitive)?.contentOrNull
                    when {
                        m.isNotEmpty() && tot != null -> "${m} · ${tot} tok"
                        m.isNotEmpty() -> m
                        else -> null
                    }
                }
                _state.update { st ->
                    val s = st.chat.streaming
                    val done = (s ?: ChatMsg("s${System.nanoTime()}", "assistant", "")).copy(
                        streaming = false,
                        content = finalText?.takeIf { it.isNotEmpty() } ?: s?.content ?: "",
                        usageLine = line,
                    )
                    st.copy(chat = st.chat.copy(
                        streaming = null, spinner = "", running = false,
                        messages = st.chat.messages + done,
                    ))
                }
            }
            "session.title" -> {
                val t = (pl?.get("title") as? JsonPrimitive)?.contentOrNull
                if (!t.isNullOrBlank()) _state.update { st -> st.copy(chat = st.chat.copy(title = t)) }
            }
            "session.reclaimed" -> _state.update { st ->
                st.copy(chat = st.chat.copy(running = false, error = "session reclaimed"))
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun profileOfSession(sessionId: String): String? {
        val st = _state.value
        st.groups.entries.forEach { (p, g) ->
            if (g.sessions.any { s -> s.idCandidates.any { matchesId(it, sessionId) } }) return p
        }
        return st.profiles.firstOrNull()?.name
    }

    fun isWorking(row: SessionRow): Boolean =
        _state.value.working.any { wid -> row.idCandidates.any { matchesId(it, wid) } }

    // Official binary has NO prefix/suffix id rule — matching is exact equality only
    // (Llv7;->z used for id comparisons); the short↔long bridge is the session_key
    // field (live-verified 2026-11-24, liveIdMap above).
    private fun matchesId(a: String, b: String): Boolean = a == b

    private fun loadAvatars(profiles: List<String>) {
        val w = ws ?: return
        viewModelScope.launch(Dispatchers.IO) {
            for (p in profiles.take(8)) {
                runCatching {
                    // profiles.get_asset — avatar png base64 (CONTRACT.md; result shape guessed)
                    val res = w.rpc("profiles.get_asset", buildJsonObject {
                        put("name", p) // gateway _resolve_profile wants "name", not "profile"
                        put("asset", "avatar")
                    })
                    val b64 = when (res) {
                        is JsonPrimitive -> res.contentOrNull
                        is JsonObject -> listOf("data", "avatar", "image", "base64", "png")
                            .firstNotNullOfOrNull { (res[it] as? JsonPrimitive)?.contentOrNull }
                        else -> null
                    }
                    val bytes = b64?.let {
                        runCatching {
                            java.util.Base64.getDecoder().decode(it.substringAfterLast(','))
                        }.getOrNull()
                    } ?: return@runCatching
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@runCatching
                    withContext(Dispatchers.Main) {
                        _state.update { it.copy(avatars = it.avatars + (p to bmp.asImageBitmap())) }
                    }
                }.onFailure { e ->
                    android.util.Log.d("AvatarFetch", "profile=$p failed: ${e.message}")
                }
            }
        }
    }

    private fun parseNameList(res: JsonElement): List<String> {
        val arr = when (res) {
            is JsonArray -> res
            is JsonObject -> (res["profiles"] ?: res["names"] ?: res["items"]) as? JsonArray
            else -> null
        } ?: return emptyList()
        return arr.mapNotNull { el ->
            when (el) {
                is JsonPrimitive -> el.contentOrNull?.takeIf { it.isNotBlank() }
                is JsonObject -> listOf("name", "profile", "id").firstNotNullOfOrNull {
                    (el[it] as? JsonPrimitive)?.contentOrNull
                }
                else -> null
            }
        }
    }

}

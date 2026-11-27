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
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.add
import kotlinx.serialization.json.putJsonArray
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
/** The official Chat Settings sheet's "Show in this chat" toggles (verified on official
 *  2026-11-24). Persisted app-wide in hm_chat prefs. Tool calls + Fold finished turns gate
 *  rendering today; Device actions/Link previews/Thinking persist state until V4 renders them. */
data class ChatPrefs(
    val showToolCalls: Boolean = true,
    val deviceActions: Boolean = true,
    val showThinking: Boolean = true,
    val foldFinishedTurns: Boolean = true,
    val linkPreviews: Boolean = true,
    /** official vc49 6th toggle "Memory updates" (verified on official 2026-11-26):
     *  "Shows a note when Hermes updates its memory or skills after a turn." */
    val memoryUpdates: Boolean = true,
)

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
    /** the roster row this chat was opened from (archive/delete menu actions) */
    val row: SessionRow? = null,
    val liveMode: Boolean = true,
    /** session.resume info.cwd — shown as "Workspace" in the official Session Settings sheet */
    val currentCwd: String? = null,
    val settingsSheet: Boolean = false,
    val renameTarget: Boolean = false,   // rename dialog for THIS chat's title
    // ---- vc49 parity pass (2026-11-26) ----
    /** epoch ms of the current turn's message.start (status-bar elapsed timer) */
    val workingSinceMs: Long? = null,
    /** prompts collected while a turn runs (client-side queue, official ChatQueuedPrompt) */
    val queued: List<com.gravol.hermesclone.model.QueuedPrompt> = emptyList(),
    /** composer text pushed back for Edit (official user-pill Edit); consumed by ChatComposer */
    val composerPrefill: String? = null,
    /** message whose reaction sheet is open */
    val reactionTargetKey: String? = null,
    /** bumped each time the reconnect resync replaces the message list — ChatScreen
     *  uses it to fully reset the LazyColumn (a replace under a pending scrollToItem wedges
     *  the scroll state mid-list: VERIFIED 2026-11-27) */
    val resyncTick: Int = 0,
    /** "Regenerate this reply?" confirm */
    val regenerateDialog: Boolean = false,
    /** session.context_breakdown (VERIFIED live 2026-11-26), fetched when Session Settings opens */
    val context: com.gravol.hermesclone.model.ContextUsage? = null,
    val contextLoading: Boolean = false,
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
    val workingSince: Map<String, Long> = emptyMap(), // vc49 live-row elapsed timer
    val selectedProfile: String? = null, // null = All
    val query: String = "",
    val refreshing: Boolean = false,
    val avatars: Map<String, ImageBitmap> = emptyMap(),
    val profileOrder: List<String> = emptyList(),
    val chat: ChatState = ChatState(),
    val chatPrefs: ChatPrefs = ChatPrefs(),
    /** unanswered server->client requests (approval/clarify cards), phase 3 */
    val pendingSrqs: List<PendingSrq> = emptyList(),
    /** phase 4: model picker sheet */
    val modelSheet: ModelSheetState = ModelSheetState(),
    /** phase 7 (menu parity): local pins (gateway has no pin endpoint — official stores on-device) */
    val pinnedIds: Set<String> = emptySet(),
    val archived: List<SessionRow> = emptyList(),
    val archivedOpen: Boolean = false,
    val routinesOpen: Boolean = false,
    val routines: List<com.gravol.hermesclone.model.CronJob> = emptyList(),
    val routinesLoading: Boolean = false,
    val routinesNote: String? = null, // honest banner for unsupported actions/states
    // artifacts page (chat menu): client-side transcript scan, no server endpoint
    val artifactsOpen: Boolean = false,
    val artifacts: List<com.gravol.hermesclone.util.Artifact> = emptyList(),
    val artifactThumbs: Map<String, android.graphics.Bitmap> = emptyMap(),
    val artifactsNote: String? = null, // honest banner (All Files not ported yet)
    val renameTarget: Pair<SessionRow, String>? = null, // row to profile
    val toast: String? = null,
    val deleteDialog: Boolean = false,
    // phase 8: filter menu (options transcribed from official 2026-11-24)
    val filterView: Int = 0,        // 0 by agent, 1 by project, 2 sessions only, 3 agents only
    val filterSort: Int = 0,        // 0 recent activity, 1 name
    val filterRecentDays: Int = 1,  // group auto-expand window (official "Recent sessions")
    val filterShow: Int = 0,        // 0 all, 1 unread, 2 working, 3 pinned
    val filterAgent: String? = null,
    val filterProject: String? = null,
    val filterSheet: String? = null, // open submenu title
    // Include toggles (official rows): Routine runs ≈ roster source=cron, Kanban workers = kanban.
    // Group chats / Hidden agents need semantics we haven't observed — stay inert.
    // official defaults (verified on official app 2026-11-24): routine + kanban HIDDEN,
    // group chats shown, hidden agents off. Subtitle counts come from the server.
    val includeGroupChats: Boolean = true,
    val includeHiddenAgents: Boolean = false,
    val includeRoutine: Boolean = false,
    val includeKanban: Boolean = false,
    val sourceCounts: Map<String, Int> = emptyMap(), // "cron"/"kanban" -> total sessions
    // vc49: "N more sessions" opens a dedicated page (official, verified 2026-11-26)
    val moreSessions: MoreSessionsState = MoreSessionsState(),
)

/** Dedicated "more sessions" page state (vc49 item 5). The page has its own filter
 *  sheet (Show / Project / Include w/ per-source counts / Group by project /
 *  Select sessions) and a select mode with bulk archive. */
data class MoreSessionsState(
    val open: Boolean = false,
    val profile: String = "",
    val sessions: List<SessionRow> = emptyList(),
    val total: Int? = null,
    val loadedPages: Int = 0,
    val loading: Boolean = false,
    val filterSheet: String? = null,   // open submenu title in the page's own sheet
    val filterShow: Int = 0,           // All | Unread | Working | Pinned
    val filterProject: String? = null,
    val groupByProject: Boolean = false,
    val selectMode: Boolean = false,
    val selected: Set<String> = emptySet(), // row ids
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("gateways", Application.MODE_PRIVATE)
    private val localPrefs = app.getSharedPreferences("hm_local", Application.MODE_PRIVATE)
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

    private val chatPrefsStore = app.getSharedPreferences("hm_chat", Application.MODE_PRIVATE)

    private fun loadChatPrefs() {
        _state.update { it.copy(chatPrefs = ChatPrefs(
            showToolCalls = chatPrefsStore.getBoolean("show_tool_calls", true),
            deviceActions = chatPrefsStore.getBoolean("device_actions", true),
            showThinking = chatPrefsStore.getBoolean("show_thinking", true),
            foldFinishedTurns = chatPrefsStore.getBoolean("fold_turns", true),
            linkPreviews = chatPrefsStore.getBoolean("link_previews", true),
            memoryUpdates = chatPrefsStore.getBoolean("memory_updates", true),
        )) }
    }

    fun setChatPref(key: String, value: Boolean) {
        val k = when (key) {
            "tool_calls" -> "show_tool_calls"; "device_actions" -> "device_actions"
            "thinking" -> "show_thinking"; "fold_turns" -> "fold_turns"
            "memory_updates" -> "memory_updates"
            else -> "link_previews"
        }
        chatPrefsStore.edit().putBoolean(k, value).apply()
        _state.update { st ->
            val p = st.chatPrefs.copy(
                showToolCalls = if (key == "tool_calls") value else st.chatPrefs.showToolCalls,
                deviceActions = if (key == "device_actions") value else st.chatPrefs.deviceActions,
                showThinking = if (key == "thinking") value else st.chatPrefs.showThinking,
                foldFinishedTurns = if (key == "fold_turns") value else st.chatPrefs.foldFinishedTurns,
                linkPreviews = if (key == "link_previews") value else st.chatPrefs.linkPreviews,
                memoryUpdates = if (key == "memory_updates") value else st.chatPrefs.memoryUpdates,
            )
            st.copy(chatPrefs = p)
        }
    }

    fun openChatSettings(open: Boolean) {
        _state.update { it.copy(chat = it.chat.copy(settingsSheet = open)) }
        if (open) fetchContextBreakdown()
    }
    fun openChatRename(open: Boolean) = _state.update { it.copy(chat = it.chat.copy(renameTarget = open)) }

    /** session.context_breakdown — VERIFIED live 2026-11-26 against the real gateway:
     *  {session_id:<long REST id works>, profile} ->
     *  {categories:[{color,id,label,tokens}], estimated_total, context_max,
     *   context_percent, context_used, context_source, context_estimated, model, prompt_cache} */
    private fun fetchContextBreakdown() {
        val chat = _state.value.chat
        val w = ws ?: return
        if (chat.longId.isEmpty()) return
        _state.update { it.copy(chat = it.chat.copy(contextLoading = true)) }
        viewModelScope.launch {
            runCatching {
                w.rpc("session.context_breakdown", buildJsonObject {
                    put("session_id", chat.longId)
                    put("profile", chat.profile)
                }) as? JsonObject
            }.onSuccess { res ->
                if (res == null) return@onSuccess
                val cats = (res["categories"] as? JsonArray)?.mapNotNull { el ->
                    if (el !is JsonObject) return@mapNotNull null
                    com.gravol.hermesclone.model.ContextCategory(
                        id = (el["id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null,
                        label = (el["label"] as? JsonPrimitive)?.contentOrNull ?: "",
                        tokens = (el["tokens"] as? JsonPrimitive)?.intOrNull ?: 0,
                        color = (el["color"] as? JsonPrimitive)?.contentOrNull ?: "",
                    )
                } ?: emptyList()
                _state.update { st ->
                    st.copy(chat = st.chat.copy(
                        contextLoading = false,
                        context = com.gravol.hermesclone.model.ContextUsage(
                            categories = cats,
                            used = (res["context_used"] as? JsonPrimitive)?.intOrNull ?: 0,
                            max = (res["context_max"] as? JsonPrimitive)?.intOrNull ?: 0,
                            percent = (res["context_percent"] as? JsonPrimitive)?.intOrNull ?: 0,
                        ),
                    ))
                }
            }.onFailure {
                _state.update { st -> st.copy(chat = st.chat.copy(contextLoading = false)) }
            }
        }
    }

    init {
        _state.update { it.copy(gateways = loadGateways()) }
        loadPins()
        loadFilters()
        loadChatPrefs()
        // Auto-reconnect to the last-used gateway after process death (system kill or
        // force-stop). Explicit disconnect() clears the marker so we don't fight the user.
        val activeId = localPrefs.getString("active_gw", null)
        _state.value.gateways.firstOrNull { it.id == activeId }?.let { connect(it) }
    }

    private fun loadFilters() {
        runCatching {
            // one-time migration: pre-parity installs saved routine/kanban as included;
            // official defaults hide them (verified on official app 2026-11-24)
            if (localPrefs.getInt("filters_v", 0) < 1) {
                localPrefs.edit().putInt("filters_v", 1).apply()
                _state.update { it.copy(includeRoutine = false, includeKanban = false) }
                saveFilters()
            }
            val o = json.parseToJsonElement(localPrefs.getString("filters", "") ?: "{}")
                .let { it as? kotlinx.serialization.json.JsonObject ?: return@runCatching }
            fun i(k: String, d: Int) = (o[k] as? JsonPrimitive)?.intOrNull ?: d
            _state.update {
                it.copy(
                    filterView = i("view", it.filterView),
                    filterSort = i("sort", it.filterSort),
                    filterRecentDays = i("recent", it.filterRecentDays),
                    filterShow = i("show", it.filterShow),
                    filterAgent = (o["agent"] as? JsonPrimitive)?.contentOrNull?.takeIf { s -> s.isNotEmpty() },
                    filterProject = (o["project"] as? JsonPrimitive)?.contentOrNull?.takeIf { s -> s.isNotEmpty() },
                    includeRoutine = (o["routine"] as? JsonPrimitive)?.booleanOrNull ?: it.includeRoutine,
                    includeKanban = (o["kanban"] as? JsonPrimitive)?.booleanOrNull ?: it.includeKanban,
                    includeGroupChats = (o["groupchats"] as? JsonPrimitive)?.booleanOrNull ?: it.includeGroupChats,
                    includeHiddenAgents = (o["hiddenagents"] as? JsonPrimitive)?.booleanOrNull ?: it.includeHiddenAgents,
                )
            }
        }
    }

    private fun saveFilters() {
        val st = _state.value
        localPrefs.edit().putString("filters", buildJsonObject {
            put("view", st.filterView); put("sort", st.filterSort)
            put("recent", st.filterRecentDays); put("show", st.filterShow)
            put("agent", st.filterAgent ?: ""); put("project", st.filterProject ?: "")
            put("routine", st.includeRoutine); put("kanban", st.includeKanban)
            put("groupchats", st.includeGroupChats); put("hiddenagents", st.includeHiddenAgents)
        }.toString()).apply()
    }

    fun openFilterSheet(name: String) = _state.update { it.copy(filterSheet = name) }
    fun closeFilterSheet() = _state.update { it.copy(filterSheet = null) }
    fun setFilterView(v: Int) { _state.update { it.copy(filterView = v, filterSheet = null) }; saveFilters() }
    fun setFilterSort(v: Int) { _state.update { it.copy(filterSort = v, filterSheet = null) }; saveFilters() }
    fun setFilterRecent(v: Int) {
        _state.update { it.copy(filterRecentDays = v, filterSheet = null) }; saveFilters(); orderGroupsByRecency()
    }
    fun setFilterShow(v: Int) { _state.update { it.copy(filterShow = v, filterSheet = null) }; saveFilters() }
    fun setFilterAgent(a: String?) { _state.update { it.copy(filterAgent = a, filterSheet = null) }; saveFilters() }
    fun setFilterProject(p: String?) { _state.update { it.copy(filterProject = p, filterSheet = null) }; saveFilters() }
    fun toggleIncludeRoutine() { _state.update { it.copy(includeRoutine = !it.includeRoutine) }; saveFilters(); reloadListsForExclusionChange() }
    fun toggleIncludeKanban() { _state.update { it.copy(includeKanban = !it.includeKanban) }; saveFilters(); reloadListsForExclusionChange() }

    /** the server-side exclusion set changed — re-fetch all profiles' loaded pages */
    private fun reloadListsForExclusionChange() {
        viewModelScope.launch { _state.value.groups.keys.toList().forEach { reloadLoaded(it) } }
    }
    // display-only: no server flag observed for either yet (stays inert like the official rows
    // we cannot back, but the switch state persists like the official's)
    fun toggleIncludeGroupChats() { _state.update { it.copy(includeGroupChats = !it.includeGroupChats) }; saveFilters() }
    fun toggleIncludeHiddenAgents() { _state.update { it.copy(includeHiddenAgents = !it.includeHiddenAgents) }; saveFilters() }

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
        localPrefs.edit().putString("active_gw", gw.id).apply()
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
            // Include-sheet subtitles: official shows server-wide per-source totals
            // (verified: "738 sessions"/"28 sessions" = sum over profiles)
            viewModelScope.launch {
                val cron = profiles.sumOf { c.sourceTotal("cron", it.name) }
                val kan = profiles.sumOf { c.sourceTotal("kanban", it.name) }
                _state.update { it.copy(sourceCounts = mapOf("cron" to cron, "kanban" to kan)) }
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
        localPrefs.edit().remove("active_gw").apply()
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
                    else g.copy(expanded = name in liveProfiles || (newest > 0 && nowSec - newest < st.filterRecentDays * 86400L))
                },
            )
        }
    }

    private suspend fun refreshActiveLists(w: WsClient) {
        val profiles = _state.value.profiles.map { it.name }
        val working = mutableSetOf<String>()
        val sinceNew = mutableMapOf<String, Long>()
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
                        val sa = (o["started_at"] as? JsonPrimitive)?.content?.toDoubleOrNull()
                        val ms = if (sa != null && sa > 0) (sa * 1000).toLong() else 0L
                        if (ms > 0) { sinceNew[shortId] = ms; if (!longId.isNullOrBlank()) sinceNew[longId] = ms }
                    }
                }
            }
        }
        _state.update { st ->
            val keep = st.workingSince.filterKeys { it in working }
            val merged = keep + sinceNew + working.filterNot { it in keep || it in sinceNew }
                .associateWith { System.currentTimeMillis() }
            st.copy(working = working, workingSince = merged)
        }
    }

    private fun scheduleReconnect(c: GatewayClient, gw: SavedGateway) {
        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            var backoff = 1_000L
            var ticketFails = 0
            while (!manualDisconnect) {
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(30_000L) // backoff cadence: estimated, see NOTES
                // Two consecutive ticket failures means our session cookie is dead (revoked or
                // expired) — re-login with the saved credentials before the next attempt.
                if (ticketFails >= 2) {
                    ticketFails = 0
                    c.login(gw.username, gw.password).getOrNull() ?: continue
                }
                val ticketRes = c.wsTicket()
                if (ticketRes.isFailure) { ticketFails++; continue }
                val ticket = ticketRes.getOrThrow()
                ticketFails = 0
                val w = WsClient(
                    http,
                    "${c.baseUrl.replaceFirst("http", "ws")}/api/ws?ticket=${java.net.URLEncoder.encode(ticket.first, "UTF-8")}",
                    "${WsClient.SUBPROTOCOL_BASE}, ${WsClient.SUBPROTOCOL_TICKET_PREFIX}${ticket.second ?: ticket.first}",
                )
                w.onEvent = { type, sessionId, payload, seq -> handleEvent(type, sessionId, payload, seq) }
                w.onServerRequest = { id, method, params -> handleServerRequest(w, id, method, params) }
                w.onClosed = { _, _ ->
                    if (!manualDisconnect) {
                        _state.update {
                            it.copy(phase = ConnPhase.RECONNECTING, statusText = "Reconnecting…")
                        }
                        // The loop below has already exited (it breaks on success) — this
                        // socket dying needs a FRESH loop, else the app is disconnected
                        // forever (VERIFIED: stuck spinner after second drop, 2026-11-27).
                        scheduleReconnect(c, gw)
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
                // Re-sync the OPEN chat after a gap (Jeff 2026-11-27: a dropped WS that
                // missed message.complete left the transcript stale + the "compacting..."
                // spinner stuck forever — reconnect replayed open_requests + home list
                // only, never the chat itself). Refetch messages, then trust the server's
                // running flag to clear or keep the working state.
                val reSync = _state.value.chat
                if (reSync.open && reSync.longId.isNotBlank()) {
                    android.util.Log.d("HMWATCH", "resync: fetch1 start long=${reSync.longId}")
                    c.messages(reSync.longId)
                        .onSuccess { msgs ->
                            android.util.Log.d("HMWATCH", "resync: fetch1 ok n=${msgs.size} last=${msgs.lastOrNull()?.content?.take(30)}")
                            _state.update { st -> st.copy(chat = st.chat.copy(messages = msgs, resyncTick = st.chat.resyncTick + 1)) }
                        }
                        .onFailure { e ->
                            android.util.Log.d("HMWATCH", "resync: fetch1 FAIL ${e.message}")
                        }
                    // Belt-and-braces: the server read path has been observed briefly behind
                    // right after a turn lands (2026-11-27) — one more pass after a beat.
                    viewModelScope.launch {
                        delay(2_500)
                        if (_state.value.chat.longId == reSync.longId) {
                            c.messages(reSync.longId)
                                .onSuccess { msgs ->
                                    android.util.Log.d("HMWATCH", "resync: fetch2 ok n=${msgs.size} last=${msgs.lastOrNull()?.content?.take(30)}")
                                    _state.update { st -> st.copy(chat = st.chat.copy(messages = msgs, resyncTick = st.chat.resyncTick + 1)) }
                                }
                                .onFailure { e ->
                                    android.util.Log.d("HMWATCH", "resync: fetch2 FAIL ${e.message}")
                                }
                        }
                    }
                    runCatching {
                        (w.rpc("session.resume", buildJsonObject {
                            put("session_id", reSync.longId)
                            put("profile", reSync.profile)
                            put("defer_history", true)
                            put("omit_messages", true)
                            put("close_on_disconnect", false
                            )
                        }) as? JsonObject)?.let { res ->
                            val short = res["session_id"]?.jsonPrimitive?.contentOrNull
                            if (!short.isNullOrBlank()) liveIdMap[short] = reSync.longId
                            val isRunning = (res["running"] as? JsonPrimitive)?.booleanOrNull == true
                            android.util.Log.d("HMWATCH", "resync: resume running=$isRunning short=$short")
                            _state.update { st -> st.copy(chat = st.chat.copy(
                                shortId = short ?: st.chat.shortId,
                                running = isRunning,
                                streaming = if (isRunning) st.chat.streaming else null,
                                spinner = if (isRunning) st.chat.spinner else "",
                                workingSinceMs = if (isRunning)
                                    (st.chat.workingSinceMs ?: System.currentTimeMillis()) else null,
                            )) }
                        }
                    }
                }
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
                    // Dead link: HARD kill; graceful close(1000) on a dead socket never
                    // completes its handshake → onClosed never fires → no reconnect.
                    // cancel() forces onFailure → onClosed(-1) → scheduleReconnect.
                    s.cancel()
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
                _state.update { it.copy(
                    working = it.working + sessionId + (mapped ?: ""),
                    workingSince = it.workingSince + (sessionId to (it.workingSince[sessionId] ?: System.currentTimeMillis()))
                        + (mapped?.let { m -> mapOf(m to (it.workingSince[m] ?: System.currentTimeMillis())) } ?: emptyMap()),
                ) }
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

    private fun excludeSourcesNow(): List<String> {
        val s = _state.value
        return buildList {
            if (!s.includeRoutine) add("cron")
            if (!s.includeKanban) add("kanban")
        }
    }

    fun loadPage(profile: String, offset: Int, replace: Boolean) {
        val c = client ?: return
        _state.update { st ->
            val g = st.groups[profile] ?: ProfileGroup(profile)
            st.copy(groups = st.groups + (profile to g.copy(loading = true)))
        }
        viewModelScope.launch {
            c.sessions(profile, offset, excludeSourcesNow()).onSuccess { page ->
                _state.update { st ->
                    val g = st.groups[profile] ?: ProfileGroup(profile)
                    val merged = if (replace) page.sessions
                        else (g.sessions + page.sessions).distinctBy { it.id }
                    // official: archived rows are absent from the list; an "Archived" entry
                    // at the bottom opens a dedicated screen (verified vs official 2026-11-24)
                    // REST rows don't reliably carry the archived flag, and a silent roster
                    // refresh would otherwise resurrect an archived row — the local archived
                    // list is authoritative for rows this device has archived/unarchived.
                    val knownArch = st.archived.filter { it.profile == profile }
                    val isArch = { r: SessionRow ->
                        r.archived || knownArch.any { a -> a.idCandidates.any { c -> r.idCandidates.contains(c) } }
                    }
                    val (arch, live) = merged.partition { isArch(it) }
                    val others = st.archived.filter { it.profile != profile }
                    st.copy(
                        groups = st.groups + (profile to g.copy(
                            sessions = live,
                            total = page.total ?: g.total,
                            loadedPages = if (replace) 1 else g.loadedPages + 1,
                            loading = false,
                        )),
                        archived = (others + arch).sortedByDescending { it.updatedAt ?: 0L },
                    )
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
            val page = c.sessions(profile, i * GatewayClient.PAGE_SIZE, excludeSourcesNow()).getOrNull() ?: break
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
        // live-home fix: silent sessions.changed reloads must re-sort groups by recency
        // too (loadPage did it; reloadLoaded did not) - otherwise a background profile
        // updates its rows but the group stays stuck in its old position.
        orderGroupsByRecency()
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

    // ---- phase 7 menu parity (behaviors verified vs official 2026-11-24) ----

    /** Pin is on-device only (official: row jumps to group top + yellow pin icon). */
    fun togglePin(row: SessionRow) {
        val pinned = _state.value.pinnedIds
        val isPinned = row.idCandidates.any { it in pinned }
        val next = if (isPinned) pinned - row.idCandidates
                   else pinned + row.idCandidates
        _state.update { it.copy(pinnedIds = next, toast = if (isPinned) "Conversation unpinned" else "Conversation pinned") }
        localPrefs.edit().putString("pinned", next.joinToString(",")).apply()
        autoClearToast()
    }

    private fun loadPins() {
        val raw = localPrefs.getString("pinned", "") ?: ""
        if (raw.isNotBlank()) _state.update { it.copy(pinnedIds = raw.split(",").filter { it.isNotBlank() }.toSet()) }
    }

    fun setRenameTarget(row: SessionRow) = _state.update { it.copy(renameTarget = row to row.profile) }
    fun clearRenameTarget() = _state.update { it.copy(renameTarget = null) }

    /** rename from the Chat Settings sheet — updates chat header + roster row via RPC */
    fun renameChat(title: String) {
        val chat = _state.value.chat
        val t = title.trim()
        _state.update { it.copy(chat = it.chat.copy(renameTarget = false)) }
        if (t.isNotEmpty() && t != chat.title) {
            _state.update { it.copy(chat = it.chat.copy(title = t)) }
            chat.row?.let { renameSession(it, chat.profile, t) }
        }
    }

    fun renameSession(row: SessionRow, profile: String, title: String) {
        val t = title.trim()
        _state.update { it.copy(renameTarget = null) }
        if (t.isEmpty() || t == row.title) return
        viewModelScope.launch {
            val w = ws ?: return@launch
            runCatching {
                w.rpc("session.title", buildJsonObject {
                    put("session_id", row.id); put("title", t)
                    if (profile.isNotBlank()) put("profile", profile)
                })
            }.onSuccess {
                _state.update { st -> st.copy(
                    groups = st.groups.mapValues { (_, g) -> g.copy(
                        sessions = g.sessions.map { if (it.id == row.id) it.copy(title = t) else it }) },
                    archived = st.archived.map { if (it.id == row.id) it.copy(title = t) else it },
                    chat = if (st.chat.longId == row.id || st.chat.shortId in row.idCandidates)
                        st.chat.copy(title = t) else st.chat,
                ) }
            }.onFailure { _state.update { it.copy(toast = "Rename failed") }; autoClearToast() }
        }
    }

    fun setArchived(row: SessionRow, archived: Boolean) {
        viewModelScope.launch {
            val w = ws ?: return@launch
            runCatching {
                w.rpc("session.archive", buildJsonObject {
                    put("session_id", row.id); put("archived", archived)
                    if (row.profile.isNotBlank()) put("profile", row.profile)
                })
            }.onSuccess {
                _state.update { st ->
                    val g = st.groups[row.profile]
                    st.copy(
                        groups = if (g != null) st.groups + (row.profile to g.copy(
                            sessions = if (archived) g.sessions.filter { it.id != row.id }
                                       else (st.archived.find { it.id == row.id }?.let { listOf(it) } ?: listOf(row)) + g.sessions
                        )) else st.groups,
                        archived = if (archived) (st.archived + row).sortedByDescending { it.updatedAt ?: 0L }
                                   else st.archived.filter { it.id != row.id },
                        toast = if (archived) "Conversation archived" else "Conversation unarchived",
                    )
                }
                autoClearToast()
            }.onFailure { _state.update { it.copy(toast = "Archive failed") }; autoClearToast() }
        }
    }

    fun deleteSession(row: SessionRow) {
        viewModelScope.launch {
            val w = ws ?: return@launch
            runCatching {
                w.rpc("session.delete", buildJsonObject {
                    put("session_id", row.id)
                    if (row.profile.isNotBlank()) put("profile", row.profile)
                })
            }.onSuccess {
                _state.update { st ->
                    val g = st.groups[row.profile]
                    st.copy(
                        groups = if (g != null) st.groups + (row.profile to g.copy(
                            sessions = g.sessions.filter { it.id != row.id }
                        )) else st.groups,
                        archived = st.archived.filter { it.id != row.id },
                        deleteDialog = false,
                        chat = if (st.chat.longId == row.id || st.chat.shortId in row.idCandidates)
                            st.chat.copy(open = false) else st.chat,
                    )
                }
            }.onFailure { _state.update { it.copy(toast = "Delete failed", deleteDialog = false) }; autoClearToast() }
        }
    }

    fun toggleLiveMode() = _state.update { it.copy(chat = it.chat.copy(liveMode = !it.chat.liveMode)) }
    fun openDeleteDialog() = _state.update { it.copy(deleteDialog = true) }
    fun closeDeleteDialog() = _state.update { it.copy(deleteDialog = false) }
    fun openRoutines() {
        _state.update { it.copy(routinesOpen = true) }
        loadRoutines()
    }
    fun closeRoutines() = _state.update { it.copy(routinesOpen = false, routinesNote = null) }
    fun routinesNote(text: String) = _state.update { it.copy(routinesNote = text) }

    // ---- Artifacts (chat menu): official derives the list client-side from the
    // transcript (no server endpoint — verified). Scan runs on open; thumbnails
    // stream in from GET /api/media?path= (allowlisted image roots). ----
    fun openArtifacts() {
        _state.update { it.copy(artifactsOpen = true) }
        refreshArtifacts()
    }
    fun closeArtifacts() = _state.update {
        it.copy(artifactsOpen = false, artifactsNote = null)
    }
    fun artifactsNote(text: String) = _state.update { it.copy(artifactsNote = text) }
    /** Folder/"All Files" and row taps return to the chat (source conversation). */
    fun openChatFromArtifacts() = closeArtifacts()

    fun refreshArtifacts() {
        // Official parity (xf6.java + df.java case 7): Artifacts is SESSION-SCOPED —
        // session -> More -> Artifacts scans ONLY this session's transcript, fetched
        // oldest-first in pages of 500, hard-capped at 5000 raw rows.
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val c = client ?: return@launch
            val chat = _state.value.chat
            if (!chat.open || chat.longId.isEmpty()) {
                _state.update { it.copy(artifacts = emptyList()) }
                return@launch
            }
            val sess = com.gravol.hermesclone.util.ArtifactSession(
                id = chat.longId, title = chat.title, preview = null,
                lastActive = null, startedAt = null,
            )
            val profile = chat.profile.ifEmpty { "default" }
            // Official parity: cumulative index survives server-side compaction.
            // Show the persisted index instantly, then merge this window's scan in.
            val cached = com.gravol.hermesclone.util.ArtifactIndex.load(
                getApplication(), profile, sess.id)
            _state.update {
                it.copy(artifacts = cached.sortedByDescending { a -> a.timestampMs })
            }
            val found = LinkedHashMap<String, com.gravol.hermesclone.util.Artifact>()
            for (a in cached) found[a.id] = a
            for (a in com.gravol.hermesclone.util.ArtifactExtractor.forSession(sess, profile) { off ->
                    runCatching { kotlinx.coroutines.runBlocking { c.artifactRowsPage(sess.id, off) }.getOrThrow() }
                        .getOrNull()
                }) {
                val prev = found[a.id]
                found[a.id] = if (prev != null && prev.timestampMs > a.timestampMs) prev else a
            }
            val list = found.values.sortedByDescending { it.timestampMs }
            android.util.Log.d("HMART", "SESSIONSCAN cached=" + cached.size + " merged=" + list.size)
            com.gravol.hermesclone.util.ArtifactIndex.save(getApplication(), profile, sess.id, list)
            _state.update { it.copy(artifacts = list) }
            loadArtifactThumbs(list)
        }
    }

    private fun loadArtifactThumbs(list: List<com.gravol.hermesclone.util.Artifact>) {
        val c = client ?: return
        val images = list.filter { it.kind == com.gravol.hermesclone.util.ArtifactKind.Image }
        if (images.isEmpty()) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            for (art in images.take(40)) {
                if (_state.value.artifactThumbs.containsKey(art.id)) continue
                if (!_state.value.artifactsOpen) return@launch
                val bytes = c.mediaBytes(art.value) ?: continue
                val bmp = runCatching {
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }.getOrNull() ?: continue
                _state.update { it.copy(artifactThumbs = it.artifactThumbs + (art.id to bmp)) }
            }
        }
    }

    fun loadRoutines() {
        val client = client ?: return
        _state.update { it.copy(routinesLoading = true) }
        viewModelScope.launch {
            client.fetchCronJobs().onSuccess { jobs ->
                // official order: next_run_at asc, paused/completed (null next) first
                val sorted = jobs.sortedWith(
                    compareBy({ it.nextRunAt ?: -1L }, { it.name.lowercase() })
                )
                _state.update { it.copy(routines = sorted, routinesLoading = false) }
            }.onFailure {
                _state.update { it.copy(routinesLoading = false) }
            }
        }
    }

    fun openArchived() = _state.update { it.copy(archivedOpen = true) }
    fun closeArchived() = _state.update { it.copy(archivedOpen = false) }

    // ---- vc49 item 5: dedicated "more sessions" page + select mode ----

    fun openMoreSessions(profile: String) {
        _state.update { it.copy(moreSessions = MoreSessionsState(open = true, profile = profile)) }
        loadMoreSessionsPage(0, replace = true)
    }
    fun closeMoreSessions() = _state.update { it.copy(moreSessions = MoreSessionsState()) }

    fun loadMoreSessionsPage(page: Int, replace: Boolean) {
        val c = client ?: return
        val ms = _state.value.moreSessions
        if (ms.loading) return
        _state.update { it.copy(moreSessions = ms.copy(loading = true)) }
        viewModelScope.launch {
            c.sessions(ms.profile, page * GatewayClient.PAGE_SIZE, excludeSourcesNow()).onSuccess { p ->
                _state.update { st ->
                    val m = st.moreSessions
                    val merged = if (replace) p.sessions else (m.sessions + p.sessions).distinctBy { it.id }
                    st.copy(moreSessions = m.copy(
                        sessions = merged, total = p.total ?: m.total,
                        loadedPages = if (replace) 1 else m.loadedPages + 1,
                        loading = false,
                    ))
                }
            }.onFailure {
                _state.update { st -> st.copy(moreSessions = st.moreSessions.copy(loading = false)) }
            }
        }
    }

    fun moreSessionsFilterSheet(name: String?) = _state.update {
        it.copy(moreSessions = it.moreSessions.copy(filterSheet = name))
    }
    fun setMoreFilterShow(v: Int) = _state.update {
        it.copy(moreSessions = it.moreSessions.copy(filterShow = v, filterSheet = null))
    }
    fun setMoreFilterProject(p: String?) = _state.update {
        it.copy(moreSessions = it.moreSessions.copy(filterProject = p, filterSheet = null))
    }
    fun toggleMoreGroupByProject() = _state.update {
        it.copy(moreSessions = it.moreSessions.copy(groupByProject = !it.moreSessions.groupByProject))
    }
    fun enterMoreSelectMode() = _state.update {
        it.copy(moreSessions = it.moreSessions.copy(selectMode = true, filterSheet = null))
    }
    fun exitMoreSelectMode() = _state.update {
        it.copy(moreSessions = it.moreSessions.copy(selectMode = false, selected = emptySet()))
    }
    fun toggleMoreSelection(id: String) = _state.update {
        val sel = it.moreSessions.selected
        it.copy(moreSessions = it.moreSessions.copy(
            selected = if (id in sel) sel - id else sel + id,
        ))
    }
    fun selectAllMoreSessions() = _state.update { st ->
        val m = st.moreSessions
        st.copy(moreSessions = m.copy(selected = m.sessions.map { it.id }.toSet()))
    }

    /** Bulk archive — session.archive VERIFIED with the LONG id ({session_id:<long>, profile}
     *  -> {archived:true, session_key}); short id answers 4001 "session not found". */
    fun bulkArchiveMore() {
        val m = _state.value.moreSessions
        val rows = m.sessions.filter { it.id in m.selected }
        if (rows.isEmpty()) return
        exitMoreSelectMode()
        viewModelScope.launch {
            val w = ws ?: return@launch
            var done = 0
            for (row in rows) {
                runCatching {
                    w.rpc("session.archive", buildJsonObject {
                        put("session_id", row.id)
                        put("archived", true)
                        if (row.profile.isNotBlank()) put("profile", row.profile)
                    })
                }.onSuccess { done++ }
            }
            // drop from both the page and the home roster
            _state.update { st ->
                val ids = rows.map { it.id }.toSet()
                st.copy(
                    moreSessions = st.moreSessions.copy(
                        sessions = st.moreSessions.sessions.filterNot { it.id in ids },
                        total = st.moreSessions.total?.let { it - rows.size },
                    ),
                    groups = st.groups.mapValues { (_, g) ->
                        g.copy(sessions = g.sessions.filterNot { it.id in ids })
                    },
                    archived = (st.archived + rows).sortedByDescending { it.updatedAt ?: 0L },
                    toast = if (done > 0) "$done conversation${if (done == 1) "" else "s"} archived" else "Archive failed",
                )
            }
            autoClearToast()
        }
    }

    fun showToast(msg: String) {
        _state.update { it.copy(toast = msg) }
        autoClearToast()
    }

    private fun autoClearToast() {
        viewModelScope.launch {
            kotlinx.coroutines.delay(2400)
            _state.update { it.copy(toast = null) }
        }
    }

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
                row = row,
                queued = restoreQueue(row.id),
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
                    currentCwd = (info?.get("cwd") as? JsonPrimitive)?.contentOrNull ?: st.chat.currentCwd,
                    // re-opened onto a live turn: elapsed base starts now (true turn start not
                    // on the wire — resume only says running:true; timer base is our guess)
                    workingSinceMs = if ((res["running"] as? JsonPrimitive)?.booleanOrNull == true)
                        System.currentTimeMillis() else st.chat.workingSinceMs,
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

    /** prompt.submit — VERIFIED capture: session_id is the SHORT live id, surface "hermes-android".
     *  vc49 queue (VERIFIED live 2026-11-26): while a turn runs the official does NOT submit —
     *  a direct prompt.submit mid-turn returns {status:"redirected"} — it collects the prompt in
     *  a client-side queue (official ChatQueuedPrompt) shown in a queue bar, flushes it when the
     *  turn settles, and offers "Steer current task" via session.steer -> {status:"queued"}. */
    fun sendPrompt(
        text: String,
        truncateBeforeRowId: Long? = null,
        rebindIds: List<Long> = emptyList(),
    ) {
        val chat = _state.value.chat
        val w = ws ?: return
        val short = chat.shortId ?: run {
            _state.update { it.copy(chat = it.chat.copy(error = "session not resumed yet")) }
            return
        }
        if (text.isBlank()) return
        if (chat.running) {
            // working: collect in the queue bar instead of submitting
            _state.update { st -> st.copy(chat = st.chat.copy(
                queued = st.chat.queued + com.gravol.hermesclone.model.QueuedPrompt(
                    id = "q${System.currentTimeMillis()}", text = text.trim(),
                ),
            )) }
            persistQueue()
            return
        }
        if (chat.sending) return
        _state.update { st -> st.copy(chat = st.chat.copy(
            sending = true,
            messages = st.chat.messages + ChatMsg("u${System.currentTimeMillis()}", "user", text,
                ts = System.currentTimeMillis() / 1000),
        )) }
        viewModelScope.launch {
            runCatching {
                w.rpc("prompt.submit", buildJsonObject {
                    put("session_id", short)
                    put("profile", chat.profile)
                    put("text", text)
                    put("surface", "hermes-android")
                    // official Regenerate wire (VERIFIED via proxy 2026-11-27): the cut rides
                    // on the resubmit; confirm_* are consent gates (4029 without them).
                    if (truncateBeforeRowId != null) {
                        put("truncate_before_row_id", truncateBeforeRowId)
                        put("confirm_truncate", true)
                        put("confirm_empty_truncate", true)
                        if (rebindIds.isNotEmpty()) putJsonArray("rebind_survivor_row_ids") {
                            rebindIds.forEach { add(it) }
                        }
                    }
                })
            }.onSuccess { res ->
                // survivor_row_id_map {old:new|null}: a strict-prefix cut keeps ids unchanged;
                // a divergent rewrite mints new rows, null = dropped — rebind our cached rowIds.
                val map = (res as? JsonObject)?.get("survivor_row_id_map") as? JsonObject
                _state.update { st -> st.copy(chat = st.chat.copy(
                    sending = false,
                    messages = if (map.isNullOrEmpty()) st.chat.messages else st.chat.messages.map { m ->
                        val old = m.rowId ?: return@map m
                        val entry = map[old.toString()] ?: return@map m
                        m.copy(rowId = if (entry == JsonNull) null
                               else (entry as? JsonPrimitive)?.longOrNull ?: old)
                    },
                )) }
            }.onFailure { e ->
                _state.update { it.copy(chat = it.chat.copy(sending = false, error = e.message)) }
            }
        }
    }

    // ---- vc49 queue controls ----

    /** "Steer" on a queued prompt — session.steer {session_id,profile,text}
     *  VERIFIED live 2026-11-26 -> {status:"queued", text} (injects into the running turn). */
    fun steerQueued(id: String) {
        val chat = _state.value.chat
        val q = chat.queued.firstOrNull { it.id == id } ?: return
        val w = ws ?: return
        val short = chat.shortId ?: return
        _state.update { st -> st.copy(chat = st.chat.copy(queued = st.chat.queued.filterNot { it.id == id })) }
        persistQueue()
        viewModelScope.launch {
            runCatching {
                w.rpc("session.steer", buildJsonObject {
                    put("session_id", short); put("profile", chat.profile); put("text", q.text)
                })
            }.onSuccess {
                _state.update { st -> st.copy(chat = st.chat.copy(
                    messages = st.chat.messages + ChatMsg("u${System.currentTimeMillis()}", "user", q.text),
                )) }
            }.onFailure { e ->
                _state.update { st -> st.copy(chat = st.chat.copy(
                    queued = st.chat.queued + q, error = e.message,
                )) }
                persistQueue()
            }
        }
    }

    fun dropQueued(id: String) {
        _state.update { st -> st.copy(chat = st.chat.copy(queued = st.chat.queued.filterNot { it.id == id })) }
        persistQueue()
    }

    /** Edit a queued prompt: remove from queue, push text back into the composer. */
    fun editQueued(id: String) {
        val q = _state.value.chat.queued.firstOrNull { it.id == id } ?: return
        _state.update { st -> st.copy(chat = st.chat.copy(
            queued = st.chat.queued.filterNot { it.id == id },
            composerPrefill = q.text,
        )) }
        persistQueue()
    }

    /** Official user-pill Edit: push the message text back into the composer (NOTES: the
     *  official rewrites the server-side row; we only re-fill the composer — the server
     *  edit method was not observed on this backend). */
    fun editMessage(msg: ChatMsg) {
        _state.update { it.copy(chat = it.chat.copy(composerPrefill = msg.content)) }
    }

    fun consumeComposerPrefill() = _state.update { it.copy(chat = it.chat.copy(composerPrefill = null)) }

    /** Queue persistence (official strings "Couldn't save queued messages:" imply the
     *  official persists per session; our storage key/format is our own). */
    private fun persistQueue() {
        val chat = _state.value.chat
        if (chat.longId.isEmpty()) return
        localPrefs.edit().putString(
            "queue_" + chat.longId,
            chat.queued.joinToString("\n") { it.text.replace("\n", "\\n") },
        ).apply()
    }

    private fun restoreQueue(longId: String): List<com.gravol.hermesclone.model.QueuedPrompt> {
        val raw = localPrefs.getString("queue_$longId", null) ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return raw.split("\n").filter { it.isNotBlank() }.mapIndexed { i, t ->
            com.gravol.hermesclone.model.QueuedPrompt(id = "qr$i${longId}", text = t.replace("\\n", "\n"))
        }
    }

    /** Stop the running turn. VERIFIED live 2026-11-26: session.interrupt
     *  {session_id:<short>, profile} -> {status:"interrupted"}; the turn ends with
     *  message.complete{text:""} shortly after. (session.stop/cancel/abort also answer
     *  with result:null on this backend — session.interrupt is the one that acts.) */
    fun stopTurn() {
        val chat = _state.value.chat
        val w = ws ?: return
        val short = chat.shortId ?: return
        viewModelScope.launch {
            runCatching {
                w.rpc("session.interrupt", buildJsonObject {
                    put("session_id", short); put("profile", chat.profile)
                })
            }
        }
    }

    // ---- vc49 inline message actions ----

    /** message.react — VERIFIED live 2026-11-26:
     *  {session_id:<short>, profile, row_id | newest_role, emoji} ->
     *  {row_id, reactions:[{emoji, author, at}]}; same emoji twice toggles it off.
     *  No dedicated reaction event fires (only sessions.changed) — update from the RPC result. */
    fun react(msgKey: String, emoji: String) {
        // official guards (Lh38;->P @02c0/@02c6/@02cc)
        if (emoji.isBlank()) {
            _state.update { it.copy(toast = "Choose an emoji.") }; autoClearToast(); return
        }
        val chat = _state.value.chat
        val w = ws
        val short = chat.shortId
        if (w == null || short.isNullOrBlank()) {
            _state.update { it.copy(toast = "Reconnect this conversation before reacting.") }
            autoClearToast(); return
        }
        val msg = chat.messages.firstOrNull { it.key == msgKey }
        viewModelScope.launch {
            runCatching {
                // official MessageReactParams key order (serializer Lrea;->serialize
                // @0022/0037/0061/008d/00b7/00e1): session_id, profile, row_id,
                // newest_role, emoji, author
                w.rpc("message.react", buildJsonObject {
                    put("session_id", short)
                    put("profile", chat.profile)
                    if (msg?.rowId != null) put("row_id", msg.rowId)
                    put("newest_role", if (msg?.role == "user") "user" else "assistant")
                    put("emoji", emoji)
                    put("author", "user")
                }) as? JsonObject
            }.onSuccess { res ->
                recordFrequentReaction(emoji)
                val rxs = (res?.get("reactions") as? JsonArray)?.mapNotNull { el ->
                    if (el !is JsonObject) return@mapNotNull null
                    com.gravol.hermesclone.model.Reaction(
                        emoji = (el["emoji"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null,
                        author = (el["author"] as? JsonPrimitive)?.contentOrNull ?: "user",
                        at = (el["at"] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0,
                    )
                }.orEmpty()
                val rowId = (res?.get("row_id") as? JsonPrimitive)?.longOrNull
                _state.update { st ->
                    st.copy(chat = st.chat.copy(messages = st.chat.messages.map { m ->
                        if (m.key == msgKey) m.copy(reactions = rxs, rowId = rowId ?: m.rowId) else m
                    }))
                }
            }.onFailure { e ->
                _state.update { st -> st.copy(toast = "Could not add reaction", chat = st.chat.copy(error = e.message)) }
                autoClearToast()
            }
        }
    }

    fun openReactionSheet(msgKey: String?) = _state.update { it.copy(chat = it.chat.copy(reactionTargetKey = msgKey)) }

    /** "FREQUENTLY USED" section of the Add Reaction sheet — persisted on-device
     *  (official behavior; our storage format is our own). */
    val frequentReactions: List<String>
        get() = (localPrefs.getString("frequent_reactions", null) ?: "👍,❤️,🙏,😂,🔥,✅,👀,😍")
            .split(",").filter { it.isNotBlank() }

    private fun recordFrequentReaction(emoji: String) {
        val cur = frequentReactions.toMutableList()
        cur.remove(emoji); cur.add(0, emoji)
        localPrefs.edit().putString("frequent_reactions", cur.take(18).joinToString(",")).apply()
    }

    /** Branch in new chat — session.branch VERIFIED live 2026-11-26:
     *  {session_id:<short>, profile} -> {session_id:<new short>, stored_session_id, title:"<parent> #2",
     *  parent, message_count, info{...}}. Requires the session to be idle (official toast
     *  "Stop the current turn before branching this chat."). */
    /** official SessionBranchParams key order (Ldoe;->serialize @0022/0037/0061/008b/00b7):
     *  session_id, profile, name, count, idempotency_key. `count` is the per-message
     *  branch anchor (messages kept through the tapped reply); `idempotency_key` is
     *  client-generated. `name` is left out so the server names the chat — the client
     *  reads the title back and shows "Hermes did not name the new chat." when it is
     *  missing (Ll17;->Q @01aa). */
    fun branchChat(keepCount: Int? = null) {
        val chat = _state.value.chat
        if (chat.running) {
            _state.update { it.copy(toast = "Stop the current turn before branching this chat.") }
            autoClearToast(); return
        }
        val w = ws
        val short = chat.shortId
        if (w == null || short.isNullOrBlank()) {
            _state.update { it.copy(toast = "Reconnect this conversation to branch it.") }
            autoClearToast(); return
        }
        viewModelScope.launch {
            runCatching {
                w.rpc("session.branch", buildJsonObject {
                    put("session_id", short)
                    put("profile", chat.profile)
                    if (keepCount != null) put("count", keepCount)
                    put("idempotency_key", java.util.UUID.randomUUID().toString())
                }) as? JsonObject
            }.onSuccess { res ->
                val newShort = res?.get("session_id")?.jsonPrimitive?.contentOrNull
                val newLong = res?.get("stored_session_id")?.jsonPrimitive?.contentOrNull
                val title = res?.get("title")?.jsonPrimitive?.contentOrNull ?: chat.title
                if (newLong != null) {
                    if (!newShort.isNullOrBlank()) liveIdMap[newShort] = newLong
                    openChat(SessionRow(newLong, title, null, profile = chat.profile), chat.profile, newShort)
                    chat.row?.profile?.let { loadPage(it, 0, replace = true) }
                } else if (title.isNullOrBlank()) {
                    _state.update { it.copy(toast = "Hermes did not name the new chat.") }
                    autoClearToast()
                }
            }.onFailure {
                _state.update { it.copy(toast = "Could not branch this chat") }; autoClearToast()
            }
        }
    }

    fun openRegenerateDialog(open: Boolean) = _state.update { it.copy(chat = it.chat.copy(regenerateDialog = open)) }

    /** Regenerate — official wire VERIFIED live 2026-11-27 (gateway logging proxy):
     *  prompt.submit with truncate_before_row_id (the last user message's row_id) +
     *  confirm_truncate + confirm_empty_truncate + rebind_survivor_row_ids covering the
     *  rows being replaced. Server contract: tui_gateway/methods_prompt.py. The old
     *  session.undo+resend approximation remains only as fallback when row_id is unknown. */
    fun regenerateLastReply() {
        val chat = _state.value.chat
        val w = ws ?: return
        val short = chat.shortId ?: return
        val lastUserIdx = chat.messages.indexOfLast { it.role == "user" }
        if (lastUserIdx < 0) return
        val lastUser = chat.messages[lastUserIdx]
        openRegenerateDialog(false)
        if (lastUser.rowId == null) {
            val lastUserText = lastUser.content
            viewModelScope.launch {
                runCatching {
                    w.rpc("session.undo", buildJsonObject {
                        put("session_id", short); put("profile", chat.profile)
                    })
                }.onSuccess {
                    _state.update { st -> st.copy(chat = st.chat.copy(
                        messages = st.chat.messages.dropLastWhile { it.role != "user" }.dropLast(1),
                    )) }
                    sendPrompt(lastUserText)
                }.onFailure { e ->
                    _state.update { st -> st.copy(chat = st.chat.copy(error = e.message)) }
                }
            }
            return
        }
        val rebindIds = chat.messages.drop(lastUserIdx).mapNotNull { it.rowId }
        _state.update { st -> st.copy(chat = st.chat.copy(
            messages = st.chat.messages.take(lastUserIdx),
        )) }
        sendPrompt(lastUser.content, truncateBeforeRowId = lastUser.rowId, rebindIds = rebindIds)
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
                    workingSinceMs = System.currentTimeMillis(),
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
                        workingSinceMs = null,
                        messages = st.chat.messages + done,
                    ))
                }
                // vc49: flush the client-side queue once the turn settles (official queue bar
                // drains in order; VERIFIED live that a mid-turn submit is "redirected", so we
                // wait for this point before sending).
                if (_state.value.chat.queued.isNotEmpty()) {
                    val first = _state.value.chat.queued.first()
                    _state.update { st -> st.copy(chat = st.chat.copy(queued = st.chat.queued.drop(1))) }
                    persistQueue()
                    sendPrompt(first.text)
                }
            }
            "session.title" -> {
                val t = (pl?.get("title") as? JsonPrimitive)?.contentOrNull
                if (!t.isNullOrBlank()) _state.update { st -> st.copy(chat = st.chat.copy(title = t)) }
            }
            "session.reclaimed" -> _state.update { st ->
                st.copy(chat = st.chat.copy(running = false, workingSinceMs = null, error = "session reclaimed"))
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

    fun workingSinceFor(row: SessionRow): Long? =
        _state.value.workingSince.entries.firstOrNull { e -> row.idCandidates.any { matchesId(it, e.key) } }?.value

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

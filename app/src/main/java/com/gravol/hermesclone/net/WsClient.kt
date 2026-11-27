package com.gravol.hermesclone.net

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * JSON-RPC over the gateway WebSocket, per CONTRACT.md:
 *  - connect: GET /api/ws?ticket=<ticket>, subprotocols "hermes-gateway-v1, hermes-gateway-ticket.<sig>"
 *  - request:  {jsonrpc:"2.0", id:<int>, method, params}  → result {jsonrpc, id, result}
 *  - events:   {jsonrpc, method:"event", params:{type, session_id, payload, seq}}
 *  - server→client requests share the channel with string ids "srq-<hex>" and the client
 *    must answer {jsonrpc, id:"srq-...", result:{...}}
 */
class WsClient(
    private val http: OkHttpClient,
    private val wsUrl: String,
    private val subprotocols: String,
) {
    companion object {
        const val SUBPROTOCOL_BASE = "hermes-gateway-v1"        // CONTRACT.md (observed)
        const val SUBPROTOCOL_TICKET_PREFIX = "hermes-gateway-ticket." // CONTRACT.md + DEX Lco6;->W
        /**
         * RPC timeout: official Lco6;->w()J (classes.dex @0x4) = Math.min(0x2710,
         * Ldo6;->f) with f=15000 (fr6.y @00c4 → Ldo6;-><init> @0022) → 10_000 ms.
         * Applied to every session RPC: Lco6;->J/L/O/R/V/q pass w() into g(...,J timeout).
         */
        const val RPC_TIMEOUT_MS = 10_000L

        /**
         * Heartbeat tick: Lco6;->B delays on Ldo6;->i (B @008c/0098 `Lsh;->g0`),
         * i = 15_000 ms (Ldo6;-><init> @005d).
         */
        const val HEARTBEAT_INTERVAL_MS = 15_000L

        /**
         * Miss limit: Lco6;->B @0077-007e threshold = max(1, Ldo6;->j / Ldo6;->i)
         * = 45_000 / 15_000 = 3; at the 3rd un-acked interval Len6.invokeSuspend
         * (classes2 @0075) throws "No frame from Hermes for N heartbeat intervals"
         * → Lco6;->k(...) drops the connection.
         */
        const val HEARTBEAT_MISS_LIMIT = 3

        /** Ping method names: official Lco6;->y negotiates between these two. */
        const val PING_NEGOTIATED = "gateway.ping"
        const val PING_PLAIN = "ping"
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val nextId = AtomicInteger(1)
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<JsonElement>>()
    private val opened = CompletableDeferred<Unit>()
    private var socket: WebSocket? = null

    /**
     * Heartbeat frame (official Lco6;->B, classes.dex @0x119):
     *   {jsonrpc:"2.0", id:"heartbeat-<n>", method:<pingMethod>, params:{}}
     * The id is a *string* counter, not an int RPC id, so replies don't collide with
     * rpc() pending entries.
     */
    private val heartbeatCounter = AtomicInteger(0)
    @Volatile var pingMethod: String = PING_NEGOTIATED
        private set

    /**
     * Official Lco6;->F flag: set true in Lco6;->H @0072 once the heartbeat frame's
     * JSON-RPC response arrives; read+cleared by the heartbeat loop Lco6;->B @00a9-00b7
     * (true → miss counter resets to 0 and no frame is sent this tick).
     */
    @Volatile private var heartbeatAcked = false

    /** Set the negotiated ping method from the gateway.ready payload flag (Lco6;->y).
     *  VERIFIED: the official gateway.ready handler reads only session_authority and
     *  replay_epoch — no server-provided interval exists (Lco6;->y const-strings). */
    fun applyReady(supportsGatewayPing: Boolean?) {
        if (supportsGatewayPing != null)
            pingMethod = if (supportsGatewayPing) PING_NEGOTIATED else PING_PLAIN
    }

    /** Consume the ack flag (official B @00a9: `if (F) { F=false; misses=0; no send }`). */
    fun takeHeartbeatAck(): Boolean {
        val was = heartbeatAcked
        heartbeatAcked = false
        return was
    }

    /** Fire one heartbeat frame; returns false if the socket is gone. */
    fun sendHeartbeat(): Boolean {
        val w = socket ?: return false
        val frame = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", "heartbeat-${heartbeatCounter.getAndIncrement()}")
            put("method", pingMethod)
            put("params", buildJsonObject { })
        }
        heartbeatAcked = false
        return w.send(frame.toString())
    }

    @Volatile var lastSeq: Long? = null
        private set
    @Volatile var epoch: String? = null
        private set

    var onOpen: (() -> Unit)? = null
    var onClosed: ((code: Int, reason: String) -> Unit)? = null
    var onEvent: ((type: String, sessionId: String?, payload: JsonElement, seq: Long?) -> Unit)? = null
    var onServerRequest: ((id: String, method: String, params: JsonObject) -> Unit)? = null

    val isOpen: Boolean get() = socket != null && opened.isCompleted

    fun connect() {
        val req = Request.Builder()
            .url(wsUrl)
            .header("Sec-WebSocket-Protocol", subprotocols)
            .build()
        socket = http.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!opened.isCompleted) opened.complete(Unit)
                onOpen?.invoke()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleFrame(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                failAll("closed $code")
                onClosed?.invoke(code, reason)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!opened.isCompleted) opened.completeExceptionally(t)
                failAll(t.message ?: "ws failure")
                onClosed?.invoke(-1, t.message ?: "websocket failure")
            }
        })
    }

    suspend fun awaitOpen(timeoutMs: Long = 15_000) {
        try {
            withTimeout(timeoutMs) { opened.await() }
        } catch (e: TimeoutCancellationException) {
            error("WebSocket ticket request returned HTTP timeout / gateway did not upgrade")
        }
    }

    fun close() {
        socket?.close(1000, null)
        socket = null
    }

    /** Hard kill: OkHttp cancel() tears the socket down WITHOUT the close
     *  handshake — on a dead/half-open link close(1000) never completes, so
     *  onClosed never fires and the app never reconnects (VERIFIED via
     *  mid-turn relay kill on emulator, 2026-11-27). cancel() forces
     *  onFailure → onClosed(-1) → scheduleReconnect. */
    fun cancel() {
        socket?.cancel()
        socket = null
    }

    /** Send a JSON-RPC request and suspend for its result. */
    suspend fun rpc(method: String, params: JsonObject): JsonElement {
        val id = nextId.getAndIncrement()
        val frame = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            put("params", params)
        }
        val deferred = CompletableDeferred<JsonElement>()
        pending[id] = deferred
        val sent = socket?.send(frame.toString()) ?: false
        if (!sent) {
            pending.remove(id)
            error("websocket not open")
        }
        try {
            return withTimeout(RPC_TIMEOUT_MS) { deferred.await() }
        } finally {
            pending.remove(id)
        }
    }

    /** Send a C2S request frame without waiting for its result (clarify.lock —
     * VERIFIED: a normal int-id request {request_id, question_id, answer, profile};
     * the reply is irrelevant, the lock claim is what matters). */
    fun sendRequestNoWait(method: String, params: JsonObject): Boolean {
        val frame = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", nextId.getAndIncrement())
            put("method", method)
            put("params", params)
        }
        return socket?.send(frame.toString()) ?: false
    }

    /** Answer a server→client request (id is a string like "srq-<hex>"). */
    fun respondToServerRequest(id: String, result: JsonObject) {
        val frame = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("result", result)
        }
        socket?.send(frame.toString())
    }

    fun respondErrorToServerRequest(id: String, message: String) {
        val frame = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("error", buildJsonObject {
                put("code", -32601)
                put("message", message)
            })
        }
        socket?.send(frame.toString())
    }

    /** Record replay state from a session.events.since result. */
    fun noteReplayState(latestSeq: Long?, newEpoch: String?) {
        if (latestSeq != null) lastSeq = latestSeq
        if (!newEpoch.isNullOrBlank()) epoch = newEpoch
    }

    private fun handleFrame(text: String) {
        val el = runCatching { json.parseToJsonElement(text) }.getOrNull()
            ?: return
        if (el !is JsonObject) return

        val method = (el["method"] as? JsonPrimitive)?.jsonPrimitive?.contentOrNullSafe()
        val idEl = el["id"]

        when {
            method == "event" -> {
                val params = el["params"] as? JsonObject ?: return
                val type = (params["type"] as? JsonPrimitive)?.contentOrNullSafe() ?: return
                val sessionId = (params["session_id"] as? JsonPrimitive)?.contentOrNullSafe()
                val payload = params["payload"] ?: JsonObject(emptyMap())
                val seq = (params["seq"] as? JsonPrimitive)?.let {
                    it.content.toLongOrNull()
                }
                if (seq != null && (lastSeq == null || seq > lastSeq!!)) lastSeq = seq
                onEvent?.invoke(type, sessionId, payload, seq)
            }

            idEl is JsonPrimitive && idEl.isString -> {
                val sid = idEl.content
                // Heartbeat response: string id "heartbeat-<n>" with a result/error and no
                // method — this is what sets the official Lco6;->F flag (Lco6;->H @0072).
                if (sid.startsWith("heartbeat-") && el["method"] == null) {
                    heartbeatAcked = true
                    return
                }
                // server → client request, id "srq-<hex>", method = capability name
                if (!method.isNullOrBlank()) {
                    onServerRequest?.invoke(sid, method, el["params"] as? JsonObject ?: JsonObject(emptyMap()))
                }
            }

            idEl is JsonPrimitive -> {
                val id = idEl.content.toIntOrNull() ?: return
                val deferred = pending.remove(id) ?: return
                val err = el["error"] as? JsonObject
                if (err != null) {
                    deferred.completeExceptionally(
                        RuntimeException(err["message"]?.jsonPrimitive?.contentOrNullSafe() ?: "rpc error")
                    )
                } else {
                    deferred.complete(el["result"] ?: JsonObject(emptyMap()))
                }
            }
        }
    }

    private fun failAll(reason: String) {
        for ((_, d) in pending) d.completeExceptionally(RuntimeException("websocket $reason"))
        pending.clear()
    }

    private fun JsonPrimitive.contentOrNullSafe(): String? =
        if (this.isString) this.content else this.content.takeIf { it != "null" }
}

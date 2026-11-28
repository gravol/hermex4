package com.gravol.hermesclone.model

/** A tool call shown as a pill inside a streaming assistant bubble. */
data class ToolCard(
    val toolId: String,
    val name: String,
    val context: String,
    val done: Boolean = false,
    val durationS: Double? = null,
    val ok: Boolean? = null,
    /** raw JSON of tool args — artifact scan input (official oc0 walks args keys) */
    val argsJson: String? = null,
    /** tool result text/output — artifact scan input */
    val resultText: String? = null,
)

/**
 * One chat message (history row or live bubble).
 * Shapes VERIFIED live 2026-11-24:
 *  - REST history: {id:int, role:"user"|"assistant"|"tool", content, tool_calls:[...]}
 *  - live turn: message.start{turn_id} / message.delta{text,turn_id,segment,offset} /
 *    message.complete{text,usage,status} / thinking.delta{text} / reasoning.delta{text} /
 *    tool.start{tool_id,name,context,args} / tool.complete{tool_id,duration_s,result}
 */
/** One reaction on a message row. VERIFIED live 2026-11-26:
 *  message.react{session_id,profile,row_id|newest_role,emoji} ->
 *  {row_id, reactions:[{emoji, author:"user", at:<epoch s>}]}; same emoji twice removes it.
 *  Persisted on the REST row under display_metadata.reactions (same shape). */
data class Reaction(
    val emoji: String,
    val author: String,
    val at: Double,
)

/** A prompt collected while a turn runs (official ChatQueuedPrompt). The queue is
 *  CLIENT-side (verified 2026-11-26: prompt.submit while streaming returns
 *  {status:"redirected"} — the official app does not submit while working; it queues
 *  locally and flushes when the turn settles. "Send now" = session.steer
 *  {session_id,profile,text} -> {status:"queued"} (VERIFIED). */
data class QueuedPrompt(
    val id: String,
    val text: String,
    val queuedAtMs: Long = System.currentTimeMillis(),
)

/** session.context_breakdown result (VERIFIED live 2026-11-26 against the real gateway). */
data class ContextCategory(
    val id: String,
    val label: String,
    val tokens: Int,
    val color: String,
)

data class ContextUsage(
    val categories: List<ContextCategory>,
    val used: Int,
    val max: Int,
    val percent: Int,
)

data class ChatMsg(
    val key: String,
    val role: String,            // user | assistant
    val content: String,
    val streaming: Boolean = false,
    val reasoning: String = "",
    val tools: List<ToolCard> = emptyList(),
    val usageLine: String? = null,
    /** epoch seconds from the history row (VERIFIED live: rows carry float `timestamp`) */
    val ts: Long? = null,
    /** REST row id (VERIFIED: rows carry int `id`; message.react takes it as row_id) */
    val rowId: Long? = null,
    /** reactions persisted on the row (display_metadata.reactions) */
    val reactions: List<Reaction> = emptyList(),
    /** vc57: client-side slash-command row (official parity — VERIFIED 2026-11-28 the
     *  official app never sends slash text to the gateway; pills+output are local and
     *  survive reopen but die on app kill, so they live in the VM's per-session overlay). */
    val local: Boolean = false,
)

/** A clarify question (VERIFIED capture tour3: {qid, question, choices|null, multi_select}). */
data class ClarifyQ(
    val qid: String,
    val question: String,
    val choices: List<String>,
    val multiSelect: Boolean,
)

/**
 * An unanswered server->client request (srq-<hex> id). Shapes VERIFIED (capture tour3/3c):
 *  approval: {session_id, command, pattern_key, pattern_keys, description,
 *             allow_permanent, allow_session, request_id, timeout_s,
 *             choices:["once","session","always","deny"]}  -> answer {choice:<one of choices>}
 *  clarify:  {session_id, questions:[{qid, question, choices|null, multi_select}]}
 *            -> per question clarify.lock{request_id, question_id, answer, profile},
 *               then answer {answers:{qid:<text>}}
 */
data class PendingSrq(
    val id: String,
    val method: String,
    val sessionId: String,
    val command: String = "",
    val description: String = "",
    val choices: List<String> = emptyList(),
    val allowPermanent: Boolean = false,
    val timeoutS: Int = 600,
    val receivedAtMs: Long = System.currentTimeMillis(),
    val questions: List<ClarifyQ> = emptyList(),
)


/** Phase 4: model.options provider row (VERIFIED payload: {model, provider,
 *  providers:[{slug,name,source,models:[str],is_current,authenticated,...}]}) */
data class ModelProviderRow(
    val slug: String,
    val name: String,
    val source: String,
    val models: List<String>,
    val isCurrent: Boolean,
)

data class ModelSheetState(
    val open: Boolean = false,
    val loading: Boolean = false,
    val busy: Boolean = false,
    val currentModel: String = "",
    val currentProvider: String = "",
    val providers: List<ModelProviderRow> = emptyList(),
    val expanded: String? = null,
    val note: String? = null,
    /** slug+model awaiting confirm_expensive_model (VERIFIED: config.set model can return confirm_required) */
    val pending: Pair<String, String>? = null,
)

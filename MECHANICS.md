# MECHANICS.md - Phase 1 constants, rules and sources

Wire names are from CONTRACT.md (live-capture-verified, authoritative). Every DERIVED item
below is from the official `official-base.apk` (sha256 b545018e8cba..., classes*.dex),
read as **decompiled bytecode** (Craft Anything `android_method_show` plus an androguard
walk over every method body), cited as `Lclass;->method` + code offset.
Evidence ids: ev_de484bee (`Lco6;->w`), ev_b248221a (`Lco6;->q`), ev_b715f67d (`Ltz3;`),
ev_0209ddda (`Lco6;->y`), ev_01b79789 (`Lw1b;->invoke`), ev_b698f29a (`La50;-><clinit>`),
ev_fe75c9c0 (`Lgu6;->c`), ev_e12b07ff (`Lp4i;->e`).
Anything still marked *(estimated)* was not found in the binary.

## Transport
| Item | Value | Source |
|---|---|---|
| Gateway port (prod) | 9119 | CONTRACT.md; DEX strings `https://hermes.local:9119`, "usually on port 9119" (`Lk3i;->r` @0140) |
| REST scheme | plain HTTP + WS on the gateway port | CONTRACT.md |
| Session cookie | `hermes_session_rt` (from Set-Cookie at login) | CONTRACT.md (observed); the name appears **nowhere** in the DEX const-string pool — official stores whatever the gateway sets via an OkHttp cookie jar built in `Lgu6;` |
| WS connect | `GET /api/ws?ticket=<URLEncoder.encode(ticket,"UTF-8")>`; http→`ws`, https→`wss`; errors "Invalid dashboard URL" / "Dashboard URL must use HTTP or HTTPS" | DEX `Lco6;->W`: scheme map @0004-0017, `/api/ws` @001f, `UTF-8` @0021, `ticket=` @0037, encode @0031, errors @001d/@00b2 |
| WS local mode | same path with query `token=<local token>` ("Empty local token") — loopback/local-token variant, not used by the clone | DEX `Lco6;->W` @0083, @00a5 |
| WS subprotocols | `hermes-gateway-v1`, `hermes-gateway-ticket.<ticket>` — the sig IS the ticket | DEX `Lco6;->W` @004b (concat) + @0051/@0057 (list) |
| JSON-RPC request | `{jsonrpc:"2.0", id:<int>, method, params}` -> `{jsonrpc, id, result}` | CONTRACT.md |
| Heartbeat frame | `{jsonrpc:"2.0", id:"heartbeat-<n>", method:<gateway.ping\|ping>, params:{}}` - id is a STRING counter (`Lco6;->I` +1 per send), not the int RPC id | DEX `Lco6;->B` @00df-014d: "2.0" @00e6, "jsonrpc" @00ef, counter @00f7-00fa, "heartbeat-" @00fe, "id" @0111, method=field H @0119, "params":{} @012a-0133 |
| Heartbeat tick | **15 000 ms** (`Ldo6;->i`), read at B @008c and passed to `Lsh;->g0` (delay) @0098 | DEX `Ldo6;-><init>` @005d `const-wide/16 0x3a98` (synthetic ctor; caller `Lfr6;->y` @00c4) |
| Heartbeat send rule | ack-gated: if the ack flag (`Lco6;->F`, set in `Lco6;->H` @0072 when the frame's JSON-RPC response arrives) is true → clear it, reset miss count, skip this tick; else miss++ and send | DEX `Lco6;->B` @00a9-00b7 (F branch), @00b8-00bb (miss++), @00c1 `if-ltz` → send @00df |
| Dead-link watchdog | after **3** consecutive un-acked intervals (threshold `max(1, Ldo6;->j / Ldo6;->i)` = 45000/15000) → `Exception("No frame from Hermes for N heartbeat intervals")` → `Lco6;->k` (connection failure → reconnect) | DEX `Lco6;->B` @0077-007e (threshold), @00c3-00dd (`Lkv7;->Y` + `Len6`); `Len6;->invokeSuspend` (classes2) @0073-0084 ("No frame from Hermes for " @0075 + " heartbeat intervals" @0077) |
| RPC timeout | `min(0x2710, Ldo6;->f)` = **10 000 ms** — this is what `w()` is for; passed as the timeout arg of `g(...J...)` by `J/L/O/R/V/q` | DEX `Lco6;->w()J` @0x4; `Ldo6;-><init>` @005b f/g=15000 (0x3a98), @005f j=45000 (0xafc8); callers `Lco6;->J` @0066, `L` @0060, `O` @0089, `R` @0098, `V` @004d, `q` @00e3 |
| Ping method | default `gateway.ping` (`Lco6;-><init>` @003d); `gateway.ready` payload flag `session_authority` selects `gateway.ping` (true) / `ping` (false), stored to field H | DEX `Lco6;->y` "session_authority" @0044, "gateway.ping" @025c / "ping" @025f, `iput H` @0261; `<init>` @003d |
| `gateway.ready` payload keys | `session_authority`, `replay_epoch` (per-session epoch bookkeeping) — **no interval key exists** (full const-string census of `y`) | DEX `Lco6;->y` @0044, @0070 |
| Reconnect: post-open wait | 1000 ms before replay (`Lsh;->g0(1000)`) | DEX `Lco6;->q` @0058 |
| Reconnect: replay pass | walk the pending-session map in **TreeMap** order; skip sessions not in the subscribed set; for each whose last event is <= 90 s old, call `session.events.since{session_id, since=last_seq or 0}` with timeout `w()`; older sessions are dropped from pending | DEX `Lco6;->q`: TreeMap @006b, `System.nanoTime` @0099, staleness const `0xd18c2e2800` ns = 90 s @00a5, `"session.events.since"` @00b0, since default 0L @00ca, `w()` @00e3, `g(...)` @00ee |
| Reconnect: retry cadence | 1 s -> 30 s exponential (clone policy) | *(estimated - the official WS retry cadence constant was not located; `Ltz3;->invokeSuspend` @0x450 has a 3000 ms delay in a profile-sync branch, not the WS backoff)* |
| Unanswered-prompt timeout | ~10 min -> "No answer was submitted before the prompt timed out." | CONTRACT.md (server message; the string is NOT in the DEX — server-side text) |
| Session idle-time presets | Immediately (0 s) / "After 1 minute" "1 min" (60) / "After 5 minutes" "5 min" (300) / "After 15 minutes" "15 min" (900) / "After 1 hour" "1 hour" (3600); enum names Immediately/OneMinute/FiveMinutes/FifteenMinutes/OneHour | DEX `La50;-><clinit>` (ev_b698f29a) @0002-004f: 0x3c, 0x12c, 0x384, 0xe10 seconds |

## REST surface
| Endpoint | Notes | Source |
|---|---|---|
| `POST /auth/password-login` | JSON body `{username,password}`, `Content-Type: application/json`, **no Authorization header** (cookie jar holds the session) | DEX `Lzaf;->e`: "POST" @0048, "/auth/password-login" @004a, "application/json" @004e, body = `Lrih;.serializer` JSON @0037-0041, `Lgu6;.c` @0055 |
| `GET /api/auth/ws-ticket` | response key is exactly `"ticket"`; HTTP 401/403 -> "WebSocket ticket request returned HTTP <n>"; other non-2xx -> "The agent is asleep."; blank -> "Empty WebSocket ticket"; missing key -> "Ticket response has no ticket" | DEX `Lpx3;->d`: "/api/auth/ws-ticket" @0069, status branch @0091-009f, "WebSocket ticket request returned HTTP " @0099, "ticket" @00b3, "Empty WebSocket ticket" @00d5, "Ticket response has no ticket" @00dd |
| `GET /api/auth/providers` | `DashboardAuthProvidersResponse(providers=...)` - not needed for phase 1 | DEX |
| `GET /api/status` | liveness check | CONTRACT.md; DEX |
| `GET /api/profiles` | profile list = tree groups (+ `/api/profiles/active`, `/api/profiles/projects/tree`) | CONTRACT.md; DEX |
| `GET /api/sessions` | query vocabulary confirmed in `Lnb;->P`: `limit` @003f, `offset` @0051, `min_messages` @0063, `archived` @0071, `order` @007b, `profile` @0085; GET @0093, "/api/sessions" @0095; phase-1 call uses `archived=include&limit=100&offset=N&order=recent&profile=<p>` | DEX `Lnb;->P`; exact values CONTRACT.md |

## WS methods used in phase 1 (names cross-checked against the DEX string pool)
`client.capabilities{server_requests:true}` / `profiles.list` / `profiles.get_asset`
(avatar png base64) / `session.active_list{profile}` / `session.events.since{session_id,
since}` (param shape from `Lco6;->q`) / `session.create{profile, source:"gui",
close_on_disconnect, hidden, follow_profile_config}` / `session.resume`
(`Lco6;->R` @0x62, `Lk90;->i` @0x7) / heartbeat `gateway.ping` / `ping`.

NOT used: any `turn.*` - it does not exist on the wire. Turn/live state is derived from
`message.*` / `tool.*` events + `session.active_list` (CONTRACT.md note).

## Events handled in phase 1
`gateway.ready` -> ping-method + replay-epoch bookkeeping (see Transport) /
`sessions.changed` -> silent refresh of loaded pages /
`message.start`/`tool.start` -> mark session working (live dot + "Working" + spinner) /
`message.complete` -> clear working, refresh page 0 of that profile /
`session.title` -> update row title in place /
`session.reclaimed` -> drop row /
everything else (chat stream) ignored until phase 2.

## Server requests (srq-) in phase 1
`approval` -> answer `{choice:"deny"}` (safe default; choice vocabulary
once|session|always|deny is CONTRACT.md; the DEX carries the real UI labels — enum
`Lsw1;-><clinit>` (classes2): `once`="Allow Once"/"Once", `session`="Allow for Session"/"Session",
`always`="Always Allow"/"Always", `deny`="Deny"/"Deny") /
other capabilities -> JSON-RPC error -32601. Full handling is phase 2.

## Session id mapping
Short ids (`d53b0506`) are live/WS, long ids (`20261122_211856_012d96`) persisted/REST
(CONTRACT.md). The official client keeps a per-session epoch map (`Lco6;->Z`) and matches
by exact equality only (`Llv7;->z`) - no prefix/suffix rule exists in the binary, so the
clone now matches exact only. *(the earlier prefix/suffix rule was a guess; removed)*

## UI constants (sampled from shots-tour-2026-11-22 + resources.arsc)
| Item | Value | Source |
|---|---|---|
| Background | `#000022` deep navy | dominant color of tour screenshots (67-79% of pixels). NOTE: a const-wide/32 census over all three dexes found no app ARGB constants - Compose computes these colors at runtime, so screenshots remain the source |
| Card/surface | `#0C1A3C` | screenshot sampling |
| Row highlight | `#1D2761` | screenshot sampling |
| Accents | purple `#8B5CF6`, green `#66DD4B`, teal `#4BDDC7` | screenshot sampling (avatar/FAB colors) |
| "Working" color | blue `#4D8DFF` + spinner | 01-launch.png; exact hex *(estimated)* |
| resources.arsc window_background | light `#faf6ee`, night `#1c1c1e` | resources.arsc (splash/widget palette only - Compose screens draw the navy theme themselves) |
| Child rows per expanded group | all loaded, then "N more sessions" | gap report + 01-launch.png; page size 100 caps it |
| Relative time format | `now` `5m` `12h` `5w` | right column in 01-launch.png. The DEX duration formatters `Lp4i;->e` (ev_e12b07ff) and `Lsgg;->b` use " sec"/" min"/" hr" on other surfaces; the session-row label is built in Compose |
| Search placeholder | "Search agents and sessions" | DEX string pool (`Ljf9;->c`) |
| Load-earlier label | "Load earlier messages" | resources.arsc 0x7f12003a |
| Group expansion label | "%d more session(s)" | gap report + 01-launch.png (built in Compose, not a const-string) |
| Login error copy | "Hermes did not accept that username and password." | DEX `Lk3i;->r` @011c (full connect-screen set @0069-0174: portal-not-running, portal-signin, browser-session, no-signed-in-session, session-token, "Unknown provider", no-basic-auth-provider, HTTP-404 "usually on port 9119" hint, rate-limit "Too many sign-in attempts. Wait a minute, then try again.", "Dashboard sign-in failed: ") |
| Ticket error copy | "WebSocket ticket request returned HTTP <n>" / "The agent is asleep." | DEX `Lpx3;->d` |
| Connect screen copy | "Connect to your gateway" + basic-auth hint | gap report; DEX "usually on port 9119, not a chat web UI in front of it." |
| Profile-switch toasts | "You're on <name> now" / "Wait for the last change to finish." / "Added from Hermes Cloud" | DEX `Lw1b;->invoke` (ev_01b79789) @0x34+0x3c, @0x5a, @0x02 |
| Home controls | settings(gear) / filter / search / profile chips / compose FAB | gap report "Session home" + 01-launch.png |

## Clone wiring (what the code now does)
- `GatewayClient.login` - JSON body only, no Basic header (`Lzaf;->e`).
- `GatewayClient.wsTicket` - key `ticket` only, official error copy, sig = ticket (`Lpx3;->d`, `Lco6;->W`).
- `WsClient.sendHeartbeat` - string-id `heartbeat-<n>` frame, method `gateway.ping`/`ping` (`Lco6;->B` @00df-014d, `Lco6;->y` @025c-0261).
- `WsClient` ack tracking - `heartbeatAcked` set when a string-id `heartbeat-*` response arrives (`Lco6;->H` @0072 → field F); `takeHeartbeatAck()` mirrors B @00a9.
- `AppViewModel.startPingLoop` - official B loop: 15 s tick (`Ldo6;->i`), ack-gated send, miss counter, ≥3 misses → close → reconnect (`Len6;->invokeSuspend` @0073-0084 → `Lco6;->k`).
- `WsClient.RPC_TIMEOUT_MS` = 10 000 ms = `Lco6;->w()` (min(0x2710, f=15000)), matching the timeout arg of official `g(...)` in J/L/O/R/V/q.
- `AppViewModel.handleEvent("gateway.ready")` - reads `session_authority` flag only; no interval key (`Lco6;->y`).
- `AppViewModel.scheduleReconnect` - 1000 ms wait before `session.events.since` replay (`Lco6;->q` @0058).
- `AppViewModel` id matching - exact equality only (`Llv7;->z`; short↔long via `session_key`, live-verified).

---

# PART 2 — VC49 PASS (2026-11-26): mined from `official-vc49.apk`

Target opened with `core_target_open`: **t_448870ee337e** `/home/jeff/official-vc49.apk`,
sha256 `448870ee337e6c45f3246c10a72015f364b4902d895abb3ae7fd73e51b5872e8` (104,734,666 B,
"An Android app", 3 DEX: classes.dex 25,035 methods / classes2 / classes3).
Method of work: full disassembly of all three DEX (`dexdump -d`) indexed to
`string -> class#method + code-unit offset`, then each hit method read body-by-body
(`android_method_show` / the same bytecode listing). Offsets below are **code-unit offsets
inside the named method**, exactly as `dexdump`/`android_method_show` print them (`|00f1:`).
Names are R8-obfuscated in 1.0.0-49; every name here was re-derived from this APK.

## 2.1 Wire vocabulary actually implemented (dispatcher `Lboe;->f`)

`Lboe;->f(String method, …)` is the client's RPC dispatcher; every string it loads is a
method name the official client can call (offset = where the name is loaded):

| wire method | offset | wire method | offset |
|---|---|---|---|
| `session.create` | 0388 | `session.activate` | 0282 |
| `session.resume` | 02e3 | `session.interrupt` | 03cf |
| `session.branch` | 03de | `session.steer` | 07e9 |
| `session.undo` | 0626 | `session.close` | 07f6 |
| `session.list` | 0648 | `session.cwd.set` | 081a |
| `session.usage` | 0759 | `session.workspace.move` | 08d2 |
| `session.title` (`rename`) | 077e/07b4 | `session.compress` | 098b |
| `session.events.since` | 0484 | `session.redirect` | 09ab |
| `prompt.submit` | 0969 | `slash.exec` | 0440 |
| `prompt.btw` (see 2.2) | `Ll17;->M` @00c3 | `command.dispatch` | 04b7 |
| `image.attach_bytes` | 0420 | `image.detach` | 04a4 |
| `pdf.attach` | 01ac | `file.attach` | (error text @04ae) |
| `approval.respond` | 06c3 | `request.answer` | 01b5 |
| `clarify.lock` | 0511 | `clarify.respond` | 0304 |
| `config.set` | 03ff | `reload.mcp` | 088e |
| `commands.catalog` | 093e | `session.context_breakdown` | `Ld54;->u` @022e, `Lu37;<clinit>` @0060 |

Also observed: `message.react` (`Lh38;->P` @0170), `session.archive` (`Lwa7;->b` @00c3),
`session.delete` (`Lwa7;->b` @010b), `prompt.resolve_unknown` (`Ls3i;->e` @00f8),
`rollback.list` / `spawn_tree.list` (`Ld54;->u` @008c / @0128), `session.set_hidden`.
**`prompt.queue` / `message.edit` / `session.rename` / `session.pin` do not exist** in the
binary (0 hits over all three DEX).

## 2.2 message.react — real request shape

`Lh38;->P(Lswe;String;String;Liv3;)` @0170 sends `message.react`.
Request serializer `Lrea;->serialize` keys, in order: `session_id` @0022, `profile` @0037,
`row_id` @0061, `newest_role` @008d, `emoji` @00b7, `author` @00e1.
Data class: `MessageReactParams(sessionId, profile, rowId, newestRole, emoji, author)`
(`Lsea;->toString` @0004-001b). Result `MessageReactResult(rowId, reactions)`
(`Ltea;<clinit>`: `row_id`, `reactions`); pushed rows use `MessageReactionPayload(row_id,
reactions, role)` (`Lzea;<clinit>` @000f/0015/001a) and `ReactionPayload(kind, …)`
(`Lajd;<clinit>`).
Guards before the call (Lh38;->P): @02c0 `"Reconnect this conversation before reacting."`,
@02c6 `"That message is no longer part of this conversation."`, @02cc `"Choose an emoji."`.
Failure toast copy: `"Could not add reaction"` (`Lyv2;->F` @0379).

## 2.3 session.branch — the per-message anchor IS on the wire

`Ll17;->Q(Lswe;String;String;Liv3;)` @0180 sends `session.branch`.
Request serializer `Ldoe;->serialize` keys, in order: `session_id` @0022, `profile` @0037,
`name` @0061, `count` @008b, `idempotency_key` @00b7 (deserialize errors: `"Missing
session_id"` @00be).
Data class `SessionBranchParams(sessionId, profile, name, count, idempotencyKey)`
(`Leoe;->toString` @0004-0010). So a branch carries a **message count** (the anchor) plus a
client-generated **idempotency key** — the earlier "branch anchor not observed" gap is closed.
Result `SessionBranchResult(sessionId, storedSessionId, title, parent, messageCount,
messages, info)` (`Lhoe;->toString`; wire keys `session_id, stored_session_id, title,
parent, message_count, messages, info`).
Guards (Ll17;->Q): @0024 `"That message is no longer part of this conversation."`,
@0026 `"Stop the current turn before branching this chat."`, @01aa `"Hermes did not name
the new chat."`, @01bc `"Reconnect this conversation to branch it."`.

## 2.4 prompt.btw — side question (not the queue)

`Ll17;->M(Lswe;Lsya;String;Liv3;)` @00c3 sends `prompt.btw` with
`SideAgentParams(sessionId, profile, text)` (`Lu7f;->toString` @0004) → `TaskIdResult(taskId)`
(`Lu7g;->toString` @0000). UI copy @008d: `"Ask something after /btw — it is answered on the
side, without interrupting the current reply."`; guards @00f3 `"The conversation changed
while asking the side question."`, @0134 `"Couldn't confirm this side question: "`.
Slash parsing: split on `\s+` @0061, token `"COMMAND"` @0082, id prefix `"command:"` @0124.

## 2.5 The queue is CLIENT-side and persisted (no wire name)

No `prompt.queue`/`queued_prompt` string exists. What the official does instead:
* a dedicated dispatcher named **`chat-queues`** (`Lc80;<init>` @0216);
* per-session queue file **`<id>.queue.json`** (`Lmgd;->c` @002e);
* local model `ChatQueuedPrompt(id=…)` (`Lwm2;->toString`; `Lum2;<clinit>` @0009
  `ai.nousresearch.hermes.mobile.model.chat.ChatQueuedPrompt`) and `ChatQueuedAttachment`
  (`Lfm2;<clinit>`); state transitions `SendQueued(id=…)`, `RemoveQueued(id=…)`,
  `RewordingQueued(id=…)` (`Ll32;->toString`, `Le32;->toString`, `Lj32;->toString`);
* rewording flag key **`chat.queue.rewording`** (`Lyv2;<init>` @03ec, `Lyv2;->I` @004e,
  `Lyv2;->F` @06fe); draft keys `chat.edit.draft` @015a and `chat.draft.folder` @02b5;
* rules copy: `"That message is already on its way."` (`Lyv2;->F` @03e8),
  `"Review pending messages before using live mode."` (`Lyv2;->b` @00b2),
  `"Answer the pending question before making a new voice request."` @00a6,
  `"Refresh queued until the current response finishes."` (`Lh30;->o` @009e),
  `"Couldn't keep queued messages: "` (`Lh30;->invokeSuspend` @010f),
  `"The queued attachment owner changed"` (`Lyv2;->y` @0036),
  `"Queueing attachments…"` (`Lyv2;->F` @0530), `"Preparing queued attachments…"`
  (`Lyv2;->I` @01a3), `"Message not sent"` (`Lyv2;->r` @05eb),
  `"Could not stop the current task"` (`Lyv2;->i` @0007).
* Queue bar controls (`Lmm2;->c`): `"Send now"` @01da, `"Edit"` @020f, `"Remove"` @023d,
  stale-state label `"May already be sent"` @0087, `"Next"` @002d, index prefix `"#"` @0032.
* Queue list / editor: `"Show queued messages"` (`Lhgi;->b` @0086), `"Edit queued message"`
  + `"Save"` (`Lhgi;->c` @02f4/@02f6).
* Count text is a **string resource**, not a DEX const: `%d queued message` /
  `%d queued messages` (resources.arsc, entry `chat_queued_count`).
* Composer placeholder: `"Queue a message…"` (`Lzei;->e` @0232) and the compaction variant
  `"Queue a message after compaction"` (`Lzei;->k` @0168).

## 2.6 Working-state row (label + progress) — `Lyv2;->P`

`P(Lc52;Lcw3;)` builds the status row shown above the composer. Verified logic:
* shown only when the chat is live (`Lc52;->c` true) and the composer is not in a modal
  state (`Lcw3;->H` false) and a non-blank field `Lcw3;->b` (`Lwwf;->N0` = isBlank check);
* label priority: if a tool/activity label `Lx32;->b` exists it is used, **truncated to 120
  chars** by `Lwwf;->m1(0x78, s)` @00d1; else, when the newest message is `assistant`
  (`"assistant"` @0078) with non-blank content, the literal **`"Writing"`** @00f1;
* `"tool"` @0126 counts tool rows for the row index; `"user"` @0060 anchors the user turn;
* **progress fraction** @0139-0161: `clamp(d/e, 0f, 1f)` where `Lx32;->d` and `Lx32;->e` are
  Integer counters (`Lib8;->l(0f, x, 1f)`), i.e. fraction = done/total, clamped;
* row model `Lde9(sessionId, bool, bool, bool, Long, label, index, Float progress, Integer)`
  @017a.
Stop action = `session.interrupt` (`Lboe;->G` @00f2, `Ll17;->v` @008d).

Spinner/activity **vocabulary** is the enum `Lu32` = `ChatActivityCaption(text, emoji)`
(`Lu32;->toString` @0004). Texts in enum order (`Lu32;<clinit>`):
Thinking, Pondering, Cogitating, Reasoning, Brewing, Plotting, Scheming, Untangling,
Working, Considering, Mulling, Reflecting, Computing, Hatching, Wrangling, Conjuring,
Noodling, Percolating, Marinating, Spinning up, Checking calendar, Creating event,
Reading reminders, Creating reminder, Completing reminder, Reading health data,
Getting location, Checking devices, Waiting for device, Checking device result,
Running command, Searching web, Reading webpage, Using browser, Reading file,
Editing files, Creating image, Viewing image, Updating tasks, Delegating task,
Delegated task, Waiting for update, Asking a question, Searching, Reading data,
Creating item, Updating item, Removing item, Preparing, Reference response received,
Consulting reference models, Combining reference responses.
*(the paired emoji are stored in the DEX as split surrogate pairs — declared length 2 with
6+ bytes of CESU-8 — so only the BMP ones (☕️ ⚖️ ⚙️ ✅ ❤️ ⏳ ✏️ ❓ ✨) could be read back
reliably; recorded as an unknown, not guessed).*

## 2.7 Inline message actions (exact labels)

`Ljda` is the message-action row builder:
* `b()` @003d `"Copy"`, @0040 `"Message"`, @0043 `"Copied"`;
* `d()` @0077 `"React"`;
* `e()` (assistant row) @0165 `"Branch in new chat"`, @01b9 `"Regenerate"`;
* `f()` (user row) @01b7 `"Edit"`.
Confirm/menu copy in `La92;->c`: @033c `"Regenerate this reply?"`, @03ef `"Edit message"`,
@09c1 `"Open session settings"`.
Reaction sheet title is a **string resource**: `"Add Reaction"` (resources.arsc,
`chat_add_reaction`) — it is *not* a DEX const-string, which is why earlier string mining
missed it.
Emoji catalog ships as **assets**: `assets/emoji/{emotionsAndPeople,animalsAndNature,
activities,items,foodAndDrinks,travellingAndPlaces,symbols,flags}.json`, each
`{"emojis":[{"searchKey":"jackOLantern","string":"🎃","version":0.6,"emojiKeys":[…],
"isSkinToneSupport":bool}], "type":…}` — search matches `searchKey`, grid = category per
file. The clone now bundles these exact files instead of a hand-written list.

## 2.8 Session Settings (labels, order, copy)

Toggle labels in list order — `Lahi;->c` @004d-0060:
`Memory updates`, `Link previews`, `Fold finished turns`, `Device actions`, `Thinking`,
`Tool calls`.
Subtitles (`Lcw2;->c`, mirrored in `Lxb;->f`) in the same order:
* Memory updates → "Shows a note when Hermes updates its memory or skills after a turn."
* Link previews → "Shows a preview card for each web page a reply links to. Previews are loaded by this phone."
* Fold finished turns → "Collapses tool calls and thinking into one row once Hermes has answered."
* Device actions → "Shows calendar, health and other device requests between messages."
* Thinking → "Shows the model’s reasoning before it answers, when the model provides it."
* Tool calls → "Shows the tools Hermes uses between messages."
Global page copy (`Lahi;->b`): @004a `"In New Chats"`, @004d `"Each chat can change these
in its own settings. Chats you already changed keep their choice."`.
Sheet copy (`Lov0;->i`): @04e5 `"Session Settings"`, @045e `"Workspace"`, @04dd `"Root"`,
@05f9 `"Show in this chat"`, @0624 `"Details"`, @0264 `"Settings"`.
Memory note label: `"Memory updated"` (resources.arsc `chat_memory_updated`).

## 2.9 Workspace label — it is an enum, not a path heuristic

`Ledg;<clinit>` defines the workspace presets: @0003 `"Chat Folder"` (name `ChatFolder`),
@000f `"Home Folder"` (name `Home`), @001a `"Custom"`. `Lvy2;->e(String)` @002f returns
`"Home Folder"` for the home preset. The clone's `Regex("^/home/[^/]+$")` guess is replaced
by this three-state model (Chat Folder / Home Folder / Custom).

## 2.10 Context usage card — server-computed, do not compute

`session.context_breakdown` result `SessionContextBreakdownResult` (`Lbpe;<clinit>`):
`categories` (list of `ContextCategory(color, id, label, tokens)` — `Lou3;<clinit>`),
`context_max`, `context_percent`, `context_used`, `estimated_total`, `context_estimated`,
`context_source`, `model`, `context_files`.
→ percent and used/total come from the gateway; the app renders them verbatim.
Related usage model `UsageBar(kind, remaining_display, total_display, spent_display,
pct_used, fill_fraction)` (`Lj8h;<clinit>`).

## 2.11 Sort options (`Loa;<clinit>`)

`Last day` = 86400 s (ordinal 0, name `Day`), `Last 3 days` = 259200 s (1, `ThreeDays`),
`Last week` = 604800 s (2, `Week`).

## 2.12 More-sessions page + select mode (`Lvg1;->i`)

Filter sheet section headers, in order: @0192 `"Show"`, @02c6 `"Project"` / @00b5 `"All
projects"`, @0352 `"Include"`, @042e `"Group"` + @046a `"Group by project"`, @0488
`"Sessions"` + @04b1 `"Select sessions"`; @021b `"Filter"`, @0321 `"Reset filters"`.

## 2.13 New-session sheet (`Lbhi;->c`) and new-chat hero (`Lghi;->d`)

Sheet: @024e `"Start a new session"`, @0253 `"New Channel"`, @0256 `"New Group"`,
@04cd `"New channel"`, @04d3 `"New group"`, @05c3 `"Search agents"`, @0696 `"No agents
yet"`, @06fc `"No matching agents"`, test tag `"new-session-search"` @05b9, `"@"` @0117.
Hero: `"What are we working on"` @01c1 + `" in "` @025a + workspace name (@02b1 tag
`"workspace name"`) + `"?"` @02d6; hint @0369 `"Tap the name to work in a project or
another folder."`.

## 2.4 Colors actually present as constants

| color | site |
|---|---|
| `#FF000022` (background) | `Lj44;` method offsets 0002 and 0011 (`const-wide … #00000000ff000022`) |
| `#FF447CFF` (unread dot / accent) | `Lwc7;` offset 0007 |
No constant exists for `#131331`, `#070727` (table-card header/body) or `#402B43` (stop
circle) anywhere in the three DEX — those stay screenshot-sampled and are flagged as such.

## 2.15 Still not in the binary (recorded as unknowns, not guessed)

* a wire name for the message queue (client-side: `.queue.json` + `chat-queues` +
  `ChatQueuedPrompt`);
* a wire name for editing a sent user message (no `message.edit`);
* a way to delete an ACTIVE session (`session.delete` refuses, error 4023 — live-verified);
* the emoji glyph for each `ChatActivityCaption` (split surrogate pairs in the string pool).

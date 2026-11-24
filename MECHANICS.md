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

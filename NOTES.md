# NOTES.md — contract gaps I had to guess at (phase 1)

> End-to-end verification (2026-11-22): the app was installed on a local Android 15
> emulator and driven against a throwaway mock gateway implementing CONTRACT.md
> (mock lives outside app/, in scratch). Proven in checks/emulator-*.png and the mock's
> RPC log: cookie login → ws-ticket → WS upgrade offering `hermes-gateway-v1,
> hermes-gateway-ticket.<sig>` → client.capabilities → profiles.list →
> session.active_list per profile → short-id `message.start` event renders the live
> "Working" dot on the long-id REST row → "832 more sessions" → offset=100 page 2 →
> profile chip filter → gateway.ping keepalive → auto-reconnect replaying
> session.events.since. The mock asserted the exact /api/sessions query string.
> This validates the clone against CONTRACT.md shapes, not against the real gateway's
> unobserved result shapes (gaps 4–8 below stay open).


CONTRACT.md wire names were followed exactly (verified against the APK's DEX string
pool: `hermes-gateway-v1`, `hermes-gateway-ticket.`, `session.active_list`,
`sessions.changed`, `session.events.since`, `prompt.submit`, `session.create`,
`client.capabilities`, `profiles.list`, `profiles.get_asset`→`profiles/…`,
`clarify.lock`, `gateway.ping`, `/api/sessions`, `/api/profiles`, `/api/status`).
No `turn.*` is used anywhere. Where the contract is silent on a *shape*, I guessed
leniently and list it here.

1. **`POST /auth/password-login` request body** — the endpoint itself is a DEX string
   (CONTRACT.md only says "basic auth on the connect form"). We send JSON
   `{"username","password"}` *and* an `Authorization: Basic` header, so either server
   reading works. Body shape: guessed.
2. **`GET /api/auth/ws-ticket` response shape** — DEX proves the endpoint and error
   strings ("Ticket response has no ticket", "Empty WebSocket ticket", "WebSocket ticket
   request returned HTTP <n>"). We accept the ticket from `ticket`/`ws_ticket`/`value`/
   `token` or a bare JSON string. The `sig` for the `hermes-gateway-ticket.<sig>`
   subprotocol: taken from `sig`/`signature`/`ticket_sig` if present, else the ticket
   itself is reused. Guessed.
3. **Cookie handling** — `hermes_session_rt` never appears in the DEX: the official app
   stores whatever cookies the gateway sets via a cookie jar, not by name. We do the
   same (persist all Set-Cookie for the host; the cookie is kept by the server's own
   attributes). The name from CONTRACT.md is only used as a "have we got a session" hint.
4. **`session.active_list` result shape** — not captured. We walk the result and collect
   every id-ish string (`id`/`session_id`/`live_id`/`short_id`/`sid`, or bare strings),
   dropping entries that carry an explicit false `working|active|live|running` flag.
5. **Short ↔ long session id mapping** — CONTRACT.md documents that live/WS uses short
   ids and REST long ids, but no mapping rule. Rows are matched to active ids by exact
   equality or suffix/prefix match. Wrong matches are possible until captured.
6. **`GET /api/sessions` result shape** — accepted as a bare array or
   `{sessions|items|results:[…]}`; rows read `id|session_id`, `title|name|label`,
   timestamps `updated_at|updated|last_active_at|last_message_at|…` in epoch s/ms/ISO.
   `total|total_count|count` drives the "N more sessions" count.
7. **`GET /api/profiles` result shape** — array of strings or objects
   (`name|profile|id|key`, optional `description`), or `{profiles:{name:{…}}}`.
8. **`sessions.changed` payload** — payload contents not captured; any such event marks
   the tree dirty and silently re-fetches the already-loaded pages.
9. **`profiles.get_asset` result** — assumed base64 PNG under `data|avatar|image|base64|png`
   (CONTRACT.md says "avatar png base64" but not the key); failures fall back to the
   drawn placeholder avatar.
10. **Server requests (`srq-…`)** — phase 1 has no chat UI. `approval` is answered
    `{choice:"deny"}` (vocabulary from CONTRACT.md; only "session" was ever observed
    live, so "deny" itself is a guess); every other capability gets a JSON-RPC
    −32601 error. Real handling is phase 2.
11. **Keepalive** — RESOLVED from DEX (2026-11-24 pass): official heartbeat ticks every
    15 s (`Ldo6;->i`), sends only while the previous `gateway.ping` is un-acked, and after
    3 un-acked intervals throws "No frame from Hermes for N heartbeat intervals" →
    reconnect (`Lco6;->B`/`Len6`). Clone now mirrors this loop. OkHttp's own ping frames
    stay disabled.
12. **Reconnect** — exponential backoff 1→30 s is still our choice (official cadence not
    found); on reconnect we replay `session.events.since` per subscribed session with
    `{session_id, last_seen}` (params live-verified; official `Lco6;->q` @00b0-00ca) and
    do a full reload when `truncated`.
13. **permessage-deflate** — the gateway offers it (server_max_window_bits=12); our
    OkHttp client doesn't negotiate extensions and the connection works without it.
14. **`session.create` result** — ignored; the profile's first page is reloaded after
    the call returns.
15. **Filters/sort panel** — the gap report lists it on the home screen but phase 1's
    scope is connect + session tree; the filter icon is a visible no-op placeholder.
16. **Credential storage** — saved gateways (incl. password) go to SharedPreferences in
    plaintext for phase 1; the official app has a `SecureInstanceStore`. Should be
    hardened (EncryptedSharedPreferences) in a later phase.
17. **app-screenshot check** — Craft Anything's post-build check starts the project's
    built program via the Gradle toolchain launch recipe (`build/libs/*.jar` run with
    `java -jar`) and passes `--screenshot <file.png>`. An Android APK can't be started
    that way, so the project now also builds a small JVM **preview harness**
    (`preview/` module, plain Java/Java2D, no Android deps) whose jar lands in
    `app/build/libs/` and renders the Phase-1 home screen headlessly to PNG. It mirrors
    HomeScreen.kt's layout and Theme.kt's palette against 01-launch.png with the same
    sample data the emulator test used. It is NOT the app — the real deliverable is the
    APK; the harness exists so the screenshot comparison has something to run. The
    harness draws neutral placeholder avatars (gradient squares + eyes, teal droplet)
    instead of the official avatar artwork, a Java2D default font instead of the
    official rounded font, and omits the official's faint bottom illustration.
18. **`/api/auth/providers`, `/auth/native/authorize|token|refresh`, `/auth/logout`** —
    observed in DEX (Nous Portal / OAuth flows) but out of scope for basic-auth phase 1;
    not implemented, not faked.


## LIVE GATEWAY VERIFICATION 2026-11-24 (BigRed gateway :9119, patched build on test phone)
Gaps closed by direct observation against the real gateway (login + ws-ticket + tree all live):
- Gap 1 RESOLVED: POST /auth/password-login body MUST include "provider":"basic" alongside
  username/password. Without it the server 422s (validation fails before auth; no login_failure audit).
- Gap 2 RESOLVED (verb was wrong): ws-ticket is POST /api/auth/ws-ticket with {}. GET 404s
  "No such API endpoint". Response {"ticket":"...","ttl_seconds":30}.
- Gap 4 RESOLVED: session.active_list result = {sessions:[{id:<short 8-hex>, session_key:<long REST id>,
  status:"idle"|"working", current, last_active, started_at, message_count, model, preview, title}]}.
  The profile param is IGNORED (same global list per profile). Live = status != "idle".
- Gap 5 RESOLVED: short<->long id mapping IS the session_key field (short id is not a
  prefix/suffix of the long id - old matching could never work).
- Gap 6 RESOLVED: GET /api/sessions = {sessions:[...], total, limit, offset, storage}; rows carry
  id, title, profile, is_active, last_active, started_at, message_count, unread.
- Gap 7 RESOLVED: GET /api/profiles = {profiles:[{name, path, is_default, model, provider,
  skill_count, gateway_running, description, ...}]}.
App code patched: provider field, POST ws-ticket, status-aware active_list parser + liveIdMap
(short->long via session_key) wired into message.start/complete/reclaimed.


## PHASE 2 (chat rendering) — BUILT + LIVE-VERIFIED 2026-11-24 (v0.2-phase2)
Verified wire flow (capture ev_0e07a669 + live):
- History: GET /api/sessions/<long id>/messages?limit=24&order=latest
  -> {session_id, profile, messages:[{id:int, role:user|assistant|tool, content, tool_call_id, tool_calls:[{id, function:{name,arguments}}]}], pagination}.
  Brand-new sessions 404 on this endpoint - treat as empty history, NOT an error.
- Subscribe: session.resume{session_id:<LONG>, profile, defer_history:true, omit_messages:true, close_on_disconnect:false}
  -> result {info{model,title,...}, message_count, messages, messages_omitted, running, turn_started_at,
     session_id:<SHORT>, session_key:<LONG>, started_at, status, resumed, hydrating}.
- New session: session.create -> {session_id:<SHORT>, stored_session_id:<LONG>, message_count, messages, info} -
  clone opens the chat straight from this.
- Send: prompt.submit{session_id:<SHORT>, profile, text, surface:"hermes-android"} -> {status:"streaming", user_row_id:int}.
- Turn events (all rendered): message.start{turn_id} / thinking.delta{text spinner} / reasoning.delta{text italic} /
  tool.start{tool_id,name,context,args} / tool.complete{tool_id,duration_s,result{output,exit_code}} /
  message.delta{text,turn_id,segment,offset=cumulative chars} / message.complete{text,usage{model,total,...},status} /
  session.title{title} / session.reclaimed.
UI: ChatScreen (header title+model, user bubbles right, assistant left w/ reasoning+tool pills+usage footer,
minimal markdown: **bold**, `code`, fenced blocks - no third-party dep), composer with imePadding.
Known phase-2 limits: no markdown tables/lists, no image/attachment rendering, no scrollback paging
(limit=24 only), no session.close on leave, approval srq still auto-deny (phase 3).


## PHASE 3 (interactive cards) — BUILT + LIVE-VERIFIED 2026-11-24 (v0.3-phase3)
Verified against server source (BigRed /home/jeff/.hermes/hermes-agent/tui_gateway/) + live on test phone:
- session.events.since params {session_id:<short>, last_seen:<seq>}; result {events, latest_seq, truncated,
  count, epoch, open_requests:[{id,method,params}]}. open_requests = still-unanswered srqs, replayable
  EXCEPT preview.act action=vault (params stripped for replay safety). server_requests.py:394.
- approval srq params: command, description, choices (SERVER-sourced ["once","session","always","deny"]),
  allow_permanent, timeout_s. Answer: {id, result:{choice}}. Profile approvals.mode:manual gates terminal;
  safe commands (echo) auto-approved by classifier — rm -rf gated. Countdown ticks from timeout_s.
- clarify srq params: questions[{qid,question,choices,multi_select}]. Answer = TWO steps (matches capture):
  1) C2S clarify.lock {request_id, question_id, answer, profile}  2) once all qids answered:
  {id, result:{answers:{qid:text}}}. Single-select answers on tap; multi-select chips + Other field.
- Profile config is read per NEW session (model swap took effect without gateway restart).
- BUG FOUND+FIXED: replay only ran on WS-reconnect when a chat was already open → after app restart no
  card re-armed. Fix: after session.resume success, call session.events.since(last_seen=0) and feed
  open_requests into addPendingSrq. VERIFIED: kill app mid-approval → reopen session → card back with
  fresh countdown → Allow Once → "ran with your approval, exit code 0".
- TEST SCAFFOLD: link-curator (manual approvals) temporarily pointed at clerk-unsloth local model to
  drive a gated turn; config RESTORED from /tmp/lc-config.bak afterwards.
- LIMITS: multi-question clarify sequential UI untested against a real multi-q payload; global srq inbox
  (cards while chat closed) not built; Allow Once is the only choice exercised (others share the code path).


## PHASE 4 (settings + model sheet) — BUILT + LIVE-VERIFIED 2026-11-24 (v0.4-phase4)
- model.options{profile, session_id?} -> {model, provider, providers:[{slug,name,source,models:[str],
  is_current,authenticated,featured_models,total_models,...}]}. models are plain strings. authenticated:true
  does NOT mean switchable (nous -> 5001 "no API key"; error message is human-readable, show it verbatim).
- Switch = config.set{key:"model", value:"<model> --provider <slug|custom:slug> --session", session_id:<short>}
  -> {key,value,warning,confirm_required,confirm_message,scope:"session"}. provider value: bare slug when
  source=="hermes", else custom:<slug>. Custom endpoint down -> switch STILL ACCEPTED with warning note
  (show note, keep sheet open). Server injects "[System: The active model for this chat has changed to ...]"
  into the transcript; header updates on next resume.
- confirm_required path (expensive models) + confirm_expensive_model:true re-issue: coded, UNTESTED
  (no expensive model configured on this gateway to trigger it).
- Settings screen (gear in home top bar): Gateway URL/status, Profiles, About (versionName via
  packageManager — BuildConfig is not generated by default in AGP 8), Disconnect (vm.disconnect()).
- UI gotchas: ModalBottomSheet fully-expanded drag handle sits under the status bar — swiping it down
  pulls the NOTIFICATION SHADE instead of closing the sheet (adb automation; real touch is fine but
  same region). Sheet scroll via adb swipe is flaky; verify switch paths server-side, not just by UI dump.


## PHASE 5 (static A/B parity, emulator rig) — DONE 2026-11-24 (batches A-D)
Rig: official app (ai.nousresearch.hermes.mobile v1.0.0-44) + clone side-by-side on Clerk emulator-5554,
both through relay :8099 -> SSH tunnel -> BigRed :9119. Vision-diff every screen.
- Home: status-bar insets (topBar was under the clock — taps eaten), floating search pill, 7-row filter
  menu, long-press Pin/Archive/Rename, official settings screen, compose profile sheet.
- Avatars are SERVER FILES (~/.hermes/profiles/*/assets/avatar.png) fetched via WS profiles.get_asset —
  param is `name` NOT `profile` (wrong one -> "name required"). Result {found, mime, data: data-url b64}.
- Default profile displays as "Hermes" EVERYWHERE (home header, compose picker, composer placeholder) —
  no display_name on the server; it's a client-side brand fallback.
- Group order: live group first, then newest session activity desc. Auto-expand groups whose newest
  activity < 24h; user toggle wins (expandedByUser).
- Chat history GET /messages?limit=24&order=latest returns ASCENDING (oldest first) — do NOT reverse.
  Rows carry float `timestamp`, `display_kind:"hidden"` rows (interrupted turns) must be skipped.
- Official collapses a finished turn's consecutive assistant rows into ONE pill: green check (#29A551)
  on #17313F bg, "Ran N commands and made M tool calls" (commands = terminal calls), chevron expands.
- Emulator adb quirks: fast flicks (<=300ms swipes) are IGNORED; use 400ms+ swipes. `input text` drops
  spaces — encode as %s. Screenrecord runs ~8-15 fps (GPU-off emulator).

## PHASE 6 (motion) — DONE 2026-11-24 (batch E)
- Technique: `settings put global animator_duration_scale 10` makes ~300ms anims measurable at the
  emulator's ~15fps screenrecord; measure first-static-gap span, divide by 10; restore with
  `settings delete` (not 1).
- Measured official: group expand 386 ms, collapse 383 ms (symmetric). Clone set to 380/380,
  re-verified 369 ms. Nav push/pop not isolable on emulator (live spinner + lazy-load pollute the
  diff) -> used Pixel-8 frame-verified official numbers 233/266 ms -> clone 240/270.
- Pinned material3:1.4.0 (official ships 1.4.0 spring sheet motion; BOM default was 1.3.x).
  Compiles clean; compose sheet re-verified visually.
- UNRESOLVED: group-header timestamp semantics (5w/1d/blank). Not oldest session (job-logger 23w blank),
  not dir mtime, roster row has no such field. Clone shows newest-session age; left as documented gap.

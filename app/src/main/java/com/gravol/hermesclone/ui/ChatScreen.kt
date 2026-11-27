package com.gravol.hermesclone.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ArrowForwardIos
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gravol.hermesclone.model.ChatMsg
import com.gravol.hermesclone.model.ContextUsage
import com.gravol.hermesclone.model.ModelSheetState
import com.gravol.hermesclone.model.PendingSrq
import com.gravol.hermesclone.model.QueuedPrompt
import com.gravol.hermesclone.model.ToolCard
import com.gravol.hermesclone.state.AppViewModel
import com.gravol.hermesclone.state.ChatState
import com.gravol.hermesclone.state.UiState

/**
 * Chat surface. Wire contract VERIFIED (capture bundle ev_0e07a669 + live probes 2026-11-24/26):
 * history via GET /api/sessions/<long id>/messages, subscribe via session.resume,
 * send idle via prompt.submit{session_id:<short>, surface:"hermes-android"},
 * stop via session.interrupt, react via message.react, steer via session.steer.
 *
 * vc49 parity pass (2026-11-26), derived from official49-*.png shots + live captures:
 *  - header = inline left row (back | avatar + title | More), no floating pill
 *  - empty chat hero: "What are we working on in <Workspace>?" + hint line
 *  - while a turn runs: Thinking/Writing status bar above the composer with an
 *    elapsed timer; placeholder becomes "Queue a message..."; the mic becomes the
 *    stop square (#402B43 circle + cream square); prompts sent while working collect
 *    in a queue bar (steer / edit / drop)
 *  - finished messages get an action row: date + Copy / React / Branch in new chat
 *    (+ Regenerate on the last assistant reply); user pills get Copy / Edit.
 *    The official has NO long-press menu for message actions anymore.
 *  - markdown tables render as rounded cards; reasoning renders under a collapsible
 *    "Thought" heading
 *  - Session Settings sheet (renamed from Chat Settings) with the context-usage card
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: AppViewModel, onBack: () -> Unit) {
    val st by vm.state.collectAsStateWithLifecycle()
    val chat = st.chat
    var chatMore by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    BackHandler { vm.closeChat(); onBack() }

    // A reconnect resync REPLACES the list (often same size — the window slides). Replacing
    // under a pending scrollToItem leaves the shared LazyListState wedged mid-list and
    // unscrollable (VERIFIED 2026-11-27) — so on resyncTick we build a brand-new state and
    // the unconditional initial bottom-scroll below re-arms and lands us at the newest turn.
    // (Always the same remember branch — a conditional remember would crash on recompose.)
    val listState = remember(chat.resyncTick) { androidx.compose.foundation.lazy.LazyListState() }
    var composerBarH by remember { mutableIntStateOf(0) }
    val streamLen = (chat.streaming?.content?.length ?: 0) + (chat.streaming?.reasoning?.length ?: 0)
    val totalItems = chat.messages.size + if (chat.streaming != null) 1 else 0
    // Follow-the-stream intent. canScrollForward alone is wrong: the list GROWS before the
    // effect runs, so a freshly appended message reads as "user scrolled away" and freezes
    // follow forever (vc37 regression). Track user DRAGS instead; re-arm on reaching bottom.
    // Follow-the-stream: rememberViewportEndOffsetForLastItem reads the PREVIOUS layout pass
    // (effects run before layout), so it tells us where the user actually was when the new
    // delta arrived — at-bottom follows, scrolled-up stays put, dragging back resumes.
    // (canScrollForward is wrong: the list grows before the effect runs, so a fresh message
    // reads as scrolled-away and freezes follow — vc37 regression.)
    // Chat open: land at the bottom once, unconditionally (the incremental follow guard below
    // is correct but useless if the chat opened at the top — vc37/38 regression: initial
    // bottom-scroll relied on racing the effect before first layout, which the emulator lost).
    var didInitialScroll by remember(chat.shortId, chat.resyncTick) { mutableStateOf(false) }
    LaunchedEffect(chat.shortId, chat.resyncTick, chat.messages.size) {
        if (!didInitialScroll && chat.messages.isNotEmpty()) {
            listState.scrollToItem(chat.messages.size - 1, 10_000_000)
            didInitialScroll = true
        }
    }
    // lastKey in the key set: a resync can REPLACE the list with the SAME size (window
    // slides: oldest item out, newest in) — size-only keys miss that, the LazyColumn keeps
    // the old index and the view freezes mid-list (VERIFIED via resync logging 2026-11-27).
    LaunchedEffect(chat.messages.size, streamLen, chat.messages.lastOrNull()?.key) {
        // layoutInfo here still reflects the PREVIOUS layout pass (effects run before layout),
        // so it tells us where the user actually was when this delta arrived: last item
        // visible = follow; scrolled away = stay put; drag back into view = resume.
        if (totalItems == 0) return@LaunchedEffect
        // layoutInfo is from the PREVIOUS layout pass: itemCount/lastVisible describe where the
        // user was BEFORE this delta arrived. "At bottom" = last item of the OLD list was visible.
        val info = listState.layoutInfo
        val lastVisible = info.visibleItemsInfo.lastOrNull()
        val atBottom = lastVisible == null || lastVisible.index >= info.totalItemsCount - 1
        if (atBottom) {
            listState.scrollToItem(totalItems - 1, 10_000_000)
        }
    }

    // Keyboard raise/lower resizes the transcript viewport but does NOT re-pin it: the newest
    // text hides under the composer. Re-pin when the bar height changes IF the user was at
    // the bottom in the pre-resize layout pass.
    LaunchedEffect(composerBarH) {
        if (composerBarH == 0) return@LaunchedEffect
        val info = listState.layoutInfo
        val last = info.visibleItemsInfo.lastOrNull()
        if (last != null && last.index >= info.totalItemsCount - 1 && totalItems > 0) {
            listState.scrollToItem(totalItems - 1, 10_000_000)
        }
    }

    val agentName = if (chat.profile.isBlank() || chat.profile == "default") "Hermes" else chat.profile
    // Workspace label = the official preset names (Ledg;<clinit> @0003 "Chat Folder",
    // @000f "Home Folder", @001a "Custom"). The clone can only observe two of the three
    // today: the profile home renders as "Home Folder", anything else as "Custom".
    val workspaceLabel = chat.currentCwd?.takeIf { it.isNotBlank() }?.let { cwd ->
        if (Regex("^/home/[^/]+$").matches(cwd)) "Home Folder" else "Custom"
    } ?: "Home Folder"

    androidx.compose.material3.Scaffold(
        topBar = {
            // vc49 header: inline left row — back | avatar + title | More (no floating pill)
            Row(
                Modifier.fillMaxWidth().statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(HmSurface)
                        .clickable { vm.closeChat(); onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back",
                        tint = HmTextPrimary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                AgentAvatar(name = agentName, bitmap = st.avatars[chat.profile])
                Spacer(Modifier.width(10.dp))
                Text(
                    chat.title.ifBlank { "New session" },
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    color = HmTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { vm.openChatSettings(true) },
                )
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(HmSurface)
                        .clickable { chatMore = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.MoreHoriz, contentDescription = "more",
                        tint = HmTextPrimary, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(
                    expanded = chatMore,
                    onDismissRequest = { chatMore = false },
                    containerColor = Color(0xFF141F3C),
                    shape = RoundedCornerShape(14.dp),
                    tonalElevation = 0.dp,
                ) {
                    // vc49: sheet renamed "Session Settings"
                    ChatMoreItem(Icons.Filled.Tune, "Session Settings") {
                        chatMore = false; vm.openChatSettings(true)
                    }
                    HorizontalDivider(color = HmBorder.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))
                    ChatMoreItem(Icons.Filled.Insights, "Live Mode") { vm.toggleLiveMode() }
                    HorizontalDivider(color = HmBorder.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))
                    ChatMoreItem(Icons.Filled.Terminal, "Terminal") { }
                    ChatMoreItem(Icons.Filled.Code, "Execution") { }
                    HorizontalDivider(color = HmBorder.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))
                    ChatMoreItem(Icons.Filled.History, "Routines") {
                        chatMore = false; vm.openRoutines()
                    }
                    ChatMoreItem(Icons.Filled.Inventory2, "Artifacts") {
                        chatMore = false; vm.openArtifacts()
                    }
                    HorizontalDivider(color = HmBorder.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))
                    ChatMoreItem(Icons.Outlined.Archive, "Archive") {
                        chatMore = false
                        chat.row?.let { vm.setArchived(it, true) }
                        vm.closeChat()
                    }
                    ChatMoreItem(Icons.Filled.Delete, "Delete", color = HmDanger) {
                        chatMore = false
                        vm.openDeleteDialog()
                    }
                }
            }
        },
        bottomBar = {
            ChatComposer(
                vm,
                onBarHeight = { composerBarH = it },
                enabled = chat.shortId != null && !chat.sending,
                agentName = agentName,
                running = chat.running,
                writing = !chat.streaming?.content.isNullOrEmpty(),
                workingSinceMs = chat.workingSinceMs,
                queued = chat.queued,
                spinner = chat.spinner,
                prefill = chat.composerPrefill,
            )
        },
        containerColor = HmBackground,
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (chat.error != null) {
                Text(
                    chat.error!!,
                    color = HmDanger, fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            if (chat.loading) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = HmLiveBlue, modifier = Modifier.size(28.dp))
                }
            } else if (chat.messages.isEmpty() && chat.streaming == null) {
                // vc49 new-chat hero (shot official49-newchat.png):
                // "What are we working on in <Workspace>?" + hint line
                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    AgentAvatar(name = agentName, bitmap = st.avatars[chat.profile], avatarSize = 110.dp) // vc49 hero blob
                    Spacer(Modifier.height(20.dp))
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = HmTextPrimary)) {
                                append("What are we working on in ")
                            }
                            withStyle(SpanStyle(color = HmLiveBlue, fontWeight = FontWeight.Bold)) {
                                append(workspaceLabel)
                            }
                            withStyle(SpanStyle(color = HmTextPrimary)) { append("?") }
                        },
                        fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 30.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Tap the name to work in a project or another folder.",
                        color = HmTextMuted, fontSize = 13.sp, textAlign = TextAlign.Center,
                    )
                }
            } else {
                // Official turn grouping (verified 2026-11-24): consecutive assistant
                // rows between user rows are ONE turn; all their tool calls collapse
                // into a single pill anchored at the first tool-bearing row.
                val turnMap = remember(chat.messages) {
                    val map = HashMap<String, Pair<String, List<ToolCard>>>()
                    val msgs = chat.messages
                    var i = 0
                    while (i < msgs.size) {
                        if (msgs[i].role == "assistant") {
                            var j = i
                            while (j < msgs.size && msgs[j].role == "assistant") j++
                            val run = msgs.subList(i, j).toList()
                            val tools = run.flatMap { it.tools }
                            if (tools.isNotEmpty()) {
                                val anchor = run.first { it.tools.isNotEmpty() }.key
                                run.forEach { map[it.key] = anchor to tools }
                            }
                            i = j
                        } else i++
                    }
                    map
                }
                val lastAssistantKey = chat.messages.lastOrNull { it.role == "assistant" }?.key
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(chat.messages, key = { it.key }) { msg ->
                        val turn = turnMap[msg.key]
                        MessageRow(
                            msg, spinner = "",
                            turnTools = if (turn != null && turn.first == msg.key) turn.second else emptyList(),
                            showTools = (turn == null || turn.first == msg.key) && st.chatPrefs.showToolCalls,
                            showThinking = st.chatPrefs.showThinking,
                            defaultExpandTurn = !st.chatPrefs.foldFinishedTurns,
                            showActions = true,
                            showRegenerate = msg.key == lastAssistantKey,
                            onCopy = {
                                clipboard.setText(AnnotatedString(msg.content))
                                vm.showToast("Copied")
                            },
                            onReact = { vm.openReactionSheet(msg.key) },
                            onBranch = {
                                vm.branchChat(chat.messages.indexOf(msg).takeIf { it >= 0 }?.plus(1))
                            },
                            onRegenerate = { vm.openRegenerateDialog(true) },
                            onEdit = { vm.editMessage(msg) },
                        )
                    }
                    val s = chat.streaming
                    if (s != null) {
                        item(key = "streaming") {
                            MessageRow(s, spinner = chat.spinner,
                                showTools = st.chatPrefs.showToolCalls,
                                showThinking = st.chatPrefs.showThinking,
                                defaultExpandTurn = !st.chatPrefs.foldFinishedTurns)
                        }
                    }
                }
            }
            // Phase 3: pending approval/clarify cards for THIS session, pinned above the composer
            val srqs = st.pendingSrqs.filter {
                chat.shortId != null && (it.sessionId == chat.shortId || it.sessionId == chat.longId)
            }
            if (srqs.isNotEmpty()) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    srqs.forEach { SrqCard(vm, it) }
                }
            }
        }
    }

    if (st.modelSheet.open) ModelSheet(vm, st.modelSheet)
    if (chat.settingsSheet) SessionSettingsSheet(vm, st, chat, workspaceLabel)
    if (chat.renameTarget) ChatRenameDialog(vm, chat.title)

    // vc49: "Add Reaction" emoji sheet (official49-react-picker.png)
    chat.reactionTargetKey?.let { key ->
        ReactionSheet(
            onDismiss = { vm.openReactionSheet(null) },
            onPick = { emoji -> vm.openReactionSheet(null); vm.react(key, emoji) },
            frequent = vm.frequentReactions,
        )
    }

    // vc49: Regenerate confirm (undo + resubmit the last user prompt — NOTES: the
    // official has no distinct regenerate wire method; session.undo is VERIFIED)
    if (chat.regenerateDialog) {
        AlertDialog(
            onDismissRequest = { vm.openRegenerateDialog(false) },
            containerColor = Color(0xFF101A33),
            title = { Text("Regenerate this reply?", color = HmTextPrimary, fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold) },
            text = { Text("The reply is replaced with a new answer.",
                color = HmTextMuted, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = { vm.regenerateLastReply() }) {
                    Text("Regenerate", color = HmLiveBlue, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.openRegenerateDialog(false) }) { Text("Cancel", color = HmTextMuted) }
            },
        )
    }

    // official delete confirm (copy verified 2026-11-24)
    if (st.deleteDialog && chat.row != null) {
        AlertDialog(
            onDismissRequest = { vm.closeDeleteDialog() },
            containerColor = Color(0xFF101A33),
            title = { Text("Delete this conversation?", color = HmTextPrimary, fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold) },
            text = { Text("Its transcript is removed from the server. Archiving keeps it.",
                color = HmTextMuted, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = { vm.deleteSession(chat.row!!); vm.closeChat() }) {
                    Text("Delete", color = HmDanger, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.closeDeleteDialog() }) { Text("Cancel", color = HmLiveBlue) }
            },
        )
    }

    // toast snackbar on chat screen too (archive / copied / branch errors)
    st.toast?.let { msg ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp).padding(top = 96.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xFF0F2A24))
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = HmTurnCheck,
                    modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(msg, color = HmTextPrimary, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun ChatMoreItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String,
                         color: androidx.compose.ui.graphics.Color = HmTextPrimary, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, color = color, fontSize = 15.sp) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp)) },
        onClick = onClick,
    )
}

// ---------------------------------------------------------------------------
// Session Settings sheet — vc49 parity (official49-sessionsettings-1/2.png):
// title "Session Settings"; context-usage card (session.context_breakdown, VERIFIED
// live 2026-11-26); Title / Workspace ("Home Folder") / Profile rows; "Show in this
// chat" with SIX toggles (Memory updates added; Tool calls subtitle reworded).

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionSettingsSheet(vm: AppViewModel, st: UiState, chat: ChatState, workspaceLabel: String) {
    ModalBottomSheet(
        onDismissRequest = { vm.openChatSettings(false) },
        containerColor = HmSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = HmTextMuted) },
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp).padding(bottom = 28.dp)) {
            Text("Session Settings", color = HmTextPrimary, fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 12.dp))
            ContextUsageCard(chat)
            SettingLine("Title", chat.title.ifBlank { "Session" }) { vm.openChatRename(true) }
            SettingLine("Workspace", workspaceLabel)
            SettingLine("Profile", if (chat.profile.isBlank() || chat.profile == "default") "Hermes" else chat.profile)
            Text("Show in this chat", color = HmTextMuted, fontSize = 13.sp,
                modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
            // vc49 reworded subtitle (official49-sessionsettings-2.png)
            // official order (Lahi;->c @004d-@0060) + official subtitles (Lcw2;->c)
            ToggleLine("Memory updates", "Shows a note when Hermes updates its memory or skills after a turn.",
                st.chatPrefs.memoryUpdates) { vm.setChatPref("memory_updates", it) }
            ToggleLine("Link previews", "Shows a preview card for each web page a reply links to. Previews are loaded by this phone.",
                st.chatPrefs.linkPreviews) { vm.setChatPref("link_previews", it) }
            ToggleLine("Fold finished turns", "Collapses tool calls and thinking into one row once Hermes has answered.",
                st.chatPrefs.foldFinishedTurns) { vm.setChatPref("fold_turns", it) }
            ToggleLine("Device actions", "Shows calendar, health and other device requests between messages.",
                st.chatPrefs.deviceActions) { vm.setChatPref("device_actions", it) }
            ToggleLine("Thinking", "Shows the model\u2019s reasoning before it answers, when the model provides it.",
                st.chatPrefs.showThinking) { vm.setChatPref("thinking", it) }
            ToggleLine("Tool calls", "Shows the tools Hermes uses between messages.",
                st.chatPrefs.showToolCalls) { vm.setChatPref("tool_calls", it) }
            HorizontalDivider(color = HmBorder.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 10.dp))
            SettingLine("Session details", null, chevron = true) {
                vm.openChatSettings(false); vm.openModelSheet()
            }
        }
    }
}

/** Context-usage card (official49-sessionsettings-1.png): "135K of 500K" big number +
 *  percent + bar + category dots. Data from session.context_breakdown (VERIFIED live). */
@Composable
private fun ContextUsageCard(chat: ChatState) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0B1734)).padding(14.dp),
    ) {
        if (chat.contextLoading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = HmLiveBlue)
                Spacer(Modifier.width(10.dp))
                Text("Loading usage…", color = HmTextMuted, fontSize = 13.sp)
            }
        }
        val c = chat.context
        if (c != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(fmtTokens(c.used), color = HmTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(" of ${fmtTokens(c.max)}", color = HmTextMuted, fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 4.dp))
                Spacer(Modifier.weight(1f))
                Text("${c.percent}%", color = HmLiveBlue, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            // custom bar (avoids material3 progress API churn across versions)
            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(HmSurface)) {
                Box(Modifier.fillMaxWidth(fraction = (c.percent / 100f).coerceIn(0f, 1f))
                    .height(6.dp).clip(CircleShape).background(HmLiveBlue))
            }
            if (c.categories.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    c.categories.forEach { cat ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(parseHex(cat.color)))
                            Spacer(Modifier.width(5.dp))
                            Text(cat.label, color = HmTextMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun fmtTokens(n: Int): String = when {
    n >= 1_000_000 -> "${n / 1_000_000}M"
    n >= 1000 -> "${n / 1000}K"
    else -> "$n"
}

private fun parseHex(s: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(if (s.startsWith("#")) s else "#$s"))
}.getOrDefault(HmLiveBlue)

@Composable
private fun SettingLine(label: String, value: String?, chevron: Boolean = false, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clip(RoundedCornerShape(10.dp)).clickable { onClick() } else Modifier)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = HmTextMuted, fontSize = 12.sp)
            if (value != null) Text(value, color = HmTextPrimary, fontSize = 15.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
        if (chevron) Icon(Icons.Outlined.ChevronRight, null, tint = HmTextMuted,
            modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ToggleLine(label: String, desc: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = HmTextPrimary, fontSize = 15.sp)
            Text(desc, color = HmTextMuted, fontSize = 12.sp, maxLines = 2,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
        Switch(checked = checked, onCheckedChange = onChecked,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedTrackColor = HmLiveBlue, checkedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFF2A3556), uncheckedThumbColor = Color(0xFF8A8AA6),
                uncheckedBorderColor = Color.Transparent,
            ))
    }
}

@Composable
private fun ChatRenameDialog(vm: AppViewModel, current: String) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = { vm.openChatRename(false) },
        containerColor = HmSurface,
        title = { Text("Rename session", color = HmTextPrimary) },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it },
                singleLine = true, colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = HmTextPrimary, unfocusedTextColor = HmTextPrimary,
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                ))
        },
        confirmButton = {
            TextButton(onClick = { vm.renameChat(text) }) { Text("Save", color = HmLiveBlue) }
        },
        dismissButton = {
            TextButton(onClick = { vm.openChatRename(false) }) { Text("Cancel", color = HmTextMuted) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSheet(vm: AppViewModel, ms: ModelSheetState) {
    ModalBottomSheet(onDismissRequest = { vm.closeModelSheet() }, containerColor = HmSurface) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text("Models", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = HmTextPrimary)
            if (ms.currentModel.isNotBlank())
                Text("Current: ${ms.currentModel} \u00b7 ${ms.currentProvider}",
                    fontSize = 12.sp, color = HmTextMuted)
            if (ms.loading) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = HmLiveBlue, modifier = Modifier.size(24.dp))
                }
            }
            ms.providers.forEach { p ->
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { vm.toggleProvider(p.slug) }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontSize = 14.sp, color = HmTextPrimary)
                        Text("${p.models.size} models", fontSize = 11.sp, color = HmTextMuted)
                    }
                    if (p.isCurrent) Text("current", fontSize = 11.sp, color = HmLiveBlue)
                    Spacer(Modifier.width(6.dp))
                    Text(if (ms.expanded == p.slug) "▴" else "▾",
                        fontSize = 14.sp, color = HmTextMuted)
                }
                if (ms.expanded == p.slug) {
                    p.models.forEach { m ->
                        Row(
                            Modifier.fillMaxWidth()
                                .padding(start = 16.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = !ms.busy) { vm.pickModel(p, m) }
                                .padding(horizontal = 8.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                m, fontSize = 13.sp,
                                color = if (m == ms.currentModel) HmLiveBlue else HmTextPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            if (m == ms.currentModel)
                                Icon(Icons.Filled.Check, contentDescription = null,
                                    tint = HmLiveBlue, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            if (ms.busy) {
                Row(
                    Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(color = HmLiveBlue, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("Switching\u2026", fontSize = 12.sp, color = HmTextMuted)
                }
            }
            ms.note?.let {
                Text(it, fontSize = 12.sp,
                    color = if (ms.pending != null) HmPurple else HmDanger,
                    modifier = Modifier.padding(vertical = 6.dp))
            }
            if (ms.pending != null) {
                TextButton(
                    onClick = { vm.confirmPendingModel() },
                    enabled = !ms.busy,
                    modifier = Modifier.align(Alignment.End),
                ) { Text("Confirm switch", fontSize = 13.sp, color = HmLiveBlue) }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun SrqCard(vm: AppViewModel, srq: PendingSrq) {
    var remaining by remember(srq.id) { mutableStateOf(srq.timeoutS) }
    LaunchedEffect(srq.id) {
        while (remaining > 0) { kotlinx.coroutines.delay(1_000); remaining-- }
        vm.dismissSrq(srq.id) // the server auto-times-out the request itself; just clear the card
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(HmSurface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (srq.method) {
            "approval" -> ApprovalBody(vm, srq, remaining)
            "clarify" -> ClarifyBody(vm, srq)
        }
    }
}

@Composable
private fun ApprovalBody(vm: AppViewModel, srq: PendingSrq, remaining: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Approval requested", color = HmLiveBlue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text("${remaining}s", color = HmTextMuted, fontSize = 11.sp)
    }
    Text(
        srq.command,
        color = HmTextPrimary, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
        maxLines = 3, overflow = TextOverflow.Ellipsis,
    )
    if (srq.description.isNotBlank()) {
        Text(srq.description, color = HmTextMuted, fontSize = 11.sp)
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        srq.choices.filterNot { it == "always" && !srq.allowPermanent }.forEach { c ->
            val label = when (c) {
                "deny" -> "Deny"
                "once" -> "Allow Once"
                "session" -> "Allow for Session"
                "always" -> "Always Allow"
                else -> c
            }
            TextButton(
                onClick = { vm.answerApproval(srq.id, c) },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp, 4.dp),
            ) {
                Text(
                    label, fontSize = 11.sp,
                    color = if (c == "deny") HmDanger else HmLiveBlue,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClarifyBody(vm: AppViewModel, srq: PendingSrq) {
    val answered = remember(srq.id) { mutableStateListOf<String>() }
    val q = srq.questions.firstOrNull { it.qid !in answered } ?: srq.questions.firstOrNull() ?: return
    var custom by remember(srq.id + q.qid) { mutableStateOf("") }
    val selected = remember(srq.id + q.qid) { mutableStateListOf<String>() }

    Text("Clarify", color = HmLiveBlue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    Text(q.question, color = HmTextPrimary, fontSize = 14.sp)

    if (q.choices.isNotEmpty()) {
        if (q.multiSelect) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                q.choices.forEach { ch ->
                    FilterChip(
                        selected = ch in selected,
                        onClick = { if (ch in selected) selected.remove(ch) else selected.add(ch) },
                        label = { Text(ch, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = HmRowHighlight,
                            labelColor = HmTextPrimary,
                            selectedLabelColor = HmTextPrimary,
                        ),
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = {
                        val ans = (selected.toList() + custom.takeIf { it.isNotBlank() }.orEmpty())
                            .joinToString(", ")
                        if (ans.isNotBlank()) {
                            answered.add(q.qid)
                            vm.answerClarify(srq.id, q.qid, ans)
                        }
                    },
                ) { Text("Submit", fontSize = 12.sp, color = HmLiveBlue) }
            }
        } else {
            // single select: tapping a choice answers immediately (official behavior)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                q.choices.forEach { ch ->
                    Text(
                        ch,
                        color = HmLiveBlue, fontSize = 13.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(HmToolPill)
                            .clickable {
                                answered.add(q.qid)
                                vm.answerClarify(srq.id, q.qid, ch)
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
    Row(verticalAlignment = Alignment.Bottom) {
        OutlinedTextField(
            value = custom,
            onValueChange = { custom = it },
            placeholder = { Text("Other…", fontSize = 12.sp, color = HmTextMuted) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = HmLiveBlue, unfocusedBorderColor = HmBorder,
                focusedTextColor = HmTextPrimary, unfocusedTextColor = HmTextPrimary,
            ),
        )
        Spacer(Modifier.width(6.dp))
        TextButton(
            onClick = {
                if (custom.isNotBlank()) {
                    answered.add(q.qid)
                    vm.answerClarify(srq.id, q.qid, custom.trim())
                }
            },
            enabled = custom.isNotBlank(),
        ) { Text("Send", fontSize = 12.sp, color = if (custom.isNotBlank()) HmLiveBlue else HmTextMuted) }
    }
}


/** Minimal markdown: fenced ``` blocks, **bold**, `inline code`, and — vc49 —
 *  pipe tables rendered as rounded cards (header #131331 / body #070727, centered
 *  columns) instead of raw pipe text (shot official49-chat-scroll2.png). */
@Composable
private fun MdText(raw: String) {
    val blocks = raw.split("```")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEachIndexed { i, block ->
            if (i % 2 == 1) {
                // fenced code block — first line may be the language tag
                val lines = block.lines()
                val code = if (lines.firstOrNull()?.matches(Regex("[a-zA-Z0-9+#-]*")) == true)
                    lines.drop(1).joinToString("\n") else block.trim()
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(HmToolPill)
                        .padding(10.dp),
                ) {
                    Text(code, color = HmTextPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            } else {
                // non-code block: walk lines, collecting pipe-table runs into cards
                val lines = block.lines()
                var j = 0
                while (j < lines.size) {
                    val l = lines[j].trim()
                    if (l.startsWith("|") && j + 1 < lines.size && isTableSep(lines[j + 1].trim())) {
                        val header = splitTableRow(l)
                        j += 2
                        val body = mutableListOf<List<String>>()
                        while (j < lines.size && lines[j].trim().startsWith("|")) {
                            body.add(splitTableRow(lines[j].trim()))
                            j++
                        }
                        TableCard(header, body)
                        Spacer(Modifier.height(4.dp))
                    } else {
                        // markdown heading (# … ######): strip markers, render bold+larger
                        val h = Regex("^(#{1,6})\\s+(.*)$").find(l)
                        when {
                            h != null -> {
                                val level = h.groupValues[1].length
                                Text(inlineMd(h.groupValues[2]), color = HmTextPrimary,
                                    fontSize = (19 - level).coerceIn(14, 18).sp,
                                    fontWeight = FontWeight.Bold, lineHeight = 24.sp,
                                    modifier = Modifier.padding(top = 4.dp))
                            }
                            l.isNotBlank() -> Text(inlineMd(l), color = HmTextPrimary, fontSize = 14.sp, lineHeight = 20.sp)
                        }
                        j++
                    }
                }
            }
        }
    }
}

private fun isTableSep(line: String): Boolean =
    line.startsWith("|") && line.endsWith("|") &&
        line.trim('|').split('|').all { it.trim().matches(Regex(":?-{2,}:?")) }

private fun splitTableRow(line: String): List<String> =
    line.trim().trim('|').split('|').map { it.trim() }

/** vc49 markdown table card: rounded, header row #131331, body #070727, columns centered. */
@Composable
private fun TableCard(header: List<String>, body: List<List<String>>) {
    val cols = header.size
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))) {
        Row(Modifier.fillMaxWidth().background(HmTableHeader)) {
            header.forEach { cell ->
                Text(cell, color = HmTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f).padding(10.dp))
            }
        }
        body.forEach { row ->
            Row(Modifier.fillMaxWidth().background(HmTableBody)) {
                (0 until cols).forEach { c ->
                    Text(row.getOrElse(c) { "" }, color = HmTextPrimary, fontSize = 13.sp,
                        textAlign = TextAlign.Center, modifier = Modifier.weight(1f).padding(10.dp))
                }
            }
        }
    }
}

@Composable
private fun inlineMd(line: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < line.length) {
        when {
            // [label](url) → label in blue (official renders links tautly; opening the
            // URL is a browser intent — NOTES: not wired to keep this pass UI-only)
            line[i] == '[' -> {
                val close = line.indexOf("](", i + 1)
                val end = if (close > 0) line.indexOf(')', close + 2) else -1
                if (end > 0) {
                    withStyle(SpanStyle(color = HmLiveBlue)) { append(line.substring(i + 1, close)) }
                    i = end + 1
                } else { append(line[i]); i++ }
            }
            line.startsWith("**", i) -> {
                val end = line.indexOf("**", i + 2)
                if (end > 0) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(line.substring(i + 2, end))
                    }
                    i = end + 2
                } else { append(line[i]); i++ }
            }
            line[i] == '`' -> {
                val end = line.indexOf('`', i + 1)
                if (end > 0) {
                    withStyle(SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = HmToolPill,
                    )) { append(line.substring(i + 1, end)) }
                    i = end + 1
                } else { append(line[i]); i++ }
            }
            else -> { append(line[i]); i++ }
        }
    }
}

@Composable
private fun MessageRow(
    msg: ChatMsg,
    spinner: String,
    turnTools: List<ToolCard> = emptyList(),
    showTools: Boolean = true,
    showThinking: Boolean = true,
    defaultExpandTurn: Boolean = false,
    showActions: Boolean = false,
    showRegenerate: Boolean = false,
    onCopy: () -> Unit = {},
    onReact: () -> Unit = {},
    onBranch: () -> Unit = {},
    onRegenerate: () -> Unit = {},
    onEdit: () -> Unit = {},
) {
    if (msg.role == "user") {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            Box(
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(HmUserBubble)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Text(msg.content, color = HmTextPrimary, fontSize = 16.sp, lineHeight = 23.sp)
            }
            if (msg.reactions.isNotEmpty()) ReactionChips(msg.reactions.map { it.emoji })
            if (showActions) {
                // vc49 user-pill actions: Copy + Edit (no long-press menu anymore)
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp, end = 2.dp)) {
                    MsgAction("Copy", Icons.Filled.ContentCopy, onCopy)
                    MsgAction("Edit", Icons.Filled.Edit, onEdit)
                }
            }
            msg.ts?.let {
                Text(
                    userStamp(it), color = HmTextMuted, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp, end = 4.dp),
                )
            }
        }
        return
    }
    // assistant
    Column(Modifier.fillMaxWidth()) {
        // vc49: thoughts render inline under a collapsible "Thought" heading (gray),
        // not a separate card (shot official49-chat-scroll2.png). Default collapsed.
        if (showThinking && msg.reasoning.isNotBlank()) {
            // Official behavior (Jeff 2026-11-27): while streaming, the thought is OPEN by
            // default, capped to ~4 lines, and auto-scrolls internally as reasoning arrives;
            // tap ANYWHERE on the card to collapse; auto-collapses when the turn completes.
            val thinking = msg.streaming
            var open by remember(msg.key) { mutableStateOf(thinking) }
            LaunchedEffect(thinking) { if (!thinking) open = false }
            val thinkScroll = rememberScrollState()
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .clickable { open = !open },
            ) {
                Row(
                    Modifier.padding(vertical = 4.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null, tint = Color(0xFF8A8AA6), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Thought", color = Color(0xFF8A8AA6), fontSize = 13.sp)
                }
                AnimatedVisibility(visible = open) {
                    if (thinking) {
                        Box(
                            Modifier.heightIn(max = 78.dp)
                                .padding(start = 20.dp, bottom = 4.dp)
                                .verticalScroll(thinkScroll),
                        ) {
                            Text(
                                msg.reasoning,
                                color = Color(0xFF8A8AA6), fontSize = 12.sp, fontStyle = FontStyle.Italic,
                            )
                        }
                        LaunchedEffect(msg.reasoning.length) {
                            thinkScroll.animateScrollTo(10_000_000)
                        }
                    } else {
                        Text(
                            msg.reasoning,
                            color = Color(0xFF8A8AA6), fontSize = 12.sp, fontStyle = FontStyle.Italic,
                            modifier = Modifier.padding(start = 20.dp, bottom = 4.dp),
                        )
                    }
                }
            }
        }
        if (showTools && (turnTools.isNotEmpty() || msg.tools.isNotEmpty())) {
            TurnPill(if (turnTools.isNotEmpty()) turnTools else msg.tools,
                key = msg.key, defaultExpanded = defaultExpandTurn,
                modifier = Modifier.padding(bottom = 6.dp))
        }
        if (msg.content.isNotEmpty()) {
            MdText(msg.content)
        }
        if (msg.streaming) {
            val blink by rememberInfiniteTransition(label = "cursor").animateFloat(
                initialValue = 1f, targetValue = 0.15f,
                animationSpec = infiniteRepeatable(
                    tween(530, easing = FastOutSlowInEasing), RepeatMode.Reverse,
                ),
                label = "blink",
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (msg.content.isEmpty()) {
                    CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp, color = HmLiveBlue)
                    Spacer(Modifier.width(8.dp))
                } else {
                    Text("▌", color = HmLiveBlue.copy(alpha = blink), fontSize = 14.sp)
                }
                if (spinner.isNotBlank()) {
                    Text(spinner, color = HmTextMuted, fontSize = 11.sp, fontStyle = FontStyle.Italic)
                }
            }
        }
        if (msg.usageLine != null) {
            // Official collapsed-turn row: "Worked for Ns · M tools  >" + divider.
            Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { /* expand turn detail: phase 4 (NOTES) */ }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(msg.usageLine, color = HmTextMuted, fontSize = 13.sp,
                        modifier = Modifier.weight(1f))
                    Icon(Icons.Outlined.ArrowForwardIos, contentDescription = null,
                        tint = HmTextMuted, modifier = Modifier.size(12.dp))
                }
                HorizontalDivider(color = HmBorder.copy(alpha = 0.35f), thickness = 1.dp)
            }
        }
        if (msg.reactions.isNotEmpty()) ReactionChips(msg.reactions.map { it.emoji })
        if (showActions && !msg.streaming) {
            // vc49 assistant action row: date + Copy / React / Branch in new chat
            // (+ Regenerate on the last assistant message). Accessibility labels are
            // exactly these strings (verified on official 2026-11-26).
            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp)) {
                Text(msg.ts?.let { userStamp(it) } ?: "", color = HmTextMuted, fontSize = 12.sp,
                    modifier = Modifier.padding(end = 6.dp))
                MsgAction("Copy", Icons.Filled.ContentCopy, onCopy)
                MsgAction("React", Icons.Filled.EmojiEmotions, onReact)
                MsgAction("Branch in new chat", Icons.Filled.CallSplit, onBranch)
                if (showRegenerate) MsgAction("Regenerate", Icons.Filled.Refresh, onRegenerate)
            }
        }
    }
}

/** Small icon action with the exact official accessibility label. */
@Composable
private fun MsgAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
        Icon(icon, contentDescription = label, tint = HmTextMuted, modifier = Modifier.size(16.dp))
    }
}

/** Reaction chips under a message (persisted via display_metadata.reactions). */
@Composable
private fun ReactionChips(emojis: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(top = 4.dp)) {
        emojis.distinct().forEach { e ->
            Box(Modifier.clip(RoundedCornerShape(10.dp)).background(HmSurface)
                .padding(horizontal = 7.dp, vertical = 3.dp)) {
                Text(e, fontSize = 13.sp)
            }
        }
    }
}

/** Official user-row stamp: "Nov 23, 8:59 PM" */
private fun userStamp(epochSec: Long): String =
    java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.US)
        .format(java.util.Date(epochSec * 1000))

/**
 * Official collapses a finished turn's tool calls into ONE row:
 * green check circle + "Ran N commands and made M tool calls" + chevron;
 * tap expands the individual tool rows. Live turns show a blue spinner.
 * (commands = terminal calls; everything else counts as a tool call)
 */
@Composable
private fun TurnPill(tools: List<ToolCard>, key: String, defaultExpanded: Boolean = false,
                     modifier: Modifier = Modifier) {
    var expanded by remember(key) { mutableStateOf(defaultExpanded) }
    val commands = tools.count { it.name == "terminal" }
    val others = tools.size - commands
    val allDone = tools.all { it.done }
    val label = buildString {
        append("Ran ")
        append(if (commands == 1) "1 command" else "$commands commands")
        if (others > 0) {
            append(" and made ")
            append(if (others == 1) "1 tool call" else "$others tool calls")
        }
    }
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(HmTurnPill)
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (allDone) {
                Box(
                    Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(HmTurnCheck),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null,
                        tint = Color(0xFFEAF7EE), modifier = Modifier.size(12.dp))
                }
            } else {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = HmLiveBlue)
            }
            Spacer(Modifier.width(10.dp))
            Text(label, color = HmTextPrimary, fontSize = 14.sp,
                modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ExpandMore, contentDescription = null,
                tint = HmTextMuted, modifier = Modifier.size(20.dp))
        }
        AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                tools.forEach { ToolPill(it) }
            }
        }
    }
}

@Composable
private fun ToolPill(t: ToolCard) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(HmToolPill)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (!t.done) HmLiveBlue else if (t.ok != false) HmGreen else HmDanger),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            buildString {
                append(if (t.done) "Ran " else "Using ")
                append(t.name)
                if (t.context.isNotBlank()) {
                    append(": ")
                    append(t.context.take(80))
                }
                if (t.done) t.durationS?.let { append(" · %.1fs".format(it)) }
            },
            color = HmTextPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * vc49 composer (official49-chat2.png / official49-chat-idle.png):
 *  - above the pill, while running: full-width status bar (no fill) — spinner +
 *    "Thinking…"/"Writing…" + elapsed timer
 *  - above the pill: queue bar for prompts collected while a turn runs
 *    (steer-now / edit / drop controls)
 *  - placeholder "Message <agent>…" idle → "Queue a message..." while running
 *  - the mic button becomes the stop square (#402B43 circle + cream square) while
 *    running; send circle hidden while running (the stop square is the action)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatComposer(
    vm: AppViewModel,
    onBarHeight: (Int) -> Unit = {},
    enabled: Boolean,
    agentName: String,
    running: Boolean,
    writing: Boolean,
    workingSinceMs: Long?,
    queued: List<QueuedPrompt>,
    spinner: String,
    prefill: String?,
) {
    var text by remember { mutableStateOf("") }
    // Edit (queued or user pill) pushes text back into the composer
    LaunchedEffect(prefill) {
        if (!prefill.isNullOrEmpty()) { text = prefill; vm.consumeComposerPrefill() }
    }
    // elapsed timer for the working-status bar
    var now by remember(workingSinceMs) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(workingSinceMs) {
        if (workingSinceMs != null) {
            while (true) { now = System.currentTimeMillis(); kotlinx.coroutines.delay(1000) }
        }
    }
    Column(
        Modifier.fillMaxWidth().imePadding().background(HmBackground)
            .onSizeChanged { onBarHeight(it.height) }
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        // queue bar (official: prompts sent while working collect here)
        if (queued.isNotEmpty()) {
            Column(Modifier.padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // resources.arsc chat_queued_count: "%d queued message" / "%d queued messages"
                Text(if (queued.size == 1) "1 queued message" else "${queued.size} queued messages",
                    color = HmTextMuted, fontSize = 11.sp, modifier = Modifier.padding(start = 6.dp))
                queued.forEach { q ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(HmSurface.copy(alpha = 0.65f))
                            .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(q.text, color = HmTextPrimary, fontSize = 13.sp, maxLines = 2,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(6.dp))
                        IconButton(onClick = { vm.steerQueued(q.id) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Send now",
                                tint = HmLiveBlue, modifier = Modifier.size(15.dp))
                        }
                        IconButton(onClick = { vm.editQueued(q.id) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit",
                                tint = HmTextMuted, modifier = Modifier.size(15.dp))
                        }
                        IconButton(onClick = { vm.dropQueued(q.id) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove",
                                tint = HmTextMuted, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
        }
        // working-status bar: spinner + Thinking/Writing + elapsed, no fill
        if (running) {
            val secs = ((now - (workingSinceMs ?: now)) / 1000).coerceAtLeast(0)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = HmLiveBlue)
                Spacer(Modifier.width(10.dp))
                // official Lyv2;->P: label = the activity caption truncated to 120 chars
                // (Lwwf;->m1(0x78, s) @00d1), else the literal "Writing" @00f1 — no ellipsis
                Text(spinner.ifBlank { if (writing) "Writing" else "Thinking" }.take(120),
                    color = HmTextPrimary, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Text(if (secs >= 60) "${secs / 60}m %02ds".format(secs % 60) else "${secs}s",
                    color = HmTextMuted, fontSize = 13.sp)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(HmSurface.copy(alpha = 0.55f))
                    .padding(start = 4.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(HmAccentCircle.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add attachment",
                        tint = HmTextPrimary, modifier = Modifier.size(20.dp))
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = {
                        Text(if (running) "Queue a message..." else "Message $agentName\u2026",
                            color = Color(0xFF6C6F7B), fontSize = 15.sp)
                    },
                    // official queues via the keyboard's send action while a turn runs
                    // (the in-pill button is the stop square, so Enter is the queue key).
                    // NOTE: this compose version's KeyboardOptions factory takes positional
                    // (capitalization, autoCorrect, ...); named imeOptions only resolves positionally.
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                        autoCorrect = true,
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
                        imeAction = if (running)
                            androidx.compose.ui.text.input.ImeAction.Send
                        else androidx.compose.ui.text.input.ImeAction.Default,
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSend = {
                            val t = text.trim()
                            if (t.isNotEmpty()) { vm.sendPrompt(t); text = "" }
                        },
                    ),
                    singleLine = running,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(max = 140.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        cursorColor = HmPurple,
                        focusedTextColor = HmTextPrimary,
                        unfocusedTextColor = HmTextPrimary,
                    ),
                    shape = RoundedCornerShape(22.dp),
                    maxLines = 5,
                )
                if (running) {
                    // vc49: mic becomes the stop square — #402B43 circle + cream square
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(HmStopCircle)
                            .clickable { vm.stopTurn() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(13.dp).clip(RoundedCornerShape(3.dp)).background(HmStopSquare))
                    }
                } else if (text.isNotBlank()) {
                    // official has NO send circle outside the pill — the right-edge
                    // circle becomes the send action when the field has text
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(HmLiveBlue)
                            .alpha(if (enabled) 1f else 0.55f)
                            .clickable {
                                val t = text.trim()
                                if (t.isNotEmpty() && enabled) {
                                    vm.sendPrompt(t)
                                    text = ""
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Send",
                            tint = Color.White, modifier = Modifier.size(19.dp))
                    }
                } else {
                    // official idle: filled blue circle with white mic (voice capture not wired yet)
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(HmLiveBlue)
                            .clickable { /* voice capture: NOTES gap */ },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Mic, contentDescription = "Voice",
                            tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

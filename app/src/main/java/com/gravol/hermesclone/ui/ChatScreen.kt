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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ArrowForwardIos
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gravol.hermesclone.model.ChatMsg
import com.gravol.hermesclone.model.ModelSheetState
import com.gravol.hermesclone.model.PendingSrq
import com.gravol.hermesclone.model.ToolCard
import com.gravol.hermesclone.state.AppViewModel


/**
 * Phase 2 chat surface. Wire contract VERIFIED (capture bundle ev_0e07a669 + live 2026-11-24):
 * history via GET /api/sessions/<long id>/messages, subscribe via session.resume,
 * send via prompt.submit{session_id:<short>, surface:"hermes-android"},
 * render message.start → thinking.delta / reasoning.delta / tool.* → message.delta(offset)
 * → message.complete{usage}.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: AppViewModel, onBack: () -> Unit) {
    val st by vm.state.collectAsStateWithLifecycle()
    val chat = st.chat
    var chatMore by remember { mutableStateOf(false) }
    BackHandler { vm.closeChat(); onBack() }

    val listState = rememberLazyListState()
    val streamLen = chat.streaming?.content?.length ?: 0
    val totalItems = chat.messages.size + if (chat.streaming != null) 1 else 0
    LaunchedEffect(chat.messages.size, streamLen) {
        if (totalItems > 0) listState.scrollToItem((totalItems - 1).coerceAtLeast(0))
    }

    Scaffold(
        topBar = {
            // Official chat header: circular back (left), agent avatar + title pill
            // centered, "…" circle (right).
            Box(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .align(Alignment.CenterStart)
                        .clip(CircleShape)
                        .background(HmSurface)
                        .clickable { vm.closeChat(); onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back",
                        tint = HmTextPrimary, modifier = Modifier.size(20.dp))
                }
                // Official: title pill overlaps the avatar's lower edge; no model chip in
                // the header (model lives in Chat Settings — pill tap opens the sheet).
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 60.dp),
                ) {
                    Box(Modifier.align(Alignment.TopCenter)) {
                        AgentAvatar(name = chat.profile.ifBlank { "Hermes" },
                            bitmap = st.avatars[chat.profile])
                    }
                    Text(
                        chat.title.ifBlank { "Session" },
                        fontSize = 14.sp,
                        color = HmTextPrimary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = 22.dp)
                            .widthIn(max = 240.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(HmSurface)
                            .clickable { vm.openModelSheet() }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .align(Alignment.CenterEnd)
                        .clip(CircleShape)
                        .background(HmSurface)
                        .clickable { chatMore = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.MoreHoriz, contentDescription = "more",
                        tint = HmTextPrimary, modifier = Modifier.size(20.dp))
                }
                // official chat more-menu (verified vs official 2026-11-24):
                // Chat Settings / Live Mode / Terminal / Execution / Routines / Artifacts /
                // Bot Screen / Archive / Delete(red). Only Archive/Delete/Live Mode are wired.
                DropdownMenu(
                    expanded = chatMore,
                    onDismissRequest = { chatMore = false },
                    containerColor = Color(0xFF141F3C),
                    shape = RoundedCornerShape(14.dp),
                    tonalElevation = 0.dp,
                ) {
                    ChatMoreItem(Icons.Filled.Tune, "Chat Settings") { }
                    HorizontalDivider(color = HmBorder.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))
                    ChatMoreItem(Icons.Filled.Insights, "Live Mode") { vm.toggleLiveMode() }
                    HorizontalDivider(color = HmBorder.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))
                    ChatMoreItem(Icons.Filled.Terminal, "Terminal") { }
                    ChatMoreItem(Icons.Filled.Code, "Execution") { }
                    HorizontalDivider(color = HmBorder.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))
                    ChatMoreItem(Icons.Filled.History, "Routines") { }
                    ChatMoreItem(Icons.Filled.Inventory2, "Artifacts") { }
                    ChatMoreItem(Icons.Filled.DesktopWindows, "Bot Screen") { }
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
                enabled = chat.shortId != null && !chat.sending,
                agentName = if (chat.profile.isBlank() || chat.profile == "default") "Hermes" else chat.profile,
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
                            showTools = turn == null || turn.first == msg.key,
                        )
                    }
                    val s = chat.streaming
                    if (s != null) {
                        item(key = "streaming") { MessageRow(s, spinner = chat.spinner) }
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

    // toast snackbar on chat screen too (archive)
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


/** Minimal markdown: fenced ``` blocks, **bold**, `inline code`. Official renders full
 * markdown; this covers the common cases without a third-party dep (phase 2). */
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
                block.lineSequence().filter { it.isNotBlank() }.forEach { line ->
                    Text(inlineMd(line.trim()), color = HmTextPrimary, fontSize = 14.sp, lineHeight = 20.sp)
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
            msg.ts?.let {
                Text(
                    userStamp(it), color = HmTextMuted, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp, end = 4.dp),
                )
            }
        }
        return
    }
    // assistant
    Column(Modifier.fillMaxWidth()) {
        if (msg.reasoning.isNotBlank()) {
            Text(
                msg.reasoning,
                color = HmTextMuted, fontSize = 12.sp, fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        if (showTools && (turnTools.isNotEmpty() || msg.tools.isNotEmpty())) {
            TurnPill(if (turnTools.isNotEmpty()) turnTools else msg.tools,
                key = msg.key, modifier = Modifier.padding(bottom = 6.dp))
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
private fun TurnPill(tools: List<ToolCard>, key: String, modifier: Modifier = Modifier) {
    var expanded by remember(key) { mutableStateOf(false) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatComposer(vm: AppViewModel, enabled: Boolean, agentName: String) {
    var text by remember { mutableStateOf("") }
    // Official composer: pill with a "+" circle inside on the left, "Message <agent>…"
    // placeholder, and a filled circle button at the far right (mic in the official;
    // send here — voice recording is not implemented in this build).
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .background(HmBackground)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
                placeholder = { Text("Message $agentName\u2026", color = Color(0xFF6C6F7B), fontSize = 15.sp) },
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
            // Official has a mic inside the pill's right edge (voice capture not wired yet)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { /* voice capture: NOTES gap */ },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Mic, contentDescription = "Voice",
                    tint = HmTextMuted, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(HmAccentCircle)
                .alpha(if (enabled && text.isNotBlank()) 1f else 0.55f)
                .clickable {
                    val t = text.trim()
                    if (t.isNotEmpty() && enabled) {
                        vm.sendPrompt(t)
                        text = ""
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.Send, contentDescription = "Send",
                tint = HmTextPrimary, modifier = Modifier.size(20.dp),
            )
        }
    }
}

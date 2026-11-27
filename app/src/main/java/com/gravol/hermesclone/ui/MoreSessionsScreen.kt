package com.gravol.hermesclone.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.ViewKanban
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gravol.hermesclone.model.SessionRow
import com.gravol.hermesclone.net.GatewayClient
import com.gravol.hermesclone.state.AppViewModel
import com.gravol.hermesclone.state.UiState
import com.gravol.hermesclone.util.relTime
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Dedicated "more sessions" page (vc49 item 5), matched to official shots
 * official49-sessions-page.png / -filter.png / official49-select-mode.png:
 * header = back | avatar | profile name | filter | search; the filter sheet has
 * Show / Project rows (chevrons), an Include section with per-source counts, a
 * Group section ("Group by project") and a Sessions section ("Select sessions");
 * select mode swaps the header to X / "Select all" / archive and gives every row
 * a circle checkbox. Rows: status dot + title + relative time (live rows show a
 * spinner + blue elapsed instead), matching the home row style.
 */
@Composable
fun MoreSessionsScreen(vm: AppViewModel, st: UiState, onBack: () -> Unit) {
    val ms = st.moreSessions
    BackHandler(onBack = onBack)
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // infinite scroll: one page before the bottom
    LaunchedEffect(listState, ms.loadedPages, ms.total, ms.loading) {
        snapshotFlow { listState.layoutInfo }
            .collect { info ->
                val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                val already = ms.loadedPages * GatewayClient.PAGE_SIZE
                if (!ms.loading && ms.total != null && already < ms.total &&
                    last >= info.totalItemsCount - 4
                ) vm.loadMoreSessionsPage(ms.loadedPages, replace = false)
            }
    }

    val filtered = ms.sessions
        .filter { s -> query.isBlank() || s.title.lowercase().contains(query.lowercase()) }
        .filter { r ->
            when (ms.filterShow) {
                1 -> r.unread
                2 -> vm.isWorking(r)
                3 -> r.serverPinned || r.idCandidates.any { it in st.pinnedIds }
                else -> true
            }
        }
        .filter { r -> ms.filterProject == null || r.project == ms.filterProject }

    val groups: List<Pair<String, List<SessionRow>>> =
        if (ms.groupByProject) filtered.groupBy { it.project }.entries
            .sortedByDescending { e -> e.value.maxOfOrNull { it.updatedAt ?: 0L } ?: 0L }
            .map { e -> e.key to e.value }
        else listOf("" to filtered)

    Scaffoldish(ms, st.avatars, onSearch = { searchOpen = true }, vm = vm, onBack = onBack) { mod ->
        LazyColumn(state = listState, modifier = mod.fillMaxSize()) {
            groups.forEach { (proj, rows) ->
                if (ms.groupByProject) item(key = "mgrp-$proj") {
                    Text(
                        proj, color = HmTextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(rows, key = { "ms-${it.id}" }) { row ->
                    MoreSessionRow(
                        row = row,
                        working = vm.isWorking(row),
                        selectMode = ms.selectMode,
                        selected = row.id in ms.selected,
                        onClick = {
                            if (ms.selectMode) vm.toggleMoreSelection(row.id)
                            else vm.openChat(row, row.profile)
                        },
                    )
                }
            }
            if (ms.loading) item(key = "mload") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = HmLiveBlue, modifier = Modifier.size(22.dp))
                }
            }
        }
    }

    if (ms.filterSheet != null) MoreFilterSheet(ms, st, vm)
    if (searchOpen) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            SearchPillPublic(
                query = query,
                onQueryChange = { query = it },
                onClose = { searchOpen = false; query = "" },
                modifier = Modifier.padding(top = 120.dp, start = 16.dp, end = 16.dp),
            )
        }
    }
    st.toast?.let { msg ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp).padding(top = 96.dp)
                    .clip(RoundedCornerShape(28.dp)).background(Color(0xFF0F2A24))
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(androidx.compose.material.icons.Icons.Filled.Check, null,
                    tint = HmTurnCheck, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(msg, color = HmTextPrimary, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun Scaffoldish(
    ms: com.gravol.hermesclone.state.MoreSessionsState,
    avatars: Map<String, androidx.compose.ui.graphics.ImageBitmap>,
    onSearch: () -> Unit,
    vm: AppViewModel,
    onBack: () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(HmBackground)) {
        // header: normal = back | avatar | name | filter | search; select = X | Select all | archive
        Row(
            Modifier.fillMaxWidth().statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!ms.selectMode) {
                CircleIconButton2(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "back", tint = HmTextPrimary,
                        modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                AgentAvatar(name = if (ms.profile == "default") "Hermes" else ms.profile,
                    bitmap = avatars[ms.profile])
                Spacer(Modifier.width(12.dp))
                Text(
                    if (ms.profile == "default") "Hermes" else ms.profile,
                    color = HmTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                CircleIconButton2(onClick = { vm.moreSessionsFilterSheet("Filter") }) {
                    Icon(Icons.Filled.Tune, "filters", tint = HmTextPrimary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                CircleIconButton2(onClick = onSearch) {
                    Icon(Icons.Outlined.Search, "search", tint = HmTextPrimary, modifier = Modifier.size(20.dp))
                }
            } else {
                // select-mode header (official): X left, "Select all" pill + archive right
                CircleIconButton2(onClick = { vm.exitMoreSelectMode() }) {
                    Icon(Icons.Outlined.Close, "close", tint = HmTextPrimary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    if (ms.selected.isEmpty()) "Select" else "${ms.selected.size} selected",
                    color = HmTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = { vm.selectAllMoreSessions() },
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(HmSurface),
                ) { Text("Select all", color = HmTextPrimary, fontSize = 14.sp) }
                Spacer(Modifier.width(10.dp))
                CircleIconButton2(onClick = { vm.bulkArchiveMore() }) {
                    Icon(Icons.Outlined.Archive, "archive selected", tint = HmTextPrimary,
                        modifier = Modifier.size(20.dp))
                }
            }
        }
        content(Modifier.padding(bottom = 8.dp))
    }
}

@Composable
private fun CircleIconButton2(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(42.dp).clip(CircleShape).background(HmSurface).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Row with the official select-mode checkbox: hollow circle → blue filled circle + check. */
@Composable
private fun MoreSessionRow(
    row: SessionRow,
    working: Boolean,
    selectMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectMode) {
            Box(
                Modifier.size(24.dp).clip(CircleShape)
                    .background(if (selected) HmLiveBlue else Color.Transparent)
                    .then(
                        if (!selected) Modifier.border(2.dp, Color(0xFF8A8AA6), CircleShape)
                        else Modifier
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(Icons.Filled.Check, null, tint = Color.White,
                    modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.width(14.dp))
        } else if (working) {
            CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 2.dp, color = HmLiveBlue)
            Spacer(Modifier.width(12.dp))
        } else {
            Box(Modifier.size(8.dp).clip(CircleShape).background(HmLiveBlue.copy(alpha = 0.5f)))
            Spacer(Modifier.width(14.dp))
        }
        Text(
            row.title, color = HmTextPrimary, fontSize = 15.sp,
            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(8.dp))
        if (working) {
            // official shows blue elapsed time for live rows; we only know "working" —
            // show the blue Working label (NOTES: per-turn start not on the wire here)
            Text("Working", color = HmLiveBlue, fontSize = 13.sp)
        } else {
            Text(relTime(row.updatedAt), color = HmTextMuted, fontSize = 13.sp)
        }
    }
}

/** The page's own filter sheet (official vc49, shot official49-sessions-filter.png):
 *  Filter / Show (All ›) / Project (All projects ›) / Include: Routine runs (N sessions),
 *  Kanban workers (N sessions) toggles / Group: Group by project / Sessions: Select sessions. */
@Composable
private fun MoreFilterSheet(
    ms: com.gravol.hermesclone.state.MoreSessionsState,
    st: UiState,
    vm: AppViewModel,
) {
    val title = ms.filterSheet ?: return
    val projects = ms.sessions.groupBy { it.project }.entries.sortedByDescending { e -> e.value.size }
    if (title == "Filter") {
        androidx.compose.ui.window.Dialog(onDismissRequest = { vm.moreSessionsFilterSheet(null) }) {
            androidx.compose.material3.Surface(
                color = Color(0xFF0C1A3C), shape = RoundedCornerShape(18.dp), tonalElevation = 0.dp,
            ) {
                Column(Modifier.width(300.dp).padding(vertical = 12.dp)) {
                    Text("Filter", color = HmTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 20.dp, bottom = 8.dp))
                    SheetNavRow(Icons.Outlined.History, "Show",
                        listOf("All", "Unread", "Working", "Pinned")[ms.filterShow])
                        { vm.moreSessionsFilterSheet("Show") }
                    SheetNavRow(Icons.Outlined.FolderOpen, "Project", ms.filterProject ?: "All projects")
                        { vm.moreSessionsFilterSheet("Project") }
                    Spacer(Modifier.height(8.dp))
                    Text("Include", color = HmTextMuted, fontSize = 12.sp,
                        modifier = Modifier.padding(start = 20.dp, bottom = 2.dp))
                    SheetSwitch2(Icons.Outlined.History, "Routine runs", st.includeRoutine,
                        sub = st.sourceCounts["cron"]?.let { "$it sessions" }) { vm.toggleIncludeRoutine() }
                    SheetSwitch2(Icons.Outlined.ViewKanban, "Kanban workers", st.includeKanban,
                        sub = st.sourceCounts["kanban"]?.let { "$it sessions" }) { vm.toggleIncludeKanban() }
                    Spacer(Modifier.height(8.dp))
                    Text("Group", color = HmTextMuted, fontSize = 12.sp,
                        modifier = Modifier.padding(start = 20.dp, bottom = 2.dp))
                    SheetSwitch2(Icons.Outlined.Layers, "Group by project", ms.groupByProject)
                        { vm.toggleMoreGroupByProject() }
                    Spacer(Modifier.height(8.dp))
                    Text("Sessions", color = HmTextMuted, fontSize = 12.sp,
                        modifier = Modifier.padding(start = 20.dp, bottom = 2.dp))
                    SheetNavRow(Icons.Outlined.SelectAll, "Select sessions", null,
                        onClick = { vm.enterMoreSelectMode() })
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    } else {
        // Show / Project submenus reuse the home sheet dialog styling
        androidx.compose.ui.window.Dialog(onDismissRequest = { vm.moreSessionsFilterSheet("Filter") }) {
            androidx.compose.material3.Surface(
                color = Color(0xFF0C1A3C), shape = RoundedCornerShape(18.dp), tonalElevation = 0.dp,
            ) {
                Column(Modifier.width(300.dp).padding(vertical = 10.dp)) {
                    Text(title, color = HmTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 8.dp))
                    when (title) {
                        "Show" -> listOf("All", "Unread", "Working", "Pinned")
                            .forEachIndexed { i, l ->
                                SheetOption2(l, ms.filterShow == i) { vm.setMoreFilterShow(i) }
                            }
                        "Project" -> {
                            SheetOption2("All projects", ms.filterProject == null) { vm.setMoreFilterProject(null) }
                            projects.forEach { (proj, rows) ->
                                SheetOption2(proj, ms.filterProject == proj, sub = "${rows.size} sessions")
                                    { vm.setMoreFilterProject(proj) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetNavRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String?,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = HmTextPrimary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = HmTextPrimary, fontSize = 15.sp)
            if (value != null) Text(value, color = HmTextMuted, fontSize = 12.sp)
        }
        Icon(Icons.Outlined.ArrowForwardIos, null,
            tint = HmTextMuted, modifier = Modifier.size(12.dp))
    }
}

@Composable
private fun SheetSwitch2(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    checked: Boolean,
    sub: String? = null,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onChecked(!checked) }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = HmTextPrimary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = HmTextPrimary, fontSize = 15.sp)
            if (sub != null) Text(sub, color = HmTextMuted, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedTrackColor = HmLiveBlue, checkedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFF2A3556), uncheckedThumbColor = Color(0xFF8A8AA6),
                uncheckedBorderColor = Color.Transparent,
            ))
    }
}

@Composable
private fun SheetOption2(label: String, selected: Boolean, sub: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = HmTextPrimary, fontSize = 15.sp)
            if (sub != null) Text(sub, color = HmTextMuted, fontSize = 12.sp)
        }
        if (selected) Icon(Icons.Filled.Check, null, tint = HmLiveBlue, modifier = Modifier.size(18.dp))
    }
}

/** Search pill for the more-sessions page (same look as the home search pill). */
@Composable
private fun SearchPillPublic(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(26.dp))
                .background(Color(0xFF16213F)).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Search, null, tint = HmTextMuted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = HmTextPrimary, fontSize = 15.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(HmPurple),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) Text("Search sessions", color = HmTextMuted, fontSize = 15.sp)
                        inner()
                    }
                },
            )
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(Color(0xFF16213F)).clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Close, "close search", tint = HmTextPrimary, modifier = Modifier.size(22.dp))
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

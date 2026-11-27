package com.gravol.hermesclone.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material3.Surface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.ScreenRotationAlt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ViewKanban
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Switch
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gravol.hermesclone.R
import com.gravol.hermesclone.model.SessionRow
import com.gravol.hermesclone.net.GatewayClient
import com.gravol.hermesclone.state.AppViewModel
import com.gravol.hermesclone.state.ConnPhase
import com.gravol.hermesclone.state.ProfileGroup
import com.gravol.hermesclone.state.UiState
import com.gravol.hermesclone.util.relTime


/**
 * Session home, matched to the official 01-launch screenshot:
 * no title/chips — circular gear (left) + filter/search circles (right);
 * agents_backdrop artwork faded at the bottom; agent-grouped tree with
 * connector ticks; live dots; animated group expand/collapse; pencil FAB.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: AppViewModel, onOpenGateways: () -> Unit, onSettings: () -> Unit) {
    val st by vm.state.collectAsStateWithLifecycle()
    var searchOpen by remember { mutableStateOf(false) }
    var composeSheet by remember { mutableStateOf(false) }
    val pts = rememberPullToRefreshState()

    val query = st.query.trim().lowercase()
    val ordered = if (st.profileOrder.isNotEmpty()) st.profileOrder
        else st.profiles.map { it.name }
    val visibleProfiles = ordered
        .filter { p -> st.selectedProfile == null || p == st.selectedProfile }
        .filter { p ->
            if (query.isEmpty()) true
            else {
                val g = st.groups[p]
                p.lowercase().contains(query) ||
                    (g?.sessions?.any { it.title.lowercase().contains(query) } == true)
            }
        }

    Scaffold(
        containerColor = HmBackground,
        topBar = {
            Column(Modifier.statusBarsPadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircleIconButton(onClick = onSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "settings",
                            tint = HmTextPrimary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Box {
                        var filterOpen by remember { mutableStateOf(false) }
                        CircleIconButton(onClick = { filterOpen = true }) {
                            Icon(Icons.Outlined.FilterList, contentDescription = "filters",
                                tint = if (filterOpen) HmPurple else HmTextPrimary,
                                modifier = Modifier.size(20.dp))
                        }
                        FilterMenu(st, vm, expanded = filterOpen, onDismiss = { filterOpen = false },
                            onOpenSheet = { filterOpen = false })
                    }
                    Spacer(Modifier.width(12.dp))
                    CircleIconButton(onClick = { searchOpen = !searchOpen }) {
                        Icon(Icons.Outlined.Search,
                            contentDescription = "search",
                            tint = if (searchOpen) HmPurple else HmTextPrimary,
                            modifier = Modifier.size(20.dp))
                    }
                }
                if (st.phase == ConnPhase.RECONNECTING) {
                    Text(
                        st.statusText.ifBlank { "Reconnecting…" },
                        color = HmLiveBlue, fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { composeSheet = true },
                containerColor = HmSurface,
                contentColor = HmTextPrimary,
                shape = CircleShape,
            ) {
                Icon(Icons.Outlined.Create, contentDescription = stringResource(R.string.new_chat))
            }
        },
    ) { padding ->
        androidx.compose.foundation.layout.BoxWithConstraints(
            modifier = Modifier.fillMaxSize().background(HmBackground)
        ) {
            // Official home artwork (extracted from the APK's own drawable) faded at the bottom.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(560.dp)
                    .align(Alignment.BottomCenter)
                    .clipToBounds(),
            ) {
                Image(
                    painter = painterResource(R.drawable.agents_backdrop),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .alpha(0.12f),
                    contentScale = ContentScale.FillWidth,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(HmBackground, HmBackground.copy(alpha = 0f)),
                            )
                        ),
                )
            }
            PullToRefreshBox(
                state = pts,
                onRefresh = { vm.refreshAll() },
                isRefreshing = st.refreshing,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                if (visibleProfiles.isEmpty() && !st.refreshing) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.no_sessions), color = HmTextMuted)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 8.dp, end = 8.dp, top = 8.dp, bottom = 96.dp,
                        ),
                    ) {
                        val q = st.query.trim().lowercase()
                        val pinnedOf = { row: com.gravol.hermesclone.model.SessionRow ->
                            row.serverPinned || row.idCandidates.any { it in st.pinnedIds }
                        }
                        val showOk = { row: com.gravol.hermesclone.model.SessionRow ->
                            when (st.filterShow) {
                                1 -> row.unread
                                2 -> vm.isWorking(row)
                                3 -> pinnedOf(row)
                                else -> true
                            }
                        }
                        val allPairs = st.groups.toList().flatMap { (p, g) -> g.sessions.map { p to it } }
                        val filtered = allPairs
                            .filter { (p, r) ->
                                (st.filterAgent == null || p == st.filterAgent) &&
                                    (st.filterProject == null || r.project == st.filterProject)
                            }
                            .filter { (_, r) -> q.isEmpty() || r.title.lowercase().contains(q) }
                            .filter { (_, r) -> showOk(r) }
                            .filter { (_, r) ->
                                (st.includeRoutine || r.source != "cron") &&
                                    (st.includeKanban || r.source != "kanban")
                            }
                        val sortRows = { rows: List<com.gravol.hermesclone.model.SessionRow> ->
                            val base = if (st.filterSort == 1) rows.sortedBy { it.title.lowercase() }
                                       else rows.sortedByDescending { it.updatedAt ?: 0L }
                            base.sortedByDescending { pinnedOf(it) } // official: pinned float to top
                        }
                        @Composable
                        fun rowFor(row: com.gravol.hermesclone.model.SessionRow) {
                            SessionChildRow(
                                row = row,
                                working = vm.isWorking(row),
                                sinceMs = vm.workingSinceFor(row),
                                pinned = pinnedOf(row),
                                onClick = { vm.openChat(row, row.profile) },
                                onPin = { vm.togglePin(row) },
                                onArchive = { vm.setArchived(row, true) },
                                onRename = { vm.setRenameTarget(row) },
                            )
                        }
                        if (st.filterView == 2) { // Sessions only: flat list, no group headers
                            items(sortRows(filtered.map { it.second }), key = { "flat-${it.profile}-${it.id}" }) { row ->
                                rowFor(row)
                            }
                        } else {
                            val groupsToShow: List<Pair<String, List<com.gravol.hermesclone.model.SessionRow>>> =
                                if (st.filterView == 1) filtered.groupBy { it.second.project }
                                    .map { (proj, ps) -> proj to ps.map { it.second } }
                                else visibleProfiles
                                    .filter { st.filterAgent == null || it == st.filterAgent }
                                    .map { p -> p to filtered.filter { it.first == p }.map { it.second } }
                            groupsToShow.forEach { (key, rows) ->
                                val isAgentView = st.filterView == 0
                                val group = if (isAgentView) st.groups[key] else null
                                val expanded = if (isAgentView) (group?.expanded ?: false) else true
                                item(key = "group-$key") {
                                    GroupHeader(
                                        name = if (key == "default") "Hermes" else key,
                                        avatar = st.avatars[key],
                                        updatedAt = rows.maxOfOrNull { it.updatedAt ?: 0L }?.takeIf { it > 0 },
                                        expanded = expanded,
                                        onClick = { if (isAgentView) vm.toggleExpand(key) },
                                    )
                                }
                                if (st.filterView != 3) { // Agents only: headers only, no rows
                                    items(sortRows(rows), key = { "sess-$key-${it.id}" }) { row ->
                                        AnimatedVisibility(
                                            visible = expanded,
                                            enter = expandVertically(tween(380, easing = FastOutSlowInEasing)) +
                                                fadeIn(tween(380)),
                                            exit = shrinkVertically(tween(380)) + fadeOut(tween(300)),
                                        ) { rowFor(row) }
                                    }
                                    val remaining = (group?.total ?: 0) - rows.size
                                    if (isAgentView && expanded && group != null && (remaining > 0 ||
                                                (group.total == null && rows.isNotEmpty() &&
                                                    rows.size % GatewayClient.PAGE_SIZE == 0))
                                    ) {
                                        item(key = "more-$key") {
                                            MoreSessionsRow(
                                                count = remaining,
                                                loading = group.loading,
                                                // vc49: opens the dedicated "more sessions" page
                                                // (own filter sheet + select mode), not another home page
                                                onClick = { vm.openMoreSessions(key) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        // vc49 bugfix: spacer so the pinned "Archived" overlay never overlaps the last row
                        if (st.archived.isNotEmpty()) item(key = "archived-spacer") { Spacer(Modifier.height(72.dp)) }
                    }
                }
            }
            if (composeSheet) {
                ComposeSheet(
                    profiles = st.profiles.map { it.name },
                    avatars = st.avatars,
                    onPick = { p -> composeSheet = false; vm.newSession(p) },
                    onDismiss = { composeSheet = false },
                )
            }
            // official: "Archived" entry pinned at screen bottom while any session is archived
            if (st.archived.isNotEmpty() && !searchOpen) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 20.dp, bottom = 28.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { vm.openArchived() }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Archive, contentDescription = null, tint = HmTextPrimary,
                        modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Archived", color = HmTextPrimary, fontSize = 16.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(st.archived.size.toString(), color = HmTextMuted, fontSize = 16.sp)
                }
            }
            // official snackbar: dark pill + green check, top center ("Conversation pinned")
            st.toast?.let { msg ->
                AnimatedVisibility(
                    visible = true,
                    enter = slideInVertically(tween(280)) { -it } + fadeIn(tween(280)),
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 96.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(Color(0xFF0F2A24))
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF29A551),
                            modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(msg, color = HmTextPrimary, fontSize = 15.sp)
                    }
                }
            }
            // official rename dialog: "Rename conversation" + prefilled field + "Title" + Cancel/Rename
            st.filterSheet?.let { FilterSheet(st, vm) }
            st.renameTarget?.let { (row, profile) ->
                RenameDialog(row.title,
                    onCancel = { vm.clearRenameTarget() },
                    onRename = { t -> vm.renameSession(row, profile, t) },
                )
            }
            if (searchOpen) {
                SearchPill(
                    query = st.query,
                    onQueryChange = vm::setQuery,
                    onClose = { searchOpen = false; vm.setQuery("") },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = maxHeight * 0.42f, start = 16.dp, end = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun CircleIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(HmSurface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun GroupHeader(
    name: String,
    avatar: androidx.compose.ui.graphics.ImageBitmap?,
    updatedAt: Long?,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AgentAvatar(name = name, bitmap = avatar)
        Spacer(Modifier.width(14.dp))
        Text(
            name,
            color = HmTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(relTime(updatedAt), color = HmTextMuted, fontSize = 13.sp)
    }
}

/** Neutral avatar placeholder: gradient rounded square with two "face" dots.
 *  (Gateway-supplied avatar bitmaps are used when present.) */
@Composable
fun AgentAvatar(name: String, bitmap: androidx.compose.ui.graphics.ImageBitmap?, avatarSize: androidx.compose.ui.unit.Dp = 44.dp) {
    Box(
        modifier = Modifier
            .size(avatarSize)
            .clip(RoundedCornerShape(13.dp)),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val base = avatarPalette[Math.floorMod(name.hashCode(), avatarPalette.size)]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(base, base.copy(alpha = 0.55f)),
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.size(avatarSize)) {
                    val r = size.minDimension * 0.09f
                    val dy = -size.minDimension * 0.12f
                    val gap = size.minDimension * 0.22f
                    fun dot(cx: Float) = drawCircle(
                        color = Color.White.copy(alpha = 0.92f),
                        radius = r,
                        center = androidx.compose.ui.geometry.Offset(cx, center.y + dy),
                    )
                    dot(center.x - gap)
                    dot(center.x + gap)
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SessionChildRow(
    row: SessionRow,
    working: Boolean,
    sinceMs: Long? = null,
    pinned: Boolean,
    onClick: () -> Unit,
    onPin: () -> Unit,
    onArchive: () -> Unit,
    onRename: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp)
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
            .padding(horizontal = 4.dp, vertical = 7.75.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // tree connector: vertical line + horizontal tick to the status dot (official layout).
        // vc49 pitch fix (2026-11-26): official row pitch ~103px (39dp) at density 2.625;
        // the 44dp connector box was pushing ours to ~134px.
        Box(
            modifier = Modifier
                .width(30.dp)
                .height(24.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(24.dp)
                    .align(Alignment.Center)
                    .background(HmTreeLine),
            )
            Box(
                modifier = Modifier
                    .width(14.dp)
                    .height(2.dp)
                    .align(Alignment.CenterEnd)
                    .background(HmTreeLine),
            )
        }
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (working) HmLiveBlue else HmLiveBlue.copy(alpha = 0.45f)),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            row.title,
            color = HmTextPrimary, fontSize = 15.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(8.dp))
        if (pinned) {
            Icon(
                Icons.Filled.PushPin, contentDescription = null,
                tint = Color(0xFFE8C444), modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        if (working) {
            // vc49: official live row = spinner + elapsed m:ss (was "Working" text)
            var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(working) { while (working) { nowMs = System.currentTimeMillis(); delay(1000) } }
            val secs = ((nowMs - (sinceMs ?: nowMs)) / 1000L).coerceAtLeast(0L)
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = HmLiveBlue,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "${secs / 60}:${"%02d".format(secs % 60)}",
                    color = HmLiveBlue, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                )
            }
        } else {
            Text(relTime(row.updatedAt), color = HmTextMuted, fontSize = 13.sp)
        }
    }
    // official long-press menu (verified vs official 2026-11-24): Pin<->Unpin / Archive / Rename
    SessionContextMenu(
        opened = menuOpen,
        pinned = pinned,
        onDismiss = { menuOpen = false },
        onPin = { menuOpen = false; onPin() },
        onArchive = { menuOpen = false; onArchive() },
        onRename = { menuOpen = false; onRename() },
    )
    }
}

@Composable
private fun SessionContextMenu(
    opened: Boolean,
    pinned: Boolean,
    onDismiss: () -> Unit,
    onPin: () -> Unit,
    onArchive: () -> Unit,
    onRename: () -> Unit,
) {
    DropdownMenu(
        expanded = opened,
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141F3C),
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 0.dp,
    ) {
        ContextItem(if (pinned) Icons.Outlined.PushPin else Icons.Outlined.PushPin,
            if (pinned) "Unpin" else "Pin", onPin)
        ContextItem(Icons.Outlined.Archive, "Archive", onArchive)
        ContextItem(Icons.Outlined.Edit, "Rename", onRename)
    }
}

@Composable
private fun ContextItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, color = HmTextPrimary, fontSize = 15.sp) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = HmTextPrimary, modifier = Modifier.size(20.dp)) },
        onClick = onClick,
    )
}

@Composable
private fun MoreSessionsRow(count: Int, loading: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 46.dp, end = 12.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = !loading) { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp, color = HmPurple)
            Spacer(Modifier.width(10.dp))
        }
        Text(
            if (count > 0) pluralStringResource(
                R.plurals.more_sessions, count, count,
            ) else stringResource(R.string.more_sessions_loading),
            color = HmTextMuted, fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Outlined.ArrowForwardIos, contentDescription = null,
            tint = HmTextMuted, modifier = Modifier.size(12.dp),
        )
    }
}

@Composable
private fun SearchPill(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xFF16213F))
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Search, contentDescription = null,
                tint = HmTextMuted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = HmTextPrimary, fontSize = 15.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(HmPurple),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) {
                            Text("Search agents and sessions", color = HmTextMuted, fontSize = 15.sp)
                        }
                        inner()
                    }
                },
            )
        }
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color(0xFF16213F))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Close, contentDescription = "close search",
                tint = HmTextPrimary, modifier = Modifier.size(22.dp))
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

@Composable
private fun FilterMenu(st: UiState, vm: AppViewModel, expanded: Boolean, onDismiss: () -> Unit,
                       onOpenSheet: () -> Unit = {}) {
    // official closes the dropdown when a submenu sheet opens (verified 2026-11-24)
    val openSheet = { title: String -> onOpenSheet(); onDismiss(); vm.openFilterSheet(title) }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141F3C),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 0.dp,
        modifier = Modifier.width(268.dp),
    ) {
        FilterItem(Icons.Outlined.ViewAgenda, "View",
            listOf("By agent", "By project", "Sessions only", "Agents only")
                [st.filterView.coerceIn(0, 3)]) { openSheet("View") }
        FilterItem(Icons.Outlined.Sort, "Sort",
            listOf("Recent activity", "Name")[st.filterSort.coerceIn(0, 1)]) { openSheet("Sort") }
        FilterItem(Icons.Outlined.History, "Recent sessions",
            listOf("Last day", "Last 3 days", "Last week")
                [listOf(1, 3, 7).indexOf(st.filterRecentDays).coerceIn(0, 2)]) { openSheet("Recent sessions") }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.padding(horizontal = 16.dp).height(1.dp).background(HmBorder))
        Spacer(Modifier.height(6.dp))
        FilterItem(Icons.Outlined.ScreenRotationAlt, "Show",
            listOf("All", "Unread", "Working", "Pinned")[st.filterShow.coerceIn(0, 3)]) { openSheet("Show") }
        FilterItem(Icons.Outlined.PeopleOutline, "Agents",
            st.filterAgent?.let { if (it == "default") "Hermes" else it } ?: "All") { openSheet("Agents") }
        FilterItem(Icons.Outlined.FolderOpen, "Project", st.filterProject ?: "All projects")
            { openSheet("Project") }
        FilterItem(Icons.Outlined.Layers, "Include", "Group chats") { openSheet("Include") }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ComposeSheet(
    profiles: List<String>,
    avatars: Map<String, androidx.compose.ui.graphics.ImageBitmap?>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // vc49 (shot official49-newsession.png): adds a "Search agents" field and a
    // "New group" row. Group creation has no gateway endpoint we have observed —
    // the row is present for parity and says so (NOTES gap).
    var query by remember { mutableStateOf("") }
    var note by remember { mutableStateOf<String?>(null) }
    val q = query.trim().lowercase()
    val shown = profiles.filter { q.isEmpty() || it.lowercase().contains(q) }
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = HmBackground,
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Start a new session", color = HmTextPrimary, fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(HmSurface),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.PeopleOutline, contentDescription = "New group",
                        tint = HmTextPrimary, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            // "Search agents" field
            Row(
                Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp))
                    .background(HmSurface).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Search, null, tint = HmTextMuted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = HmTextPrimary, fontSize = 14.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(HmPurple),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) Text("Search agents", color = HmTextMuted, fontSize = 14.sp)
                            inner()
                        }
                    },
                )
            }
            Spacer(Modifier.height(6.dp))
            // "New group" row (groups not wired to this gateway — honest note on tap)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .clickable { note = "Groups aren't supported by this gateway yet" }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(HmAccentCircle),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.PeopleOutline, null, tint = HmTextPrimary,
                        modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(16.dp))
                Text("New group", color = HmTextPrimary, fontSize = 16.sp)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(HmBorder.copy(alpha = 0.5f)))
            shown.forEach { p ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onPick(p) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AgentAvatar(name = p, bitmap = avatars[p])
                    Spacer(Modifier.width(16.dp))
                    Text(if (p == "default") "Hermes" else p, color = HmTextPrimary, fontSize = 16.sp)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(HmBorder.copy(alpha = 0.5f)))
            }
            note?.let {
                Text(it, color = HmTextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FilterItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Column {
                Text(title, color = HmTextPrimary, fontSize = 16.sp)
                Text(subtitle, color = HmTextMuted, fontSize = 13.sp)
            }
        },
        leadingIcon = { Icon(icon, contentDescription = null, tint = HmTextPrimary, modifier = Modifier.size(20.dp)) },
        trailingIcon = { Icon(Icons.Outlined.ArrowForwardIos, contentDescription = null, tint = HmTextMuted, modifier = Modifier.size(12.dp)) },
        onClick = onClick,
    )
}


// ---------------------------------------------------------------------------
// phase 7: archived screen + rename dialog (verified vs official 2026-11-24)

@Composable
fun ArchivedScreen(st: UiState, vm: AppViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleIconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back",
                        tint = HmTextPrimary, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(16.dp))
                Text("Archived", color = HmTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            }
        },
        containerColor = Color.Transparent,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp),
        ) {
            items(st.archived, key = { "arch-${it.profile}-${it.id}" }) { row ->
                ArchivedRow(
                    row = row,
                    pinned = row.serverPinned || row.idCandidates.any { it in st.pinnedIds },
                    avatar = st.avatars[row.profile],
                    onClick = { vm.openChat(row, row.profile) },
                    onUnpin = { vm.togglePin(row) },
                    onUnarchive = { vm.setArchived(row, false) },
                    onRename = { vm.setRenameTarget(row) },
                )
            }
        }
    }
}

@Composable
private fun ArchivedRow(
    row: com.gravol.hermesclone.model.SessionRow,
    pinned: Boolean,
    avatar: androidx.compose.ui.graphics.ImageBitmap?,
    onClick: () -> Unit,
    onUnpin: () -> Unit,
    onUnarchive: () -> Unit,
    onRename: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AgentAvatar(name = row.profile, bitmap = avatar)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(row.title, color = HmTextPrimary, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(if (row.profile == "default") "Hermes" else row.profile, color = HmTextMuted, fontSize = 13.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (pinned) {
                        Icon(Icons.Filled.PushPin, contentDescription = null, tint = Color(0xFFE8C444),
                            modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                    }
                    Text(relTime(row.updatedAt), color = HmTextMuted, fontSize = 13.sp)
                }
                Spacer(Modifier.height(2.dp))
                row.messageCount?.let {
                    Text("$it messages", color = HmTextMuted, fontSize = 13.sp)
                }
            }
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            containerColor = Color(0xFF141F3C),
            shape = RoundedCornerShape(14.dp),
            tonalElevation = 0.dp,
        ) {
            ContextItem(Icons.Outlined.PushPin, if (pinned) "Unpin" else "Pin") { menuOpen = false; onUnpin() }
            ContextItem(Icons.Outlined.Archive, "Unarchive") { menuOpen = false; onUnarchive() }
            ContextItem(Icons.Outlined.Edit, "Rename") { menuOpen = false; onRename() }
        }
    }
}

@Composable
fun RenameDialog(current: String, onCancel: () -> Unit, onRename: (String) -> Unit) {
    var text by remember { mutableStateOf(TextFieldValue(text = current, selection = TextRange(current.length))) }
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = Color(0xFF101A33),
        title = { Text("Rename conversation", color = HmTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = HmTextPrimary, unfocusedTextColor = HmTextPrimary,
                        focusedBorderColor = HmLiveBlue, unfocusedBorderColor = HmBorder,
                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text("Title", color = HmTextMuted, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = { onRename(text.text) }) {
                Text("Rename", color = HmLiveBlue, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel", color = HmLiveBlue) }
        },
    )
}


// ---------------------------------------------------------------------------
// phase 8: filter submenu sheet (options transcribed from official 2026-11-24)

@Composable
private fun FilterSheet(st: UiState, vm: AppViewModel) {
    val title = st.filterSheet ?: return
    val projects = st.groups.values.flatMap { it.sessions }
        .groupBy { it.project }.entries.sortedByDescending { e -> e.value.size }
    androidx.compose.ui.window.Dialog(onDismissRequest = { vm.closeFilterSheet() }) {
        Surface(color = Color(0xFF141F3C), shape = RoundedCornerShape(18.dp), tonalElevation = 0.dp) {
            Column(Modifier.width(300.dp).padding(vertical = 10.dp)) {
                Text(title, color = HmTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 8.dp))
                when (title) {
                    "View" -> listOf("By agent", "By project", "Sessions only", "Agents only")
                        .forEachIndexed { i, l -> SheetOption(l, st.filterView == i) { vm.setFilterView(i) } }
                    "Sort" -> listOf("Recent activity", "Name")
                        .forEachIndexed { i, l -> SheetOption(l, st.filterSort == i) { vm.setFilterSort(i) } }
                    "Recent sessions" -> listOf("Last day" to 1, "Last 3 days" to 3, "Last week" to 7)
                        .forEach { (l, v) -> SheetOption(l, st.filterRecentDays == v) { vm.setFilterRecent(v) } }
                    "Show" -> listOf("All", "Unread", "Working", "Pinned")
                        .forEachIndexed { i, l -> SheetOption(l, st.filterShow == i) { vm.setFilterShow(i) } }
                    "Agents" -> {
                        SheetOption("All agents", st.filterAgent == null) { vm.setFilterAgent(null) }
                        st.profiles.map { it.name }.forEach { p ->
                            SheetOption(if (p == "default") "Hermes" else p, st.filterAgent == p) { vm.setFilterAgent(p) }
                        }
                    }
                    "Project" -> {
                        SheetOption("All projects", st.filterProject == null) { vm.setFilterProject(null) }
                        projects.forEach { (proj, rows) ->
                            SheetOption(proj, st.filterProject == proj, sub = "${rows.size} sessions")
                                { vm.setFilterProject(proj) }
                        }
                    }
                    // Include: official switches (verified on official app 2026-11-24): Group chats ON,
                    // Hidden agents OFF, Routine runs OFF + count, Kanban workers OFF + count.
                    // Routine/Kanban filter by roster source; the other two persist display state only.
                    "Include" -> {
                        SheetSwitch(Icons.Outlined.PeopleOutline, "Group chats", st.includeGroupChats)
                            { vm.toggleIncludeGroupChats() }
                        SheetSwitch(Icons.Outlined.VisibilityOff, "Hidden agents", st.includeHiddenAgents)
                            { vm.toggleIncludeHiddenAgents() }
                        SheetSwitch(Icons.Outlined.History, "Routine runs", st.includeRoutine,
                            sub = st.sourceCounts["cron"]?.let { "$it sessions" }) { vm.toggleIncludeRoutine() }
                        SheetSwitch(Icons.Outlined.ViewKanban, "Kanban workers", st.includeKanban,
                            sub = st.sourceCounts["kanban"]?.let { "$it sessions" }) { vm.toggleIncludeKanban() }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun SheetSwitch(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String,
                        checked: Boolean, sub: String? = null, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .clickable { onChecked(!checked) }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = HmTextPrimary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = HmTextPrimary, fontSize = 15.sp)
            if (sub != null) Text(sub, color = HmTextMuted, fontSize = 12.sp)
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
private fun SheetOption(label: String, selected: Boolean, sub: String? = null,
                        enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = if (enabled) HmTextPrimary else HmTextMuted, fontSize = 15.sp)
            if (sub != null) Text(sub, color = HmTextMuted, fontSize = 12.sp)
        }
        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = HmLiveBlue,
            modifier = Modifier.size(18.dp))
    }
}

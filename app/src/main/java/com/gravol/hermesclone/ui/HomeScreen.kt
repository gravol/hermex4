package com.gravol.hermesclone.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.ScreenRotationAlt
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Close
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
    val visibleProfiles = st.profiles
        .map { it.name }
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
                        FilterMenu(expanded = filterOpen, onDismiss = { filterOpen = false })
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
                        .alpha(0.32f),
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
                        visibleProfiles.forEach { profile ->
                            val group = st.groups[profile] ?: ProfileGroup(profile)
                            item(key = "group-$profile") {
                                GroupHeader(
                                    name = profile,
                                    avatar = st.avatars[profile],
                                    updatedAt = group.sessions.maxOfOrNull { it.updatedAt ?: 0L }
                                        ?.takeIf { it > 0 },
                                    expanded = group.expanded,
                                    onClick = { vm.toggleExpand(profile) },
                                )
                            }
                            val shown = group.sessions
                            items(shown, key = { "sess-${profile}-${it.id}" }) { row ->
                                AnimatedVisibility(
                                    visible = group.expanded,
                                    enter = expandVertically(tween(300, easing = FastOutSlowInEasing)) +
                                        fadeIn(tween(300)),
                                    exit = shrinkVertically(tween(240)) + fadeOut(tween(180)),
                                ) {
                                    SessionChildRow(
                                        row = row,
                                        working = vm.isWorking(row),
                                        onClick = { vm.openChat(row, profile) },
                                    )
                                }
                            }
                            val remaining = (group.total ?: 0) - shown.size
                            if (group.expanded && (remaining > 0 || (group.total == null && shown.isNotEmpty() &&
                                        shown.size % GatewayClient.PAGE_SIZE == 0))
                            ) {
                                item(key = "more-$profile") {
                                    MoreSessionsRow(
                                        count = remaining,
                                        loading = group.loading,
                                        onClick = {
                                            vm.loadPage(
                                                profile,
                                                offset = group.loadedPages * GatewayClient.PAGE_SIZE,
                                                replace = false,
                                            )
                                        },
                                    )
                                }
                            }
                        }
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
fun AgentAvatar(name: String, bitmap: androidx.compose.ui.graphics.ImageBitmap?) {
    Box(
        modifier = Modifier
            .size(44.dp)
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
                Canvas(modifier = Modifier.size(44.dp)) {
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
private fun SessionChildRow(row: SessionRow, working: Boolean, onClick: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp)
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // tree connector: vertical line + horizontal tick to the status dot (official layout)
        Box(
            modifier = Modifier
                .width(30.dp)
                .height(44.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(44.dp)
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
        if (working) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = HmLiveBlue,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.working),
                    color = HmLiveBlue, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                )
            }
        } else {
            Text(relTime(row.updatedAt), color = HmTextMuted, fontSize = 13.sp)
        }
    }
    // official long-press menu: Pin / Archive / Rename (actions wired in a later pass)
    SessionContextMenu(opened = menuOpen, onDismiss = { menuOpen = false })
    }
}

@Composable
private fun SessionContextMenu(opened: Boolean, onDismiss: () -> Unit) {
    DropdownMenu(
        expanded = opened,
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141F3C),
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 0.dp,
    ) {
        ContextItem(Icons.Outlined.PushPin, "Pin", "True")
        ContextItem(Icons.Outlined.Archive, "Archive", null)
        ContextItem(Icons.Outlined.Edit, "Rename", null)
    }
}

@Composable
private fun ContextItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String?) {
    DropdownMenuItem(
        text = { Text(label, color = HmTextPrimary, fontSize = 15.sp) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = HmTextPrimary, modifier = Modifier.size(20.dp)) },
        trailingIcon = if (value != null) { { Text(value, color = HmTextMuted, fontSize = 14.sp) } } else null,
        onClick = { /* TODO: wire to gateway session actions */ },
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
private fun FilterMenu(expanded: Boolean, onDismiss: () -> Unit) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141F3C),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 0.dp,
        modifier = Modifier.width(268.dp),
    ) {
        FilterItem(Icons.Outlined.ViewAgenda, "View", "By agent")
        FilterItem(Icons.Outlined.Sort, "Sort", "Recent activity")
        FilterItem(Icons.Outlined.History, "Recent sessions", "Last day")
        Spacer(Modifier.height(6.dp))
        Box(Modifier.padding(horizontal = 16.dp).height(1.dp).background(HmBorder))
        Spacer(Modifier.height(6.dp))
        FilterItem(Icons.Outlined.ScreenRotationAlt, "Show", "All")
        FilterItem(Icons.Outlined.PeopleOutline, "Agents", "All")
        FilterItem(Icons.Outlined.FolderOpen, "Project", "All projects")
        FilterItem(Icons.Outlined.Layers, "Include", "Group chats")
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
                    Icon(Icons.Outlined.PeopleOutline, contentDescription = null,
                        tint = HmTextPrimary, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            profiles.forEach { p ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onPick(p) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AgentAvatar(name = p, bitmap = avatars[p])
                    Spacer(Modifier.width(16.dp))
                    Text(p, color = HmTextPrimary, fontSize = 16.sp)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(HmBorder.copy(alpha = 0.5f)))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FilterItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    DropdownMenuItem(
        text = {
            Column {
                Text(title, color = HmTextPrimary, fontSize = 16.sp)
                Text(subtitle, color = HmTextMuted, fontSize = 13.sp)
            }
        },
        leadingIcon = { Icon(icon, contentDescription = null, tint = HmTextPrimary, modifier = Modifier.size(20.dp)) },
        trailingIcon = { Icon(Icons.Outlined.ArrowForwardIos, contentDescription = null, tint = HmTextMuted, modifier = Modifier.size(12.dp)) },
        onClick = { /* TODO: filter submenus */ },
    )
}

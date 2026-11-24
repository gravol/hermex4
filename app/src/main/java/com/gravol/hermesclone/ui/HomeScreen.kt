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
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircleIconButton(onClick = onOpenGateways) {
                        Icon(Icons.Outlined.Settings, contentDescription = "gateways",
                            tint = HmTextPrimary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    CircleIconButton(onClick = { /* filters panel: phase 2 (NOTES) */ }) {
                        Icon(Icons.Outlined.FilterList, contentDescription = "filters",
                            tint = HmTextPrimary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    CircleIconButton(onClick = { searchOpen = !searchOpen }) {
                        Icon(Icons.Outlined.Search,
                            contentDescription = "search",
                            tint = if (searchOpen) HmPurple else HmTextPrimary,
                            modifier = Modifier.size(20.dp))
                    }
                }
                AnimatedVisibility(visible = searchOpen) {
                    SearchField(
                        value = st.query,
                        onValueChange = vm::setQuery,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    )
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
                onClick = {
                    val p = st.selectedProfile ?: st.profiles.firstOrNull()?.name
                    if (p != null) vm.newSession(p)
                },
                containerColor = HmSurface,
                contentColor = HmTextPrimary,
                shape = CircleShape,
            ) {
                Icon(Icons.Outlined.Create, contentDescription = stringResource(R.string.new_chat))
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().background(HmBackground)) {
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

@Composable
private fun SessionChildRow(row: SessionRow, working: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
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
private fun SearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(stringResource(R.string.search_placeholder), color = HmTextMuted) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        trailingIcon = {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = HmTextMuted)
        },
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedBorderColor = HmPurple,
            unfocusedBorderColor = Color(0xFF2A3A6E),
            cursorColor = HmPurple,
            focusedTextColor = HmTextPrimary,
            unfocusedTextColor = HmTextPrimary,
        ),
        modifier = modifier,
    )
}

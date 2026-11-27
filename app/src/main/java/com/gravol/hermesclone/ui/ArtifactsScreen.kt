package com.gravol.hermesclone.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gravol.hermesclone.state.AppViewModel
import com.gravol.hermesclone.state.UiState
import com.gravol.hermesclone.util.Artifact
import com.gravol.hermesclone.util.ArtifactKind

// ---------------------------------------------------------------------------
// Artifacts — official parity page (captured from official app 2026-11-25,
// emulator-5554). Header: back circle + "Artifacts" + folder (All Files) circle.
// Search pill "Search artifacts" with clear X while typing. Sections in the
// official vc0 order (Images, Video, Documents, Code, Data, Audio, Archives,
// Links, Other), each collapsible with a chevron. Images render as a 2-column
// card grid with the name/subtitle overlaid on a bottom gradient; all other
// kinds are rows: kind icon + name + "session title · N hours ago" + a circle
// "Open source conversation" button. Data source: client-side transcript scan
// (ArtifactExtractor mirrors the official defpackage.oc0 scanner).

private val ArtSearchFill = Color(0xFF141D36)
private val ArtRowIcon = Color(0xFF8B96AD)

@Composable
fun ArtifactsScreen(st: UiState, vm: AppViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var query by remember { mutableStateOf("") }
    var collapsed by remember { mutableStateOf(setOf<ArtifactKind>()) }

    val all = st.artifacts
    val shown = if (query.isBlank()) all else all.filter {
        it.label.contains(query, true) || it.value.contains(query, true) ||
            it.sessionTitle.contains(query, true)
    }
    val byKind = shown.groupBy { it.kind }

    Scaffold(
        topBar = {
            Column(Modifier.fillMaxWidth().statusBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape)
                            .background(HmAccentCircle.copy(alpha = 0.45f))
                            .clickable { onBack() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back",
                            tint = HmTextPrimary, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Text("Artifacts", color = HmTextPrimary, fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Box(
                        Modifier.size(40.dp).clip(CircleShape)
                            .background(HmAccentCircle.copy(alpha = 0.45f))
                            .clickable { vm.artifactsNote("All Files (the session's file browser) is not ported yet") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Folder, contentDescription = "All Files",
                            tint = HmTextPrimary, modifier = Modifier.size(22.dp))
                    }
                }
                // search pill
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(ArtSearchFill)
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Search, null, tint = HmTextMuted, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text("Search artifacts", color = HmTextMuted, fontSize = 16.sp)
                        }
                        BasicTextField(
                            value = query, onValueChange = { query = it },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = HmTextPrimary, fontSize = 16.sp, fontFamily = HmFontFamily),
                            cursorBrush = SolidColor(HmLiveBlue),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (query.isNotEmpty()) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear search",
                            tint = HmTextMuted, modifier = Modifier.size(20.dp)
                                .clickable { query = "" })
                    }
                }
                if (!st.artifactsNote.isNullOrBlank()) {
                    Text(st.artifactsNote!!, color = Color(0xFFF2994A), fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                }
            }
        },
        containerColor = Color.Transparent,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
        ) {
            for (kind in ArtifactKind.Sections) {
                val items = byKind[kind]
                if (items.isNullOrEmpty()) continue
                val isCollapsed = kind in collapsed
                item(key = "hdr-$kind") {
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            collapsed = if (isCollapsed) collapsed - kind else collapsed + kind
                        }
                            .padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(kind.label, color = HmTextMuted, fontSize = 15.sp,
                            modifier = Modifier.weight(1f))
                        Icon(Icons.Outlined.ChevronRight, null, tint = HmTextMuted,
                            modifier = Modifier.size(18.dp).rotate(if (isCollapsed) 0f else 90f))
                    }
                }
                if (isCollapsed) continue
                if (kind == ArtifactKind.Image) {
                    // 2-column card grid, official geometry ~487x476 px cards
                    items.chunked(2).forEachIndexed { idx, pair ->
                        item(key = "imgrow-$idx") {
                            Row(Modifier.fillMaxWidth().padding(top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                pair.forEach { art ->
                                    Box(Modifier.weight(1f)) { ImageCard(art, st, vm) }
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    items.forEachIndexed { idx, art ->
                        item(key = "${kind.name}-${art.id}-$idx") {
                            ArtifactRow(art, st) { vm.openChatFromArtifacts() }
                            if (idx != items.lastIndex) {
                                Divider(color = HmTextMuted.copy(alpha = 0.12f), thickness = 0.5.dp,
                                    modifier = Modifier.padding(start = 44.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageCard(art: Artifact, st: UiState, vm: AppViewModel) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(HmSurface.copy(alpha = 0.75f))
            .clickable { vm.openChatFromArtifacts() },
    ) {
        val bmp = st.artifactThumbs[art.id]
        Box(Modifier.fillMaxWidth().aspectRatio(1.02f)) {
            if (bmp != null) {
                Image(bmp.asImageBitmap(), contentDescription = art.label,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize().background(HmAccentCircle.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Image, null, tint = ArtRowIcon, modifier = Modifier.size(36.dp))
                }
            }
            // name + subtitle overlaid on a bottom gradient (official)
            Column(
                Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xCC050A1E))))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(art.label, color = HmTextPrimary, fontSize = 15.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Text(artSubtitle(art, st), color = HmTextMuted, fontSize = 12.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

@Composable
private fun ArtifactRow(art: Artifact, st: UiState, onOpenChat: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onOpenChat() }
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(kindIcon(art.kind), null, tint = ArtRowIcon, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(art.label, color = HmTextPrimary, fontSize = 16.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Text(artSubtitle(art, st), color = HmTextMuted, fontSize = 13.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
        }
        Box(
            Modifier.size(44.dp).clip(CircleShape)
                .background(HmAccentCircle.copy(alpha = 0.55f))
                .clickable { onOpenChat() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = "Open source conversation",
                tint = HmTextMuted, modifier = Modifier.size(20.dp))
        }
    }
}

/** Official subtitle: "<session title> · <N> hours ago" (long form, unlike the
 *  home list's "11h"). Session title rides the record (global page). */
private fun artSubtitle(art: Artifact, st: UiState): String {
    val title = art.sessionTitle.takeIf { it.isNotBlank() } ?: "this session"
    val ago = artAgo(art.timestampMs)
    return if (ago == null) title else "$title · $ago"
}

internal fun artAgo(timestampMs: Long?, nowMs: Long = System.currentTimeMillis()): String? {
    if (timestampMs == null) return null
    val deltaMs = nowMs - timestampMs
    if (deltaMs < 60_000) return "just now"
    val mins = deltaMs / 60_000
    if (mins < 60) return if (mins == 1L) "1 minute ago" else "$mins minutes ago"
    val hrs = mins / 60
    if (hrs < 24) return if (hrs == 1L) "1 hour ago" else "$hrs hours ago"
    val days = hrs / 24
    return if (days == 1L) "1 day ago" else "$days days ago"
}

private fun kindIcon(kind: ArtifactKind) = when (kind) {
    ArtifactKind.Image -> Icons.Filled.Image
    ArtifactKind.Video -> Icons.Filled.Movie
    ArtifactKind.Audio -> Icons.Filled.MusicNote
    ArtifactKind.Code -> Icons.Filled.Code
    ArtifactKind.Document -> Icons.Filled.Description
    ArtifactKind.Data -> Icons.Filled.DataObject
    ArtifactKind.Archive -> Icons.Filled.Work
    ArtifactKind.Link -> Icons.Filled.Link
    ArtifactKind.Other -> Icons.Filled.Star
}

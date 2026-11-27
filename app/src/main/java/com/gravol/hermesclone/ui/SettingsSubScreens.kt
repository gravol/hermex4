package com.gravol.hermesclone.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.outlined.SettingsInput
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Window
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gravol.hermesclone.state.AppViewModel

// ---------------------------------------------------------------------------
// vc52 RENDER-ONLY ports of the official v1.0.0-49 settings sub-screens.
// Ground truth: off-v50-lookfeel/chatsettings(+2)/notifications/security/
// agents(+2).png captured 2026-11-27 on the Pixel 8. No persistence, no
// backend: switches/swatches/segments are local state so the controls render
// and move; nothing they select changes app behavior yet.
// ---------------------------------------------------------------------------

@Composable
private fun SubSectionHeader(text: String) {
    Text(text, color = HmTextMuted, fontSize = 13.sp,
        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 6.dp))
}

@Composable
private fun ToggleRow(label: String, desc: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = HmTextPrimary, fontSize = 15.sp)
            Text(desc, color = HmTextMuted, fontSize = 12.sp, lineHeight = 16.sp,
                modifier = Modifier.padding(top = 3.dp))
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedTrackColor = HmLiveBlue,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = HmSurface,
                uncheckedThumbColor = HmTextMuted,
                uncheckedBorderColor = HmBorder,
            ))
    }
}

// ---------------------------------------------------------------------------
// Look & Feel (off-v50-lookfeel.png)
// ---------------------------------------------------------------------------

private val hmAccents = listOf(
    "Hermes" to Color(0xFF8B5CF6),
    "Blue" to Color(0xFF447CFF),
    "Indigo" to Color(0xFF5B5BD6),
    "Teal" to Color(0xFF4BDDC7),
    "Green" to Color(0xFF66DD4B),
    "Yellow" to Color(0xFFE8C547),
    "Orange" to Color(0xFFE8884B),
    "Red" to Color(0xFFE45B5B),
    "Pink" to Color(0xFFE45B9A),
    "Custom" to Color(0xFF3A3F55),
)

@Composable
internal fun LookFeelSettingsScreen(onBack: () -> Unit) {
    var appearance by remember { mutableIntStateOf(0) } // 0 System 1 Light 2 Dark
    var accent by remember { mutableIntStateOf(0) }
    var lightBg by remember { mutableIntStateOf(0) }
    var darkBg by remember { mutableIntStateOf(0) }
    var textSize by remember { mutableStateOf(0.5f) }
    SettingsSubScreenScaffold("Look & Feel", onBack) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize()
                .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            SubSectionHeader("Appearance")
            SettingCard {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("System", "Light", "Dark").forEachIndexed { i, label ->
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(20.dp))
                                .background(if (appearance == i) HmRowHighlight else HmBackground)
                                .border(1.dp, if (appearance == i) HmLiveBlue.copy(alpha = 0.6f)
                                    else HmBorder.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                                .clickable { appearance = i }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(label, fontSize = 14.sp,
                                color = if (appearance == i) HmLiveBlue else HmTextMuted)
                        }
                    }
                }
            }
            SubSectionHeader("Accent")
            SettingCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    hmAccents.chunked(5).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEachIndexed { _, pair ->
                                val idx = hmAccents.indexOf(pair)
                                Column(horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.weight(1f)) {
                                    Box(
                                        Modifier.fillMaxWidth().height(40.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(pair.second)
                                            .border(
                                                if (accent == idx) 2.dp else 0.dp,
                                                HmTextPrimary, RoundedCornerShape(12.dp),
                                            )
                                            .clickable { accent = idx },
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(pair.first, fontSize = 10.sp, color = HmTextMuted,
                                        textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }
            }
            SubSectionHeader("Light Background")
            SettingCard {
                Row(Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("White" to Color(0xFFFFFFFF), "Paper White" to Color(0xFFF4EFE6))
                        .forEachIndexed { i, (label, color) ->
                            Box(
                                Modifier.weight(1f).height(72.dp)
                                    .clip(RoundedCornerShape(12.dp)).background(color)
                                    .border(if (lightBg == i) 2.dp else 1.dp,
                                        if (lightBg == i) HmLiveBlue else HmBorder.copy(alpha = 0.5f),
                                        RoundedCornerShape(12.dp))
                                    .clickable { lightBg = i },
                                contentAlignment = Alignment.BottomStart,
                            ) {
                                Text(label, fontSize = 12.sp, color = Color(0xFF101024),
                                    modifier = Modifier.padding(8.dp))
                            }
                        }
                }
            }
            SubSectionHeader("Dark Background")
            SettingCard {
                Row(Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("Midnight" to Color(0xFF000022), "Black" to Color(0xFF000000),
                        "Gray" to Color(0xFF1E1E24)).forEachIndexed { i, (label, color) ->
                        Box(
                            Modifier.weight(1f).height(72.dp)
                                .clip(RoundedCornerShape(12.dp)).background(color)
                                .border(if (darkBg == i) 2.dp else 1.dp,
                                    if (darkBg == i) HmLiveBlue else HmBorder.copy(alpha = 0.5f),
                                    RoundedCornerShape(12.dp))
                                .clickable { darkBg = i },
                            contentAlignment = Alignment.BottomStart,
                        ) {
                            Text(label, fontSize = 12.sp, color = HmTextPrimary,
                                modifier = Modifier.padding(8.dp))
                        }
                    }
                }
            }
            SubSectionHeader("Font")
            SettingCard {
                SettingRow(Icons.Outlined.ChatBubbleOutline, "Typeface", "Nunito")
            }
            Text("A clear view of your conversations.", color = HmTextMuted, fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp))
            SubSectionHeader("Text Size")
            SettingCard {
                SettingRow(Icons.Outlined.ChatBubbleOutline, "Choose text size", "Medium")
                Slider(
                    value = textSize, onValueChange = { textSize = it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = HmLiveBlue, activeTrackColor = HmLiveBlue,
                        inactiveTrackColor = HmSurface,
                    ),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Chat (off-v50-chatsettings(+2).png)
// ---------------------------------------------------------------------------

@Composable
internal fun ChatSettingsScreen(onBack: () -> Unit) {
    var style by remember { mutableIntStateOf(0) }
    var dismissKb by remember { mutableStateOf(false) }
    var workspaceLoc by remember { mutableStateOf(true) }
    var streamReplies by remember { mutableStateOf(true) }
    var toolCalls by remember { mutableStateOf(false) }
    var deviceActions by remember { mutableStateOf(false) }
    var thinking by remember { mutableStateOf(true) }
    var foldTurns by remember { mutableStateOf(true) }
    var linkPreviews by remember { mutableStateOf(false) }
    var memoryUpdates by remember { mutableStateOf(false) }
    var openInHermes by remember { mutableStateOf(false) }
    var activityEmoji by remember { mutableStateOf(true) }
    SettingsSubScreenScaffold("Chat", onBack) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize()
                .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            SubSectionHeader("Chat Style")
            SettingCard {
                Row(Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("Standard", "Bubbles").forEachIndexed { i, label ->
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                .background(HmBackground)
                                .border(if (style == i) 2.dp else 1.dp,
                                    if (style == i) HmLiveBlue else HmBorder.copy(alpha = 0.5f),
                                    RoundedCornerShape(12.dp))
                                .clickable { style = i }
                                .padding(10.dp),
                        ) {
                            // mini message previews (official shows sample bubbles in each card)
                            Text("Your day is all planned.", color = HmTextPrimary, fontSize = 11.sp,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                    .background(if (style == i && label == "Bubbles") HmUserBubble else Color.Transparent)
                                    .padding(4.dp))
                            Text("Sounds good!", color = HmTextPrimary, fontSize = 11.sp,
                                modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(8.dp))
                                    .background(if (label == "Bubbles") HmUserBubble else Color.Transparent)
                                    .padding(4.dp))
                            Text("I'll take care of it.", color = HmTextPrimary, fontSize = 11.sp,
                                modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(8.dp))
                                    .background(if (label == "Bubbles") HmUserBubble else Color.Transparent)
                                    .padding(4.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(label, fontSize = 13.sp, color = if (style == i) HmLiveBlue else HmTextMuted)
                        }
                    }
                }
                Text("Choose how messages appear in all your conversations.",
                    color = HmTextMuted, fontSize = 12.sp,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp))
            }
            SubSectionHeader("Keyboard")
            SettingCard {
                ToggleRow("Dismiss keyboard after sending",
                    "Off keeps the keyboard ready for your next message.", dismissKb) { dismissKb = it }
            }
            SubSectionHeader("Header")
            SettingCard {
                ToggleRow("Workspace location",
                    "Shows the folder each chat works in under the agent's name. Off shows the chat's project, when it has one.",
                    workspaceLoc) { workspaceLoc = it }
            }
            SubSectionHeader("Replies")
            SettingCard {
                ToggleRow("Stream replies",
                    "Shows each reply as Hermes writes it. Off shows the whole reply once it's finished.",
                    streamReplies) { streamReplies = it }
            }
            SubSectionHeader("In New Chats")
            SettingCard {
                ToggleRow("Tool calls", "Shows the tools Hermes uses between messages.",
                    toolCalls) { toolCalls = it }
                SettingDivider()
                ToggleRow("Device actions",
                    "Shows calendar, health and other device requests between messages.",
                    deviceActions) { deviceActions = it }
                SettingDivider()
                ToggleRow("Thinking",
                    "Shows the model's reasoning before it answers, when the model provides it.",
                    thinking) { thinking = it }
                SettingDivider()
                ToggleRow("Fold finished turns",
                    "Collapses tool calls and thinking into one row once Hermes has answered.",
                    foldTurns) { foldTurns = it }
                SettingDivider()
                ToggleRow("Link previews",
                    "Shows a preview card for each web page a reply links to. Previews are loaded by this phone.",
                    linkPreviews) { linkPreviews = it }
                SettingDivider()
                ToggleRow("Memory updates",
                    "Shows a note when Hermes updates its memory or skills after a turn.",
                    memoryUpdates) { memoryUpdates = it }
            }
            Text("Each chat can change these in its own settings. Chats you already changed keep their choice.",
                color = HmTextMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
            SubSectionHeader("Links")
            SettingCard {
                ToggleRow("Open links in Hermes Agent",
                    "Off opens web pages in your default browser.", openInHermes) { openInHermes = it }
            }
            SubSectionHeader("Activity")
            SettingCard {
                ToggleRow("Activity emoji",
                    "Show an emoji in a group chat's header for what its agents are doing.",
                    activityEmoji) { activityEmoji = it }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Notifications (off-v50-notifications.png)
// ---------------------------------------------------------------------------

@Composable
internal fun NotificationsSettingsScreen(onBack: () -> Unit) {
    var agentReplies by remember { mutableStateOf(true) }
    var approvals by remember { mutableStateOf(true) }
    var automations by remember { mutableStateOf(true) }
    var gatewayStatus by remember { mutableStateOf(true) }
    var showSource by remember { mutableStateOf(true) }
    var workingAgents by remember { mutableStateOf(true) }
    SettingsSubScreenScaffold("Notifications", onBack) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize()
                .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            SubSectionHeader("On This Device")
            SettingCard {
                Column(Modifier.padding(16.dp)) {
                    Text("Notifications allowed", color = HmTextPrimary, fontSize = 15.sp)
                    Text("Allow Hermes Agent to show messages and requests from paired instances.",
                        color = HmTextMuted, fontSize = 12.sp, lineHeight = 16.sp,
                        modifier = Modifier.padding(top = 3.dp))
                    Text("Open Android settings", color = HmLiveBlue, fontSize = 13.sp,
                        modifier = Modifier.padding(top = 10.dp).clickable { })
                }
            }
            SubSectionHeader("Categories on This Device")
            SettingCard {
                ToggleRow("Agent Replies", "", agentReplies) { agentReplies = it }
                SettingDivider()
                ToggleRow("Approvals & Questions", "", approvals) { approvals = it }
                SettingDivider()
                ToggleRow("Automations & Deliveries", "", automations) { automations = it }
                SettingDivider()
                ToggleRow("Gateway Status", "", gatewayStatus) { gatewayStatus = it }
            }
            Text("Choose sounds, vibration and how notifications appear on this device.",
                color = HmTextMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
            SubSectionHeader("Appearance")
            SettingCard {
                ToggleRow("Show Source",
                    "Shows which app a message came from, such as Telegram or WhatsApp. Applies to new notifications. Event details and routine names are always shown.",
                    showSource) { showSource = it }
            }
            SubSectionHeader("Working Agents")
            SettingCard {
                ToggleRow("Working Agents",
                    "Show which agents are working, for how long, what they are doing and any question waiting for you, in an ongoing notification while Hermes Agent is in the background. Turning this off removes it from this device.",
                    workingAgents) { workingAgents = it }
            }
            SubSectionHeader("Instances")
            SettingCard {
                SettingRow(Icons.Outlined.Hub, "Instances", null)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Security (off-v50-security.png)
// ---------------------------------------------------------------------------

@Composable
internal fun SecuritySettingsScreen(onBack: () -> Unit) {
    var requireLock by remember { mutableStateOf(false) }
    SettingsSubScreenScaffold("Security", onBack) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize()
                .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            SettingCard {
                ToggleRow("Require Screen Lock", "Required each time the app opens.",
                    requireLock) { requireLock = it }
            }
            Text("Open Security Settings", color = HmLiveBlue, fontSize = 13.sp,
                modifier = Modifier.padding(start = 4.dp, top = 12.dp).clickable { })
            Text("This device has no screen lock or biometrics set up. Add one in Android Settings to use the lock.",
                color = HmTextMuted, fontSize = 12.sp, lineHeight = 16.sp,
                modifier = Modifier.padding(start = 4.dp, top = 10.dp))
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Agents (off-v50-agents(+2).png): avatar carousel + per-agent cards.
// RENDER-ONLY — counts that need server queries are shown blank rather than
// invented; row taps do nothing yet.
// ---------------------------------------------------------------------------

@Composable
internal fun AgentsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val st by vm.state.collectAsStateWithLifecycle()
    val profiles = st.profiles
    var selected by remember { mutableStateOf(0) }
    val selName = profiles.getOrElse(selected) { "Hermes" }
        .let { if (it.isBlank() || it == "default") "Hermes" else it }
    SettingsSubScreenScaffold(
        "Agents", onBack,
        actions = {
            Box(Modifier.size(44.dp).clip(CircleShape).background(HmSurface)
                .clickable { /* render-only: agent creation not wired */ },
                contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.PersonAdd, contentDescription = "New Agent",
                    tint = HmTextPrimary, modifier = Modifier.size(20.dp))
            }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize()
                .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                items(profiles.size) { i ->
                    val name = profiles[i].let { if (it.isBlank() || it == "default") "Hermes" else it }
                    Box(modifier = Modifier.clickable { selected = i }) {
                        AgentAvatar(name = name, bitmap = st.avatars[profiles[i]],
                            avatarSize = if (i == selected) 84.dp else 52.dp)
                    }
                }
            }
            Text(selName, color = HmTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                textAlign = TextAlign.Center)
            SettingCard {
                SettingRow(Icons.Outlined.ChatBubbleOutline, "Name", selName)
                SettingDivider()
                SettingRow(Icons.Outlined.Memory, "Model", null)
                SettingDivider()
                SettingRow(Icons.Outlined.Key, "Providers", null)
                SettingDivider()
                SettingRow(Icons.Outlined.SettingsInput, "Skills", null)
            }
            SubSectionHeader("Integrations")
            SettingCard {
                SettingRow(Icons.Outlined.Hub, "Connections", null)
                SettingDivider()
                SettingRow(Icons.Outlined.Extension, "Plugins", null)
                SettingDivider()
                SettingRow(Icons.Outlined.Power, "MCP", null)
            }
            SubSectionHeader("Work")
            SettingCard {
                SettingRow(Icons.Outlined.Folder, "Projects", null)
                SettingDivider()
                SettingRow(Icons.Outlined.History, "Routines", null)
                SettingDivider()
                SettingRow(Icons.Outlined.Inventory2, "Artifacts", null)
                SettingDivider()
                SettingRow(Icons.Outlined.Window, "Kanban", null)
            }
            SubSectionHeader("Agent")
            SettingCard {
                SettingRow(Icons.Outlined.AutoAwesome, "Soul", null)
                SettingDivider()
                SettingRow(Icons.Outlined.Storage, "Memory", null)
                SettingDivider()
                SettingRow(Icons.Outlined.Tune, "Usage", null)
                SettingDivider()
                SettingRow(Icons.Outlined.Tune, "Advanced", null)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

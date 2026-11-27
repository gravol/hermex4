package com.gravol.hermesclone.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gravol.hermesclone.state.AppViewModel

/**
 * Official settings screen (v1.0.0-49, off-v50-settings.png 2026-11-27):
 * circular back + "Settings", grouped cards:
 *  [CONTINUE WITH NOUS banner — art left, centered caps text, chevron right]
 *  Agents <n> | Self-Hosted Instances <n>
 *  Notifications | Security Off
 *  Look & Feel System | Chat Standard
 *  Advanced (expandable ▸ Caching On)
 *  Contact | Documentation
 * Sub-rows open their own screens (Look & Feel / Chat / Notifications / Security /
 * Agents) — vc52 render-only ports in SettingsSubScreens.kt.
 */
private enum class SettingsPage { MAIN, LOOK_FEEL, CHAT, NOTIFICATIONS, SECURITY, AGENTS }

@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit, onOpenGateways: () -> Unit) {
    val st by vm.state.collectAsStateWithLifecycle()
    var page by remember { mutableStateOf(SettingsPage.MAIN) }
    var advancedOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = page != SettingsPage.MAIN) { page = SettingsPage.MAIN }

    when (page) {
        SettingsPage.LOOK_FEEL -> LookFeelSettingsScreen(onBack = { page = SettingsPage.MAIN })
        SettingsPage.CHAT -> ChatSettingsScreen(onBack = { page = SettingsPage.MAIN })
        SettingsPage.NOTIFICATIONS -> NotificationsSettingsScreen(onBack = { page = SettingsPage.MAIN })
        SettingsPage.SECURITY -> SecuritySettingsScreen(onBack = { page = SettingsPage.MAIN })
        SettingsPage.AGENTS -> AgentsScreen(vm, onBack = { page = SettingsPage.MAIN })
        SettingsPage.MAIN -> Unit
    }
    if (page != SettingsPage.MAIN) return

    Scaffold(
        containerColor = HmBackground,
        topBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(HmSurface)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back",
                        tint = HmTextPrimary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(14.dp))
                Text("Settings", color = HmTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // CONTINUE WITH NOUS banner (off-v50-settings.png: art left, CENTERED caps
            // title, chevron right). Art is a placeholder — no bundled Nous artwork.
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(HmSurface)
                    .clickable { /* TODO: Nous account connect */ }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1B2540)))
                Text("CONTINUE WITH NOUS", color = HmTextPrimary, fontSize = 15.sp,
                    fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                Icon(Icons.Outlined.ArrowForwardIos, null, tint = HmTextPrimary,
                    modifier = Modifier.size(14.dp))
            }

            SettingCard {
                SettingRow(Icons.Outlined.Groups, "Agents", st.profiles.size.toString(),
                    onClick = { page = SettingsPage.AGENTS })
                SettingDivider()
                SettingRow(Icons.Outlined.SettingsBackupRestore, "Self-Hosted Instances",
                    st.gateways.size.toString(), onClick = onOpenGateways)
            }

            SettingCard {
                SettingRow(Icons.Outlined.Notifications, "Notifications", null,
                    onClick = { page = SettingsPage.NOTIFICATIONS })
                SettingDivider()
                SettingRow(Icons.Outlined.Lock, "Security", "Off",
                    onClick = { page = SettingsPage.SECURITY })
            }

            SettingCard {
                SettingRow(Icons.Outlined.Palette, "Look & Feel", "System",
                    onClick = { page = SettingsPage.LOOK_FEEL })
                SettingDivider()
                SettingRow(Icons.Outlined.ChatBubbleOutline, "Chat", "Standard",
                    onClick = { page = SettingsPage.CHAT })
            }

            // vc52: official nests Caching under an expandable "Advanced" row
            // (sliders icon, off-v50-settings.png + the inline expand in the dump).
            SettingCard {
                SettingRow(Icons.Outlined.Tune, "Advanced", null,
                    trailingIcon = if (advancedOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    onClick = { advancedOpen = !advancedOpen })
                if (advancedOpen) {
                    SettingDivider()
                    SettingRow(Icons.Outlined.Bolt, "Caching", "On")
                }
            }

            SettingCard {
                SettingRow(Icons.Outlined.MailOutline, "Contact", null)
                SettingDivider()
                SettingRow(Icons.AutoMirrored.Outlined.MenuBook, "Documentation", null)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
internal fun SettingCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HmSurface.copy(alpha = 0.55f)),
        content = content,
    )
}

@Composable
internal fun SettingRow(
    icon: ImageVector,
    label: String,
    value: String?,
    trailingIcon: ImageVector? = null,
    onClick: () -> Unit = { /* inert render-only row */ },
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = HmTextPrimary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, color = HmTextPrimary, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(value, color = HmTextMuted, fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
        }
        if (trailingIcon != null) {
            Icon(trailingIcon, null, tint = HmTextMuted, modifier = Modifier.size(18.dp))
        } else {
            Icon(Icons.Outlined.ArrowForwardIos, null, tint = HmTextMuted, modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
internal fun SettingDivider() {
    Box(Modifier.padding(horizontal = 16.dp).height(1.dp).background(HmBorder.copy(alpha = 0.4f)))
}

// Shared scaffold for the sub-screens: circular back + title (official pattern).
@Composable
internal fun SettingsSubScreenScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = HmBackground,
        topBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(HmSurface)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back",
                        tint = HmTextPrimary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(14.dp))
                Text(title, color = HmTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                actions()
            }
        },
    ) { pad -> content(pad) }
}

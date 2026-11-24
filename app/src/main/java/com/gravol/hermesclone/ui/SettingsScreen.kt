package com.gravol.hermesclone.ui

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
import androidx.compose.material.icons.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gravol.hermesclone.state.AppViewModel

/**
 * Official settings screen (v1.0.0-44): circular back + "Settings", then grouped cards:
 *  [CONTINUE WITH NOUS banner]
 *  Agents <n> | Self-Hosted Instances <n>
 *  Notifications | Security Off | Caching On
 *  Look & Feel System | Chat Standard
 *  Contact | Documentation
 */
@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit, onOpenGateways: () -> Unit) {
    val st by vm.state.collectAsStateWithLifecycle()
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
            // CONTINUE WITH NOUS banner
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
                Spacer(Modifier.width(12.dp))
                Text("CONTINUE WITH NOUS", color = HmTextPrimary, fontSize = 15.sp,
                    fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.ArrowForwardIos, null, tint = HmTextPrimary,
                    modifier = Modifier.size(14.dp))
            }

            SettingCard {
                SettingRow(Icons.Outlined.Groups, "Agents", st.profiles.size.toString())
                SettingDivider()
                SettingRow(Icons.Outlined.SettingsBackupRestore, "Self-Hosted Instances",
                    st.gateways.size.toString(), onClick = onOpenGateways)
            }

            SettingCard {
                SettingRow(Icons.Outlined.Notifications, "Notifications", null)
                SettingDivider()
                SettingRow(Icons.Outlined.Lock, "Security", "Off")
                SettingDivider()
                SettingRow(Icons.Outlined.Bolt, "Caching", "On")
            }

            SettingCard {
                SettingRow(Icons.Outlined.Palette, "Look & Feel", "System")
                SettingDivider()
                SettingRow(Icons.Outlined.ChatBubbleOutline, "Chat", "Standard")
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
private fun SettingCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HmSurface.copy(alpha = 0.55f)),
        content = content,
    )
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    label: String,
    value: String?,
    onClick: () -> Unit = { /* TODO: sub-screens */ },
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
        Icon(Icons.Outlined.ArrowForwardIos, null, tint = HmTextMuted, modifier = Modifier.size(12.dp))
    }
}

@Composable
private fun SettingDivider() {
    Box(Modifier.padding(horizontal = 16.dp).height(1.dp).background(HmBorder.copy(alpha = 0.4f)))
}

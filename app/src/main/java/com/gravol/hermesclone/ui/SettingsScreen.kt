package com.gravol.hermesclone.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gravol.hermesclone.state.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val st by vm.state.collectAsStateWithLifecycle()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val versionName = androidx.compose.runtime.remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }
            .getOrNull() ?: "?"
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = HmTextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back",
                            tint = HmTextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HmBackground),
            )
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SettingCard("Gateway") {
                Text(st.activeGateway?.baseUrl ?: "not connected", fontSize = 13.sp, color = HmTextPrimary)
                Text(st.statusText, fontSize = 11.sp, color = HmTextMuted)
            }
            SettingCard("Profiles") {
                Text(
                    if (st.profiles.isEmpty()) "\u2014" else st.profiles.joinToString { it.name },
                    fontSize = 13.sp, color = HmTextPrimary,
                )
            }
            SettingCard("About") {
                Text("Hermes Clone v$versionName", fontSize = 13.sp, color = HmTextPrimary)
                Text(ctx.packageName, fontSize = 11.sp, color = HmTextMuted)
            }
            OutlinedButton(
                onClick = { vm.disconnect(); onBack() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Disconnect", color = HmDanger, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun SettingCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(HmSurface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, fontSize = 12.sp, color = HmTextMuted, fontWeight = FontWeight.SemiBold)
        content()
    }
}

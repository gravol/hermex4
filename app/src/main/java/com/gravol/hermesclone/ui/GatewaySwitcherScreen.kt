package com.gravol.hermesclone.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gravol.hermesclone.R
import com.gravol.hermesclone.model.SavedGateway
import com.gravol.hermesclone.state.AppViewModel
import com.gravol.hermesclone.state.ConnPhase

/**
 * Gateway switcher: saved gateways + "Connect to your gateway" form
 * (URL + username + password basic-auth, per CONTRACT.md / gap report).
 */
@Composable
fun GatewaySwitcherScreen(vm: AppViewModel, onBack: (() -> Unit)?) {
    val st by vm.state.collectAsStateWithLifecycle()
    var url by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HmBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(48.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(HmSurface)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Cloud, contentDescription = "back",
                        tint = HmPurple, modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
            }
            Text(
                stringResource(R.string.connect_title),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = HmTextPrimary,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.connect_hint),
            fontSize = 13.sp, color = HmTextMuted, lineHeight = 18.sp,
        )
        Spacer(Modifier.height(24.dp))

        if (st.gateways.isNotEmpty()) {
            Text(
                stringResource(R.string.saved_gateways),
                fontSize = 13.sp, color = HmTextMuted, fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(8.dp))
            st.gateways.forEach { gw ->
                GatewayRow(
                    gw = gw,
                    active = st.activeGateway?.id == gw.id,
                    phase = st.phase,
                    onConnect = { vm.connect(gw) },
                    onDelete = { vm.deleteGateway(gw.id) },
                )
            }
            if (st.phase != ConnPhase.DISCONNECTED) {
                TextButton(onClick = { vm.disconnect() }) {
                    Text(stringResource(R.string.disconnect), color = HmDanger)
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        OutlinedTextField(
            value = url, onValueChange = { url = it },
            label = { Text(stringResource(R.string.gateway_url)) },
            placeholder = { Text("http://hermes.local:9119") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = user, onValueChange = { user = it },
            label = { Text(stringResource(R.string.username)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = pass, onValueChange = { pass = it },
            label = { Text(stringResource(R.string.password)) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors(),
        )
        st.error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = HmDanger, fontSize = 14.sp)
        }
        if (st.phase == ConnPhase.CONNECTING || st.phase == ConnPhase.RECONNECTING) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = HmPurple,
                )
                Spacer(Modifier.width(10.dp))
                Text(st.statusText, color = HmTextMuted, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                if (url.isNotBlank() && user.isNotBlank()) {
                    val gw = vm.saveGateway(url, user, pass)
                    vm.connect(gw)
                }
            },
            enabled = url.isNotBlank() && user.isNotBlank(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = HmPurple),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(stringResource(R.string.connect), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun GatewayRow(
    gw: SavedGateway,
    active: Boolean,
    phase: ConnPhase,
    onConnect: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) HmRowHighlight else HmSurface)
            .clickable { onConnect() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(
                    when {
                        active && phase == ConnPhase.LIVE -> HmGreen
                        active -> HmLiveBlue
                        else -> HmTextMuted.copy(alpha = 0.4f)
                    }
                ),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(gw.host, color = HmTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(gw.username, color = HmTextMuted, fontSize = 12.sp)
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Outlined.Delete, contentDescription = "delete",
                tint = HmTextMuted, modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = HmPurple,
    unfocusedBorderColor = Color(0xFF2A3A6E),
    focusedLabelColor = HmPurple,
    unfocusedLabelColor = HmTextMuted,
    cursorColor = HmPurple,
    focusedTextColor = HmTextPrimary,
    unfocusedTextColor = HmTextPrimary,
)

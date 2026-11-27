package com.gravol.hermesclone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gravol.hermesclone.state.AppViewModel
import com.gravol.hermesclone.state.ConnPhase
import com.gravol.hermesclone.ui.GatewaySwitcherScreen
import com.gravol.hermesclone.ui.HermesCloneTheme
import com.gravol.hermesclone.ui.ChatScreen
import com.gravol.hermesclone.ui.HomeScreen
import com.gravol.hermesclone.ui.ArchivedScreen
import com.gravol.hermesclone.ui.SettingsScreen
import com.gravol.hermesclone.ui.RoutinesScreen
import com.gravol.hermesclone.ui.ArtifactsScreen
import com.gravol.hermesclone.ui.MoreSessionsScreen

// ROUTINES above CHAT: opened from the chat menu, it pushes over the chat (official).
private enum class Screen(val depth: Int) { HOME(0), SETTINGS(1), GATEWAY(1), ARCHIVED(1), MORE_SESSIONS(1), CHAT(2), ROUTINES(3), ARTIFACTS(3) }

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HermesCloneTheme {
                val st by vm.state.collectAsStateWithLifecycle()
                var showSwitcher by rememberSaveable { mutableStateOf(false) }
                var showSettings by rememberSaveable { mutableStateOf(false) }
                val disconnected = st.phase == ConnPhase.DISCONNECTED
                val onSwitcher = showSwitcher || (disconnected && st.activeGateway == null) ||
                    (disconnected && st.error != null)
                val screen = when {
                    // routines/artifacts open ON TOP of a chat (official chat-menu entry), so test first
                    st.routinesOpen -> Screen.ROUTINES
                    st.artifactsOpen -> Screen.ARTIFACTS
                    st.chat.open -> Screen.CHAT
                    st.moreSessions.open -> Screen.MORE_SESSIONS
                    st.archivedOpen -> Screen.ARCHIVED
                    showSettings -> Screen.SETTINGS
                    onSwitcher -> Screen.GATEWAY
                    else -> Screen.HOME
                }
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        // Deeper screens slide in from the right; shallower from the left.
                        // Official durations frame-verified on Pixel 8 (Hermex4 pass):
                        // push 233 ms, pop 266 ms of moving frames.
                        val forward = screen.depth > initialState.depth
                        val dur = if (forward) 240 else 270
                        val enter = if (forward) {
                            slideInHorizontally(tween(dur, easing = FastOutSlowInEasing)) { it / 3 } +
                                fadeIn(tween((dur * 0.7).toInt(), easing = LinearOutSlowInEasing))
                        } else {
                            slideInHorizontally(tween(dur, easing = FastOutSlowInEasing)) { -it / 3 } +
                                fadeIn(tween((dur * 0.7).toInt(), easing = LinearOutSlowInEasing))
                        }
                        val exit = if (forward) {
                            slideOutHorizontally(tween(dur, easing = FastOutSlowInEasing)) { -it / 3 } +
                                fadeOut(tween((dur * 0.7).toInt()))
                        } else {
                            slideOutHorizontally(tween(dur, easing = FastOutSlowInEasing)) { it / 3 } +
                                fadeOut(tween((dur * 0.7).toInt()))
                        }
                        enter togetherWith exit
                    },
                    label = "screen",
                ) { target ->
                    when (target) {
                        Screen.CHAT ->
                            ChatScreen(vm = vm, onBack = { /* state cleared by vm.closeChat */ })
                        Screen.ARCHIVED ->
                            ArchivedScreen(st, vm, onBack = { vm.closeArchived() })
                        Screen.MORE_SESSIONS ->
                            MoreSessionsScreen(vm, st, onBack = { vm.closeMoreSessions() })
                        Screen.ROUTINES ->
                            RoutinesScreen(st, vm, onBack = { vm.closeRoutines() })
                        Screen.ARTIFACTS ->
                            ArtifactsScreen(st, vm, onBack = { vm.closeArtifacts() })
                        Screen.SETTINGS ->
                            SettingsScreen(vm, onBack = { showSettings = false }, onOpenGateways = { showSwitcher = true })
                        Screen.GATEWAY ->
                            GatewaySwitcherScreen(
                                vm = vm,
                                onBack = if (showSwitcher && !disconnected) {
                                    { showSwitcher = false }
                                } else null,
                            )
                        Screen.HOME ->
                            HomeScreen(
                                vm = vm,
                                onOpenGateways = { showSwitcher = true },
                                onSettings = { showSettings = true },
                            )
                    }
                }
            }
        }
    }
}

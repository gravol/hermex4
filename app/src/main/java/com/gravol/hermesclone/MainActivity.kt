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
import com.gravol.hermesclone.ui.SettingsScreen

private enum class Screen(val depth: Int) { HOME(0), SETTINGS(1), GATEWAY(1), CHAT(2) }

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
                    st.chat.open -> Screen.CHAT
                    showSettings -> Screen.SETTINGS
                    onSwitcher -> Screen.GATEWAY
                    else -> Screen.HOME
                }
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        // Deeper screens slide in from the right; shallower from the left —
                        // the standard Material3 predictive back shape, 320ms FastOutSlowIn.
                        val forward = screen.depth > initialState.depth
                        val enter = if (forward) {
                            slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it / 3 } +
                                fadeIn(tween(220, easing = LinearOutSlowInEasing))
                        } else {
                            slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { -it / 3 } +
                                fadeIn(tween(220, easing = LinearOutSlowInEasing))
                        }
                        val exit = if (forward) {
                            slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) { -it / 3 } +
                                fadeOut(tween(220))
                        } else {
                            slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) { it / 3 } +
                                fadeOut(tween(220))
                        }
                        enter togetherWith exit
                    },
                    label = "screen",
                ) { target ->
                    when (target) {
                        Screen.CHAT ->
                            ChatScreen(vm = vm, onBack = { /* state cleared by vm.closeChat */ })
                        Screen.SETTINGS ->
                            SettingsScreen(vm = vm, onBack = { showSettings = false })
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

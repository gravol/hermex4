package com.gravol.hermesclone.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gravol.hermesclone.model.CronJob
import com.gravol.hermesclone.state.AppViewModel
import com.gravol.hermesclone.state.UiState
import kotlin.math.max

// ---------------------------------------------------------------------------
// Routines — official parity page (verified on official app 2026-11-24).
// Cards: state icon (green check = scheduled, orange pause = paused/completed),
// name, second line = script name else prompt (1 line, ellipsized), third line
// = humanized schedule + " · next in X" (paused rows show no countdown).
// Order: next_run_at asc, paused (null next) first. "+" creation is dashboard-
// only for now (official posts through its own create form; not ported).

private val HmRoutinePause = Color(0xFFF2994A)

@Composable
fun RoutinesScreen(st: UiState, vm: AppViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .background(HmAccentCircle.copy(alpha = 0.45f))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back",
                        tint = HmTextPrimary, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(16.dp))
                Text("Routines", color = HmTextPrimary, fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .background(HmAccentCircle.copy(alpha = 0.45f))
                        .clickable { vm.routinesNote("Creating and editing routines lives in the dashboard for now") },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "add routine",
                        tint = HmLiveBlue, modifier = Modifier.size(22.dp))
                }
            }
        },
        containerColor = Color.Transparent,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
        if (!st.routinesNote.isNullOrBlank()) {
            Text(st.routinesNote, color = HmRoutinePause, fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        if (st.routinesLoading && st.routines.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = HmLiveBlue, strokeWidth = 2.dp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(st.routines, key = { "rt-${it.id}" }) { job -> RoutineCard(job) }
            }
        }
        }
    }
}

@Composable
private fun RoutineCard(job: CronJob) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HmSurface.copy(alpha = 0.75f))
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.Top,
    ) {
        val paused = job.state != "scheduled"
        Icon(
            imageVector = if (paused) Icons.Filled.PauseCircle else Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = if (paused) HmRoutinePause else HmTurnCheck,
            modifier = Modifier.size(19.dp).padding(top = 1.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(job.name, color = HmTextPrimary, fontSize = 16.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            val second = job.script ?: job.prompt
            if (!second.isNullOrBlank()) {
                Text(second, color = HmTextMuted, fontSize = 13.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
            }
            val line = buildString {
                append(humanizeSchedule(job))
                val cd = job.nextRunAt?.let { countdown(it - System.currentTimeMillis()) }
                if (cd != null) append(" · $cd")
            }
            Text(line, color = HmTextMuted.copy(alpha = 0.75f), fontSize = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = HmTextMuted,
            modifier = Modifier.size(18.dp).padding(top = 2.dp))
    }
}

/** Official humanizes client-side (verified 2026-11-24): interval -> "Every N
 *  minutes"; "M H * * *" -> "Daily at HH:MM"; anything else raw cron display. */
internal fun humanizeSchedule(job: CronJob): String {
    if (job.scheduleKind == "interval") {
        val m = job.scheduleMinutes ?: return job.scheduleCron ?: "interval"
        return if (m % 60 == 0 && m >= 60) {
            val h = m / 60
            if (h == 1) "Every hour" else "Every $h hours"
        } else if (m == 1) "Every minute" else "Every $m minutes"
    }
    if (job.scheduleKind == "once") return job.scheduleCron ?: "once"
    val expr = job.scheduleCron?.trim() ?: return ""
    val parts = expr.split(Regex("\\s+"))
    if (parts.size == 5 && parts[2] == "*" && parts[3] == "*" && parts[4] == "*") {
        val mm = parts[0].toIntOrNull()
        val hh = parts[1].toIntOrNull()
        if (hh != null && mm != null && mm in 0..59 && hh in 0..23)
            return "Daily at %02d:%02d".format(hh, mm)
    }
    return expr
}

/** Official countdowns seen: "next in 6 minutes", "next in 7 hours", "next in 21 hours". */
internal fun countdown(deltaMs: Long): String? {
    if (deltaMs < 0) return null // due/running — official text for this state unobserved
    val mins = max(1L, deltaMs / 60_000L)
    if (mins < 60) return if (mins == 1L) "next in 1 minute" else "next in $mins minutes"
    val hrs = mins / 60
    if (hrs < 24) return if (hrs == 1L) "next in 1 hour" else "next in $hrs hours"
    val days = hrs / 24
    return if (days == 1L) "next in 1 day" else "next in $days days"
}

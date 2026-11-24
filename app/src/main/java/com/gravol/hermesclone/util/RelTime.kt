package com.gravol.hermesclone.util

import java.util.concurrent.TimeUnit

/** Relative time like the official home list: "now", "5m", "12h", "5w", "14d". */
fun relTime(epochSeconds: Long?, nowMs: Long = System.currentTimeMillis()): String {
    if (epochSeconds == null) return ""
    val deltaMs = nowMs - epochSeconds * 1000L
    if (deltaMs < 0) return "now"
    val min = TimeUnit.MILLISECONDS.toMinutes(deltaMs)
    if (min < 1) return "now"
    if (min < 60) return "${min}m"
    val h = TimeUnit.MILLISECONDS.toHours(deltaMs)
    if (h < 24) return "${h}h"
    val d = TimeUnit.MILLISECONDS.toDays(deltaMs)
    if (d < 7) return "${d}d"
    val w = d / 7
    if (w < 52) return "${w}w"
    return "${w / 52}y"
}

package com.gravol.hermesclone.util

import com.gravol.hermesclone.model.ChatMsg
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** One row from `complete.slash` (VERIFIED server shape tui_gateway/methods_complete.py:
 *  {items:[{text, display, meta, kind:"command"|"skill"}], replace_from}). */
data class SlashItem(
    val text: String,
    val meta: String,
    val kind: String,
)

/** commands.catalog parsed just far enough to render /help and category badges.
 *  Wire (VERIFIED tui_gateway/command_discovery.py): {pairs:[[key,desc]], categories:
 *  [{name, pairs:[[key,desc]]}], skills:{"/name":{usage,origin}}, ...}. Registry
 *  commands live in `categories`; skill pairs ride in the flat `pairs` only. */
data class SlashCatalog(
    val categories: List<Pair<String, List<Pair<String, String>>>>,
    val skills: List<Pair<String, String>>,
    val commandCategories: Map<String, String>, // "/name" -> category (popup badge)
)

/**
 * Official-mobile slash semantics (VERIFIED on-device vs ai.nousresearch.hermes.mobile
 * 1.0.0-54, 2026-11-28 — zero gateway footprint for any of these):
 *  - position-0 `/name` is intercepted CLIENT-side; it never reaches prompt.submit.
 *  - /help  -> category-grouped catalog bullets, then skills, rendered locally.
 *  - /model -> `Current model: <model> (<provider>)` monospace card.
 *  - /history -> `Conversation History` card, `[You #n]` / `[Hermes #n]` real rows only.
 *  - /new  -> local transcript clear (no server session; next message mints one).
 *  - unknown -> `Unknown command /x — nothing was sent.` + `Type /help for the full list.`
 * Output rows are ephemeral: they survive back-nav/reopen (client overlay) but die on
 * app kill and never appear in the server history or /history itself.
 */
object SlashCommands {

    // ^-anchored single bare segment (mirrors the desktop looksLikeSlashCommand):
    // "/usr/local" is a path, not a command; "/retry x" is a command with args.
    private val CMD_RE = Regex("^/([a-zA-Z][\\w-]*)(?:\\s.*)?$")

    fun commandName(text: String): String? =
        CMD_RE.matchEntire(text.trim())?.groupValues?.get(1)?.lowercase()

    fun parseCompletions(res: JsonObject?): Pair<List<SlashItem>, Int?> {
        val items = (res?.get("items") as? JsonArray)?.mapNotNull {
            val o = it as? JsonObject ?: return@mapNotNull null
            val t = (o["text"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            SlashItem(t, (o["meta"] as? JsonPrimitive)?.contentOrNull ?: "",
                (o["kind"] as? JsonPrimitive)?.contentOrNull ?: "command")
        } ?: emptyList()
        val rf = (res?.get("replace_from") as? JsonPrimitive)?.content?.toIntOrNull()
        return items to rf
    }

    fun parseCatalog(res: JsonObject?): SlashCatalog? {
        res ?: return null
        val desc = HashMap<String, String>()
        (res["pairs"] as? JsonArray)?.forEach {
            val a = it as? JsonArray ?: return@forEach
            if (a.size >= 2) desc[a[0].jsonPrimitive.content] = a[1].jsonPrimitive.content
        }
        val cats = (res["categories"] as? JsonArray)?.mapNotNull {
            val o = it as? JsonObject ?: return@mapNotNull null
            val name = (o["name"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val pairs = (o["pairs"] as? JsonArray)?.mapNotNull { p ->
                val a = p as? JsonArray ?: return@mapNotNull null
                if (a.size >= 2) a[0].jsonPrimitive.content to a[1].jsonPrimitive.content else null
            } ?: emptyList()
            name to pairs
        } ?: emptyList()
        val skillKeys = (res["skills"] as? JsonObject)?.keys ?: emptySet()
        val skills = skillKeys.sorted().map { it to (desc[it] ?: "Skill") }
        val cmdCats = HashMap<String, String>()
        cats.forEach { (cat, pairs) -> pairs.forEach { (k, _) -> cmdCats[k.lowercase()] = cat } }
        return SlashCatalog(cats, skills, cmdCats)
    }

    /** Official /help layout (off-slash-sent/off-help-* captures): category header line,
     *  then `● /name — description` bullets; skills follow as plain bullets. */
    fun helpMarkdown(cat: SlashCatalog): String = buildString {
        cat.categories.forEach { (name, pairs) ->
            appendLine(name)
            pairs.forEach { (k, d) -> appendLine("\u2022 $k \u2014 $d") }
            appendLine()
        }
        appendLine("Skills")
        cat.skills.forEach { (k, d) -> appendLine("\u2022 $k \u2014 $d") }
    }.trimEnd()

    /** Official /model output — fenced code block renders as the monospace card.
     *  VERIFIED official shows the provider SLUG only ("custom", not "custom:clerk-…"). */
    fun modelMarkdown(model: String?, provider: String?): String =
        "```\nCurrent model: ${model?.takeIf { it.isNotBlank() } ?: "unknown"} " +
            "(${provider?.substringBefore(":")?.takeIf { it.isNotBlank() } ?: "unknown"})\n```"

    /** Official /history card (VERIFIED dump 2026-11-28): header, box-drawing rule,
     *  `[You #n] text` / `[Hermes #n] text` for REAL messages only (slash rows excluded). */
    fun historyMarkdown(messages: List<ChatMsg>, agentName: String): String {
        val real = messages.filter { !it.local && it.content.isNotBlank() &&
            (it.role == "user" || it.role == "assistant") }
        val sb = StringBuilder("Conversation History\n").append("\u2500".repeat(40)).append("\n")
        real.forEachIndexed { i, m ->
            val who = if (m.role == "user") "You" else agentName.ifBlank { "Hermes" }
            sb.appendLine("[$who #${i + 1}] ${m.content.trim()}")
        }
        return "```\n${sb.toString().trimEnd()}\n```"
    }

    fun unknownMarkdown(name: String): String =
        "```\nUnknown command /$name \u2014 nothing was sent.\nType /help for the full list.\n```"
}

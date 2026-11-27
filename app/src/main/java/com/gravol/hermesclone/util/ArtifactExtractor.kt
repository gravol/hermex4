package com.gravol.hermesclone.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// ---------------------------------------------------------------------------
// Artifacts — 1:1 port of apps/desktop/src/app/artifacts/artifact-utils.ts
// (the official mobile app is an obfuscated build of this same contract — the
// decompiled dex oc0/mc0/df agreed with the TS before the source was found).
//
// KEY FACTS (all from the TS):
//  * The Artifacts page is GLOBAL: the 30 most recent sessions, each fully
//    paginated (100 msgs/page, order=oldest, includeCompacted=true).
//  * assistant rows: scan text only (MEDIA tags, md images/links, URLs, paths).
//  * tool rows: text scanned as prose only for producer tools (create/download/
//    export/generate/render/save/speech/tts/write, bfl_flux3_*) or terminal;
//    browser_vision additionally yields "Screenshot path:" matches; parsed-JSON
//    string values pass only when their key path hits a strong/explicit artifact
//    key, or terminal output|stdout|path (then the value is re-scanned as prose).
//  * dedup key = "<session id>:<value>"; records carry session title + timestamp.
//  * user rows are never scanned.

/** One raw gateway row (tool rows kept — the chat parser folds them away). */
data class ArtifactRow(
    val role: String,
    val content: String,
    val toolName: String?,
    val timestamp: Long?,
    /** raw JSON-string arguments of the assistant message's tool_calls (mobile scans the
     *  message JSON blob, so command-line paths/URLs inside args reach the artifact list) */
        val toolCalls: List<Pair<String, String>> = emptyList(), // (name, arguments) — official scans these too
)

/** One gateway page: filtered scan rows + the server's RAW returned count. */
data class ArtifactPage(val rows: List<ArtifactRow>, val returned: Int)

data class ArtifactSession(
    val id: String,
    val title: String,
    val preview: String?,
    val lastActive: Long?,
    val startedAt: Long?,
)

enum class ArtifactKind(val label: String, val exts: String) {
    Image("Images", "png jpg jpeg gif webp svg bmp heic heif avif tif tiff ico icns"),
    Video("Video", "mp4 mov m4v avi mkv webm mpg mpeg wmv flv"),
    Audio("Audio", "mp3 wav m4a aac flac ogg oga opus aiff aif caf"),
    Code("Code", "swift m mm h hpp hxx c cc cpp cxx cs java kt kts scala sc go rs rb erb php py pyw pyi js mjs cjs jsx ts tsx vue svelte dart ex exs erl hs lhs lua pl r sql sh bash zsh fish ps1 bat cmd html htm css scss sass less graphql gql proto ipynb gradle cmake mk podspec rake vim"),
    Document("Documents", "pdf doc docx rtf txt text md markdown mdx pages odt epub tex key ppt pptx odp"),
    Data("Data", "json json5 jsonl ndjson yaml yml toml xml csv tsv plist ini env cfg conf log parquet sqlite sqlite3 db xls xlsx ods numbers xcconfig"),
    Archive("Archives", "zip tar gz tgz bz2 xz 7z rar dmg pkg jar war iso ipa apk"),
    Link("Links", ""),
    Other("Other Files", "");

    companion object {
        // Official mobile section order (live capture 2026-11-25): Images, Video,
        // Documents, Code, Data, Audio, Archives, Links
        val Sections = listOf(Image, Video, Document, Code, Data, Audio, Archive, Link, Other)
        private val extMap: Map<String, ArtifactKind> = buildMap {
            for (k in values()) for (e in k.exts.split(' ')) if (e.isNotBlank()) put(e, k)
        }
        fun byExt(ext: String): ArtifactKind = extMap[ext.lowercase()] ?: Other
        /** for URLs: unknown/absent extension means Link, not Other */
        fun extOr(ext: String, fallback: ArtifactKind): ArtifactKind = extMap[ext.lowercase()] ?: fallback
    }
}

enum class ArtifactKind3 { IMAGE, FILE, LINK }

private typealias Push = (String, Boolean) -> Unit

data class Artifact(
    val id: String,          // dedup key "<session id>:<value>"
    val kind3: ArtifactKind3,
    val kind: ArtifactKind,  // mobile UI section (by extension)
    val value: String,       // normalized full string (path or URL)
    val label: String,       // display name (last non-empty path segment)
    val sessionId: String,
    val sessionTitle: String,
    val timestampMs: Long,
    val profile: String = "default",
)

object ArtifactExtractor {

    private const val MAX_UNIX_SECONDS = 10_000_000_000L

    // ---- regexes (verbatim from artifact-utils.ts / parts.ts) ----
    // bare character-class BODY (no brackets!) — used as [$WS] / [^$WS]
    private const val WS = "\\t\\n\\u000B\\u000C\\r \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF"

    private val MARKDOWN_IMAGE = Regex("!\\[([^\\]]*)\\]\\(([^)$WS]+)\\)")
    private val MARKDOWN_LINK = Regex("\\[([^\\]]+)\\]\\(([^)$WS]+)\\)")
        private val URL_RE = Regex("https?://[^$WS<>\"')`]+")
    private val PATH_RE = Regex("(?:^|[$WS(\"'`])((?:/|~[/\\\\]|\\.\\.?[/\\\\]|\\\\\\\\)[^$WS\"'`<>]+(?:\\.[a-z0-9]{1,8})?)", RegexOption.IGNORE_CASE)
    private val WINDOWS_PATH_RE = Regex("(?:^|[$WS(\"'`])([A-Za-z]:[/\\\\][^$WS\"'`<>]+(?:\\.[a-z0-9]{1,8})?)", RegexOption.IGNORE_CASE)
    private val IMAGE_EXT = Regex("\\.(?:png|jpe?g|gif|webp|svg|bmp)(?:\\?.*)?$", RegexOption.IGNORE_CASE)
    private val FILE_EXT = Regex("\\.(?:png|jpe?g|gif|webp|svg|bmp|pdf|txt|json|md|csv|xlsx?|docx?|pptx?|html|zip|tar|gz|avi|flac|m4a|mkv|mp3|ogg|opus|wav|webm|mp4|mov)(?:\\?.*)?$", RegexOption.IGNORE_CASE)

    private val PRODUCER_TOOL = Regex("(?:^|_)(?:creat(?:e|ion)|download|export|generat(?:e|ion)|render|save|speech|tts|write)(?:_|$)", RegexOption.IGNORE_CASE)
    private val STRONG_KEY = Regex("^(?:artifact_(?:file|image|path|url)|files?_(?:created|modified|written)|generated_(?:file|image|path|url)|media_tag|output_(?:file|path|url)|result_(?:file|path|url)|saved_to|screenshot_path)$", RegexOption.IGNORE_CASE)
    private val PRODUCER_KEY = Regex("^(?:artifact(?:s|_(?:file|image|path|url))?|attachment(?:s|_(?:file|image|path|url))?|download(?:s|_(?:file|path|url))?|(?:audio|image|video)(?:_(?:file|path|url))?|file_path|local_path|media(?:_(?:file|path|url))?|path)$", RegexOption.IGNORE_CASE)
    private val SHELL_OUTPUT_KEY = Regex("^(?:output|stdout|path)$", RegexOption.IGNORE_CASE)
    private val SCREENSHOT_PATH = Regex("Screenshot path:[$WS]*([^\\r\\n<>]+)", RegexOption.IGNORE_CASE)

    // MEDIA tag (parts.ts MEDIA_TAG_RE, simplified alternation: quoted | any non-space run)
    private val MEDIA_TAG = Regex("[`\"']?MEDIA:[$WS]*(`[^`\n]+`|\"[^\n]+\"|'[^'\n]+'|[^\\s`\"]+)")
    private const val MEDIA_TRAILING_PUNCT = ".,;:!?"

    // noise filters (artifact-utils.ts)
    private val PIP_CACHE_DIR = Regex("/(?:\\.cache/pip|library/caches/pip|appdata/local/pip/cache)/")
    private val WHEEL = Regex("\\.whl(?:\\.metadata)?$")
    private val PACKAGE_INDEX_DIST = Regex("/packages/.+\\.(?:tar\\.gz|tar\\.bz2|zip|egg)(?:\\.metadata)?$")

    private val PATH_LIKE = Regex("^(?:file:|/|[~.][/\\\\]|\\.\\.[/\\\\]|[a-z]:[/\\\\]|\\\\\\\\)", RegexOption.IGNORE_CASE)
    private val URL_LIKE = Regex("^(?:https?|file)://", RegexOption.IGNORE_CASE)
    private val JSON = Json { ignoreUnknownKeys = true }

    /**
     * One session, paginated exactly like the official mobile loader (xf6.java):
     * loadPage(offset) -> raw rows oldest-first, 500/page. Stop when a page
     * returns < 500 rows or after 5000 raw rows (official hard cap); failures of
     * a whole session are swallowed.
     */
    fun forSession(
        session: ArtifactSession,
        profile: String = "default",
        loadPage: (offset: Int) -> ArtifactPage?,
    ): List<Artifact> {
        val title = session.title.trim().ifEmpty {
            session.preview?.trim()?.ifEmpty { null } ?: "Untitled session"
        }
        val found = LinkedHashMap<String, Artifact>()
        var offset = 0
        while (offset < 5000) { // official mobile caps the transcript scan at 5000 rows
            val pg = try { loadPage(offset) } catch (t: Throwable) {
                android.util.Log.d("HMART", "loadfail " + t)
                null
            } ?: break
            if (pg.rows.isEmpty() && pg.returned == 0) break
            try {
                collectSession(session, title, profile, pg.rows, found)
            } catch (t: Throwable) {
                android.util.Log.d("HMART", "scanfail " + android.util.Log.getStackTraceString(t))
                break
            }
            if (pg.returned < 500) break
            offset += pg.returned
        }
        return found.values.toList()
    }

    private fun collectSession(
        session: ArtifactSession,
        title: String,
        profile: String,
        rows: List<ArtifactRow>,
        found: LinkedHashMap<String, Artifact>,
    ) {
        for (row in rows) {
            if (row.role != "assistant" && row.role != "tool") continue
            collectMessage(row) { candidate, explicit ->
                val value = normalizeValue(decodeMediaHref(candidate))
                if (value.isEmpty() || !looksLikeArtifact(value, explicit)) return@collectMessage
                val key = "${session.id}:$value"
                if (found.containsKey(key)) return@collectMessage
                found[key] = Artifact(
                    id = "$profile:${session.id}:$value",
                    kind3 = artifactKind3(value),
                    kind = uiSection(value),
                    value = value,
                    label = artifactLabel(value),
                    sessionId = session.id,
                    sessionTitle = title,
                    timestampMs = artifactTimestamp(row, session),
                    profile = profile,
                )
            }
        }
    }

    private fun collectMessage(row: ArtifactRow, push: Push) {
        val text = row.content
        if (row.role == "assistant") {
            if (text.isNotBlank()) collectArtifactsFromText(text, push)
            // Official df.java case 7: for assistant rows the scanned "str" is each
            // tool_call's arguments, gated by the SAME producer/terminal tool-name
            // rule as tool rows (browser_navigate is not a producer -> its args are
            // skipped; browser_vision args are scanned for "Screenshot path:" lines).
            for ((callName, callArgs) in row.toolCalls) {
                val cn = callName.trim().lowercase()
                val producer = PRODUCER_TOOL.containsMatchIn(cn) || cn.startsWith("bfl_flux3_")
                val terminal = cn == "terminal"
                if (callArgs.isBlank() || (!producer && !terminal)) continue
                collectArtifactsFromText(callArgs, push)
                if (cn == "browser_vision") {
                    SCREENSHOT_PATH.findAll(callArgs).forEach { push(it.groupValues[1], false) }
                }
                parseMaybeJson(callArgs)?.let { parsed ->
                    collectStringValues(parsed, "tool_call") { value, keyPath ->
                        val segments = keyPath.split('.').filter { it.isNotEmpty() && !it.matches(Regex("\\d+")) }
                        val shellOutput = terminal && segments.any { SHELL_OUTPUT_KEY.matches(it) }
                        if (!shellOutput && !explicitToolArtifactKey(keyPath, producer)) return@collectStringValues
                        if (shellOutput) {
                            if (value.isNotEmpty()) collectArtifactsFromText(value, push)
                            return@collectStringValues
                        }
                        collectMediaValues(value) { v, e -> push(v, e) }
                        val normalized = normalizeValue(decodeMediaHref(value))
                        if (normalized.isNotEmpty() && looksLikeArtifact(normalized)) push(normalized, false)
                    }
                }
            }
            return
        }
        val name = (row.toolName ?: "").trim().lowercase()
        val producer = PRODUCER_TOOL.containsMatchIn(name) || name.startsWith("bfl_flux3_")
        val terminal = name == "terminal"

        if (text.isNotBlank() && (producer || terminal)) collectArtifactsFromText(text, push)

        if (name == "browser_vision" && text.isNotBlank()) {
            SCREENSHOT_PATH.findAll(text).forEach { push(it.groupValues[1], false) }
        }

        // parseToolPayloads: try JSON on raw text, then on the untrusted_tool_result inner payload
        val payloads = ArrayList<JsonElement>()
        parseMaybeJson(text)?.let { payloads.add(it) }
        untrustedToolPayload(text)?.let { inner -> parseMaybeJson(inner)?.let { payloads.add(it) } }

        for (parsed in payloads) {
            collectStringValues(parsed, "tool_result") { value, keyPath ->
                val segments = keyPath.split('.').filter { it.isNotEmpty() && !it.matches(Regex("\\d+")) }
                val shellOutput = terminal && segments.any { SHELL_OUTPUT_KEY.matches(it) }
                if (!shellOutput && !explicitToolArtifactKey(keyPath, producer)) return@collectStringValues
                if (shellOutput) {
                    // shell result is free text: scan as prose, not as one path
                    if (value.isNotEmpty()) collectArtifactsFromText(value, push)
                    return@collectStringValues
                }
                collectMediaValues(value) { v, e -> push(v, e) }
                val normalized = normalizeValue(decodeMediaHref(value))
                if (normalized.isNotEmpty() && looksLikeArtifact(normalized)) push(normalized, false)
            }
        }
    }

    // ---- text scanning ----
    private fun collectArtifactsFromText(text: String, push: Push) {
        collectMediaValues(text, push)
        MARKDOWN_IMAGE.findAll(text).forEach { push(it.groupValues[2], false) }
        MARKDOWN_LINK.findAll(text).forEach { m ->
            if (m.range.first > 0 && text[m.range.first - 1] == '!') return@forEach // image, not link
            val value = decodeMediaHref(m.groupValues[2])
            if (looksLikeArtifact(value)) push(value, false)
        }
        URL_RE.findAll(text).forEach {
            if (looksLikeArtifact(it.value)) push(it.value, false)
        }
        PATH_RE.findAll(text).forEach { push(it.groupValues[1], false) }
        WINDOWS_PATH_RE.findAll(text).forEach { push(it.groupValues[1], false) }
    }

    private fun collectMediaValues(text: String, push: Push) {
        MEDIA_TAG.findAll(text).forEach { m ->
            var value = m.groupValues[1]
            if (!isPlausibleMediaPath(unquoteMediaPath(value))) return@forEach
            // shed trailing prose punctuation unless it would gut the path
            var end = value.length
            while (end > 0 && MEDIA_TRAILING_PUNCT.indexOf(value[end - 1]) >= 0) {
                if (!isPlausibleMediaPath(value.substring(0, end - 1))) break
                end -= 1
            }
            value = value.substring(0, end)
            push(value, true)
        }
    }

    private fun unquoteMediaPath(value: String): String {
        val trimmed = value.trim()
        if (trimmed.length >= 2 && trimmed.first() == trimmed.last() &&
            (trimmed.first() == '"' || trimmed.first() == '\'' || trimmed.first() == '`')
        ) {
            return trimmed.substring(1, trimmed.length - 1)
        }
        val last = trimmed.lastOrNull()
        return if (last == '`' || last == '"') trimmed.dropLast(1) else trimmed
    }

    private fun isPlausibleMediaPath(value: String): Boolean =
        value.contains('/') || value.contains('\\') || Regex("\\.[^.]").containsMatchIn(value)

    // ---- key classification ----
    private val NUMERIC = Regex("\\d+")

    private fun explicitToolArtifactKey(keyPath: String, producerTool: Boolean): Boolean {
        // strip the "tool_result" root prefix the collector seeds
        val path = if (keyPath == "tool_result" || keyPath.startsWith("tool_result."))
            keyPath.removePrefix("tool_result").removePrefix(".") else keyPath
        return path.split('.')
            .filter { it.isNotEmpty() && !NUMERIC.matches(it) }
            .any { STRONG_KEY.matches(it) || (producerTool && PRODUCER_KEY.matches(it)) }
    }

    private fun collectStringValues(el: JsonElement, keyPath: String, collector: (String, String) -> Unit) {
        when (el) {
            is JsonPrimitive -> {
                val v = runCatching { el.contentOrNull }.getOrNull() ?: el.toString()
                collector(v, keyPath)
            }
            is JsonArray -> el.forEachIndexed { i, child -> collectStringValues(child, "$keyPath.$i", collector) }
            is JsonObject -> el.forEach { (k, child) -> collectStringValues(child, if (keyPath.isEmpty()) k else "$keyPath.$k", collector) }
            else -> {}
        }
    }

    // ---- validation / normalization ----
    private fun parseMaybeJson(value: String): JsonElement? {
        if (value.isBlank()) return null
        return runCatching { JSON.parseToJsonElement(value) }.getOrNull()
    }

    private fun untrustedToolPayload(value: String): String? {
        val trimmed = value.trim()
        val open = Regex("^<untrusted_tool_result\\b[^>]*>[$WS]*").find(trimmed) ?: return null
        val closeIndex = trimmed.lastIndexOf("</untrusted_tool_result>")
        if (closeIndex <= open.value.length) return null
        val wrapped = trimmed.substring(open.value.length, closeIndex).trim()
        val payloadStart = wrapped.indexOf("\n\n")
        return (if (payloadStart == -1) wrapped else wrapped.substring(payloadStart + 2)).trim()
    }

    private fun decodeMediaHref(value: String): String {
        if (!value.startsWith("#media:")) return value
        return runCatching { java.net.URLDecoder.decode(value.substring(7), "UTF-8") }.getOrNull() ?: value
    }

    private fun normalizeValue(value: String): String {
        var trimmed = value.trim()
        for (i in 0 until 3) {
            val quote = trimmed.firstOrNull() ?: break
            if (quote != trimmed.lastOrNull() || (quote != '"' && quote != '\'' && quote != '`')) break
            trimmed = trimmed.substring(1, trimmed.length - 1).trim()
        }
        // old-mobile oc0.h: cut at first backslash — escaped JSON values carry literal \n
        trimmed = trimmed.split('\\').firstOrNull() ?: trimmed
        return trimmed
            .replace(Regex("[`*]+$"), "")
            .replace(Regex("[),.;]+$"), "")
    }

    private fun artifactPathForFiltering(value: String): String {
        if (URL_LIKE.containsMatchIn(value)) {
            runCatching {
                val u = java.net.URI(value)
                java.net.URLDecoder.decode(u.path ?: "", "UTF-8").lowercase()
            }.getOrNull()?.let { return it }
        }
        return value.replace('\\', '/').lowercase()
    }

    private fun isPythonPackageDownload(value: String): Boolean {
        val path = artifactPathForFiltering(value)
        return PIP_CACHE_DIR.containsMatchIn(path) || WHEEL.containsMatchIn(path) ||
            (Regex("^https?://", RegexOption.IGNORE_CASE).matches(value) && PACKAGE_INDEX_DIST.containsMatchIn(path))
    }

    private fun looksLikeArtifact(value: String, explicit: Boolean = false): Boolean {
        if (!explicit && isPythonPackageDownload(value)) return false
        if (value.startsWith("http://") || value.startsWith("https://") || value.startsWith("data:image/")) return true
        if (!(value.startsWith("http://") || value.startsWith("https://") ||
                value.startsWith("data:image/") || PATH_LIKE.containsMatchIn(value))
        ) {
            return false
        }
        if (explicit) return true
        return IMAGE_EXT.containsMatchIn(value) || FILE_EXT.containsMatchIn(value)
    }

    private fun artifactKind3(value: String): ArtifactKind3 = when {
        value.startsWith("data:image/") || IMAGE_EXT.containsMatchIn(value) -> ArtifactKind3.IMAGE
        PATH_LIKE.containsMatchIn(value) -> ArtifactKind3.FILE
        else -> ArtifactKind3.LINK
    }

    /** mobile UI section: extension map; URLs without a known ext -> Link. */
    private fun uiSection(value: String): ArtifactKind {
        if (value.startsWith("data:image/")) return ArtifactKind.Image
        val pth = if (URL_LIKE.containsMatchIn(value)) {
            value.substringAfter("://").substringAfter("/", "")
                .substringBefore('?').substringBefore('#').trimEnd('/')
        } else value.substringBefore('?').substringBefore('#')
        val seg = pth.split('/', '\\').lastOrNull { it.isNotEmpty() } ?: ""
        val ext = if (seg.contains('.')) seg.substringAfterLast('.').lowercase() else ""
        return if (URL_LIKE.containsMatchIn(value)) ArtifactKind.extOr(ext, ArtifactKind.Link)
        else ArtifactKind.byExt(ext)
    }

    private fun artifactLabel(value: String): String {
        if (Regex("^(?:https?|file|data):").matches(value)) {
            val item = runCatching {
                java.net.URI(value).path?.split('/')?.filter { it.isNotEmpty() }?.lastOrNull()
            }.getOrNull()
            if (!item.isNullOrEmpty()) return item
        }
        val parts = value.split('/', '\\').filter { it.isNotEmpty() }
        return parts.lastOrNull() ?: value
    }

    private fun normalizeTs(timestamp: Long?): Long? {
        if (timestamp == null || timestamp <= 0) return null
        return if (timestamp < MAX_UNIX_SECONDS) timestamp * 1000 else timestamp
    }

    private fun artifactTimestamp(row: ArtifactRow, session: ArtifactSession): Long =
        normalizeTs(row.timestamp) ?: normalizeTs(session.lastActive) ?:
            normalizeTs(session.startedAt) ?: System.currentTimeMillis()
}

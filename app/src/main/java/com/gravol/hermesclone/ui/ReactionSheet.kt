package com.gravol.hermesclone.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Official "Add Reaction" sheet (verified on official vc49 2026-11-26, shot
 * official49-react-picker.png): Cancel (top-left) + centered title "Add Reaction",
 * full-width rounded search field with placeholder "Search emoji", uppercase gray
 * section headers ("FREQUENTLY USED", "SMILEYS & PEOPLE", …), a 9-column emoji grid,
 * and a bottom category icon bar. The emoji catalog is OUR OWN curated set (the
 * official ships its own full Unicode list; only the sheet chrome copy is matched).
 */

/**
 * Emoji catalog: the official ships 8 category JSON files in assets/emoji/ (1,870 emojis,
 * MECHANICS.md 2.7 — searchKey/string/version/emojiKeys/isSkinToneSupport) and matches
 * search against `searchKey`. The clone now bundles those same files and loads them from
 * assets instead of a hand-written list. Category titles/icons are OURS except
 * "SMILEYS & PEOPLE", which is screenshot-verified (official49-react-picker.png).
 */
private class EmojiCat(val title: String, val icon: String, val emojis: List<Pair<String, String>>)

// (asset file, header, bottom-bar icon) in the official file set's natural order
private val emojiCatSpecs = listOf(
    Triple("emotionsAndPeople", "SMILEYS & PEOPLE", "\ud83d\ude42"),
    Triple("animalsAndNature", "ANIMALS & NATURE", "\ud83d\udc0d"),
    Triple("foodAndDrinks", "FOOD & DRINK", "\ud83c\udf54"),
    Triple("travellingAndPlaces", "TRAVEL & PLACES", "\ud83d\ude97"),
    Triple("activities", "ACTIVITIES", "\ud83c\udfaf"),
    Triple("items", "OBJECTS", "\ud83d\udca1"),
    Triple("symbols", "SYMBOLS", "\u2764\ufe0f"),
    Triple("flags", "FLAGS", "\ud83c\udfc1"),
)

/** One-time load of the bundled catalog (assets/emoji/<file>.json). */
@Composable
private fun loadEmojiCats(): List<EmojiCat> {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    return remember(ctx) {
        emojiCatSpecs.mapNotNull { (file, title, icon) ->
            val items = runCatching {
                val root = org.json.JSONObject(
                    ctx.assets.open("emoji/$file.json").bufferedReader().use { it.readText() })
                val arr = root.getJSONArray("emojis")
                List(arr.length()) { i ->
                    val e = arr.getJSONObject(i)
                    e.getString("string") to e.getString("searchKey")
                }
            }.getOrDefault(emptyList())
            if (items.isEmpty()) null else EmojiCat(title, icon, items)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReactionSheet(
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    frequent: List<String>,
) {
    var query by remember { mutableStateOf("") }
    var catIndex by remember { mutableStateOf(0) }
    val q = query.trim().lowercase()
    val emojiCats = loadEmojiCats()
    val sections = buildList {
        if (q.isEmpty() && frequent.isNotEmpty())
            add("FREQUENTLY USED" to frequent.map { it to "" })
        for ((ci, cat) in emojiCats.withIndex()) {
            val items = if (q.isEmpty()) cat.emojis
                        else cat.emojis.filter { it.second.lowercase().contains(q) || it.first.contains(q) }
            if (items.isNotEmpty() && (q.isNotEmpty() || ci == catIndex))
                add(cat.title to items)
        }
    }
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(color = Color(0xFF101A33), shape = RoundedCornerShape(20.dp), tonalElevation = 0.dp) {
            Column(Modifier.width(340.dp)) {
                // Cancel (left) + centered title — official chrome copy
                Box(Modifier.fillMaxWidth().padding(top = 14.dp, start = 10.dp, end = 10.dp)) {
                    Text("Cancel", color = HmLiveBlue, fontSize = 15.sp,
                        modifier = Modifier.align(Alignment.CenterStart)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onDismiss() }
                            .padding(horizontal = 8.dp, vertical = 6.dp))
                    Text("Add Reaction", color = HmTextPrimary, fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Center))
                }
                // search field — official placeholder "Search emoji"
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF0C1A3C))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Search, null, tint = HmTextMuted, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        if (q.isEmpty()) Text("Search emoji", color = HmTextMuted, fontSize = 14.sp)
                        androidx.compose.foundation.text.BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(color = HmTextPrimary, fontSize = 14.sp),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(HmLiveBlue),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(9),
                    modifier = Modifier.fillMaxWidth().height(360.dp).padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    sections.forEach { (title, items) ->
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(9) }) {
                            Text(title, color = HmTextMuted, fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified,
                                modifier = Modifier.padding(start = 6.dp, top = 10.dp, bottom = 4.dp))
                        }
                        items(items, key = { title + it.first }) { (emoji, _) ->
                            Box(
                                Modifier.size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onPick(emoji) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(emoji, fontSize = 20.sp)
                            }
                        }
                    }
                }
                // category icon bar (bottom)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    emojiCats.forEachIndexed { i, cat ->
                        Box(
                            Modifier.size(34.dp).clip(RoundedCornerShape(8.dp))
                                .background(if (i == catIndex && q.isEmpty()) HmRowHighlight else Color.Transparent)
                                .clickable { catIndex = i; query = "" },
                            contentAlignment = Alignment.Center,
                        ) { Text(cat.icon, fontSize = 17.sp) }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

package com.sanwolf.daw.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.sanwolf.daw.audio.InstrumentDefinition
import com.sanwolf.daw.audio.InstrumentLibrary
import com.sanwolf.daw.audio.MultisampleLibrary
import com.sanwolf.daw.audio.MultisamplePickerGroup
import com.sanwolf.daw.audio.SanwolfAudioEngine
import com.sanwolf.daw.model.InstrumentCatalog
import com.sanwolf.daw.model.InstrumentCategory
import com.sanwolf.daw.model.InstrumentPreset
import com.sanwolf.daw.ui.theme.SanwolfBlack
import com.sanwolf.daw.ui.theme.SanwolfCyan
import com.sanwolf.daw.ui.theme.SanwolfGold
import com.sanwolf.daw.ui.theme.SanwolfLime
import com.sanwolf.daw.ui.theme.SanwolfMagenta
import com.sanwolf.daw.ui.theme.SanwolfOrange
import com.sanwolf.daw.ui.theme.SanwolfPanel
import com.sanwolf.daw.ui.theme.SanwolfPanelBorder
import com.sanwolf.daw.ui.theme.SanwolfPanelElevated
import com.sanwolf.daw.ui.theme.SanwolfPurple
import com.sanwolf.daw.ui.theme.SanwolfTextMuted
import com.sanwolf.daw.ui.theme.SanwolfTextPrimary
import com.sanwolf.daw.ui.theme.SanwolfTextSecondary

/** Category tabs of the simple Add-track picker (FL Studio Mobile style). */
enum class AddTrackTab(val label: String, val icon: ImageVector, val color: Color) {
    DRUMS("Drums", Icons.Default.GridView, SanwolfOrange),
    KEYS("Keys", Icons.Default.Piano, SanwolfGold),
    SYNTHS("Synths", Icons.Default.GraphicEq, SanwolfCyan),
    BASS("Bass", Icons.Default.Speaker, SanwolfMagenta),
    STRINGS("Strings & Plucked", Icons.Default.MusicNote, SanwolfLime),
    MALLETS("Perc & Mallets", Icons.Default.Album, SanwolfPurple),
    WORLD("World", Icons.Default.Public, SanwolfOrange),
    AUDIO("Audio", Icons.Default.Mic, SanwolfMagenta)
}

/** One instrument tile: the preset that creates the track, and the tab it sits under. */
class AddTrackEntry(val preset: InstrumentPreset, val tab: AddTrackTab)

/** Tab opened last (process lifetime), so adding several drums in a row stays on Drums. */
private var lastAddTrackTab: AddTrackTab = AddTrackTab.DRUMS

private val MALLET_WORDS = listOf("marimba", "vibra", "xylo", "glock", "bell", "celest", "mallet", "steel", "chime", "music box")
private val PLUCKED_WORDS = listOf("guitar", "harp", "koto", "sitar", "banjo", "mandolin", "ukulele", "pizz", "kora", "oud", "kalimba")

private fun String.hasAny(words: List<String>): Boolean {
    val l = lowercase()
    return words.any { l.contains(it) }
}

private fun tabForCatalogPreset(p: InstrumentPreset): AddTrackTab = when (p.category) {
    InstrumentCategory.DRUM_KIT -> if (p.name.hasAny(MALLET_WORDS)) AddTrackTab.MALLETS else AddTrackTab.DRUMS
    InstrumentCategory.WORLD_PERCUSSION -> if (p.name.hasAny(MALLET_WORDS)) AddTrackTab.MALLETS else AddTrackTab.WORLD
    InstrumentCategory.BASS_808 -> AddTrackTab.BASS
    InstrumentCategory.KEYS_PAD -> if (p.name.contains("pad", ignoreCase = true)) AddTrackTab.SYNTHS else AddTrackTab.KEYS
    InstrumentCategory.ORCHESTRAL_STRINGS, InstrumentCategory.BRASS_WINDS -> AddTrackTab.STRINGS
    InstrumentCategory.SYNTH_LEAD -> when {
        p.name.hasAny(MALLET_WORDS) -> AddTrackTab.MALLETS
        p.name.hasAny(PLUCKED_WORDS) -> AddTrackTab.STRINGS
        else -> AddTrackTab.SYNTHS
    }
    InstrumentCategory.ATMOSPHERE_FX -> AddTrackTab.SYNTHS
}

private fun tabForDefinition(def: InstrumentDefinition): AddTrackTab {
    return when (def.category) {
        com.sanwolf.daw.audio.InstrumentCategory.DRUMS,
        com.sanwolf.daw.audio.InstrumentCategory.DRUMS_PERCUSSION ->
            if (def.name.hasAny(MALLET_WORDS)) AddTrackTab.MALLETS else AddTrackTab.DRUMS
        com.sanwolf.daw.audio.InstrumentCategory.BASS -> AddTrackTab.BASS
        com.sanwolf.daw.audio.InstrumentCategory.KEYS_PIANO,
        com.sanwolf.daw.audio.InstrumentCategory.KEYBOARDS,
        com.sanwolf.daw.audio.InstrumentCategory.PIANO_KEYS -> AddTrackTab.KEYS
        com.sanwolf.daw.audio.InstrumentCategory.STRINGS,
        com.sanwolf.daw.audio.InstrumentCategory.ORCHESTRAL_STRINGS,
        com.sanwolf.daw.audio.InstrumentCategory.BRASS_WINDS -> AddTrackTab.STRINGS
        com.sanwolf.daw.audio.InstrumentCategory.PLUCKS_MALLETS ->
            if (def.name.hasAny(PLUCKED_WORDS)) AddTrackTab.STRINGS else AddTrackTab.MALLETS
        com.sanwolf.daw.audio.InstrumentCategory.WORLD_PERCUSSION ->
            if (def.name.hasAny(MALLET_WORDS)) AddTrackTab.MALLETS else AddTrackTab.WORLD
        else -> AddTrackTab.SYNTHS
    }
}

/**
 * Every instrument the full browser offers (VCSL multisamples, catalogue presets, library
 * definitions), de-duplicated by id and name the same way, each placed under one simple tab.
 */
fun buildAddTrackEntries(): List<AddTrackEntry> {
    val out = ArrayList<AddTrackEntry>(256)
    val seenIds = HashSet<String>()
    val seenNames = HashSet<String>()
    fun add(p: InstrumentPreset, tab: AddTrackTab) {
        val nameKey = p.name.lowercase()
        if (p.id in seenIds || nameKey in seenNames) return
        seenIds.add(p.id)
        seenNames.add(nameKey)
        out.add(AddTrackEntry(p, tab))
    }
    // Sampled VCSL instruments first in their tabs
    runCatching {
        MultisampleLibrary.specs.forEach { spec ->
            val tab = when (spec.group) {
                MultisamplePickerGroup.KEYS -> AddTrackTab.KEYS
                MultisamplePickerGroup.MALLETS -> AddTrackTab.MALLETS
                MultisamplePickerGroup.PLUCKED -> AddTrackTab.STRINGS
                MultisamplePickerGroup.WORLD -> AddTrackTab.WORLD
            }
            add(spec.toInstrumentPreset(), tab)
        }
    }
    runCatching {
        InstrumentCatalog.presets
            .filter { !MultisampleLibrary.isMultisample(it.name) }
            .forEach { add(it, tabForCatalogPreset(it)) }
    }
    runCatching {
        InstrumentLibrary.definitions.forEach { def ->
            runCatching { add(def.toInstrumentPreset(), tabForDefinition(def)) }
        }
    }
    return out
}

/**
 * Simple Add-track picker: category tabs over a grid of big tiles. One tap adds the track (the
 * caller names it, selects it and opens its editor); the small ▶ (or a long press) previews.
 * The Audio tab records the mic, imports a file or opens the factory sounds.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AddTrackPicker(
    audioEngine: SanwolfAudioEngine?,
    onAddInstrument: (InstrumentPreset) -> Unit,
    onRecordMic: () -> Unit,
    onImportAudio: () -> Unit,
    onBrowseSounds: () -> Unit,
    onDismiss: () -> Unit
) {
    val entries = remember { buildAddTrackEntries() }
    var tab by remember { mutableStateOf(lastAddTrackTab) }
    var query by remember { mutableStateOf("") }

    val visible = remember(tab, query, entries) {
        val q = query.trim()
        if (q.isNotEmpty()) {
            entries.filter { it.preset.name.contains(q, ignoreCase = true) || it.tab.label.contains(q, ignoreCase = true) }
        } else {
            entries.filter { it.tab == tab }
        }
    }

    ScaledDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.98f)
                .fillMaxHeight(0.96f)
                .clip(RoundedCornerShape(14.dp))
                .background(SanwolfBlack)
                .border(1.dp, SanwolfGold.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .testTag("add_track_picker")
        ) {
            // Header: title, search, close
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("ADD TRACK", color = SanwolfGold, fontSize = 15.sp, fontWeight = FontWeight.Black)
                Spacer(modifier = Modifier.width(10.dp))
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SanwolfPanel)
                        .border(1.dp, SanwolfPanelBorder, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = SanwolfTextMuted, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) Text("Search instruments", color = SanwolfTextMuted, fontSize = 12.sp, maxLines = 1)
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it.take(40) },
                            singleLine = true,
                            textStyle = TextStyle(color = SanwolfTextPrimary, fontSize = 13.sp),
                            cursorBrush = SolidColor(SanwolfGold),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (query.isNotEmpty()) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Clear search",
                            tint = SanwolfTextSecondary,
                            modifier = Modifier.size(18.dp).clickable { query = "" }
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(SanwolfPanelElevated)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = SanwolfTextPrimary, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Category tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AddTrackTab.values().forEach { t ->
                    val selected = query.isBlank() && t == tab
                    Row(
                        modifier = Modifier
                            .heightIn(min = 34.dp)
                            .clip(RoundedCornerShape(17.dp))
                            .background(if (selected) t.color else SanwolfPanel)
                            .border(1.dp, if (selected) t.color else SanwolfPanelBorder, RoundedCornerShape(17.dp))
                            .clickable {
                                tab = t
                                lastAddTrackTab = t
                                query = ""
                            }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(t.icon, contentDescription = null, tint = if (selected) Color.Black else t.color, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            t.label,
                            color = if (selected) Color.Black else SanwolfTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (query.isBlank() && tab == AddTrackTab.AUDIO) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        ActionTile("Record mic", "New audio track from your microphone", Icons.Default.Mic, SanwolfMagenta) {
                            onRecordMic()
                        }
                    }
                    item {
                        ActionTile("Import audio", "WAV, MP3, … from your phone", Icons.Default.FolderOpen, SanwolfCyan) {
                            onImportAudio()
                        }
                    }
                    item {
                        ActionTile("Factory sounds", "One-shots and loops", Icons.Default.LibraryMusic, SanwolfGold) {
                            onBrowseSounds()
                        }
                    }
                }
            } else if (visible.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        if (query.isNotBlank()) "Nothing matches \"${query.trim()}\"" else "No instruments here yet",
                        color = SanwolfTextSecondary,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 112.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(visible, key = { it.preset.id + "|" + it.preset.name }) { entry ->
                        InstrumentTile(
                            entry = entry,
                            onAdd = { onAddInstrument(entry.preset) },
                            onPreview = { runCatching { playPresetPreview(audioEngine, entry.preset) } }
                        )
                    }
                }
            }

            Text(
                "Tap a tile to add the track · ▶ or hold to preview",
                color = SanwolfTextMuted,
                fontSize = 10.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InstrumentTile(entry: AddTrackEntry, onAdd: () -> Unit, onPreview: () -> Unit) {
    val accent = Color(entry.preset.colorHex)
    val sampled = entry.preset.id.startsWith(MultisampleLibrary.PRESET_ID_PREFIX)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(SanwolfPanel)
            .border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onAdd, onLongClick = onPreview)
            .padding(8.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(entry.tab.icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                entry.preset.name,
                color = SanwolfTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 14.sp
            )
            if (sampled) {
                Text("Sampled", color = SanwolfGold, fontSize = 9.sp, maxLines = 1)
            }
        }
        // Small preview button (top right)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(30.dp)
                .clip(CircleShape)
                .background(SanwolfPanelElevated)
                .clickable { onPreview() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Preview ${entry.preset.name}", tint = accent, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ActionTile(title: String, subtitle: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(SanwolfPanel)
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text(title, color = SanwolfTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(subtitle, color = SanwolfTextSecondary, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

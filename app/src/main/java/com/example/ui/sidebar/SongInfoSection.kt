package com.example.ui.sidebar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ProjectData
import com.example.ui.SanwolfTextField
import com.example.ui.theme.SanwolfGold
import com.example.ui.theme.SanwolfPanel
import com.example.ui.theme.SanwolfPanelBorder
import com.example.ui.theme.SanwolfPanelElevated
import com.example.ui.theme.SanwolfTextMuted
import com.example.ui.theme.SanwolfTextPrimary
import com.example.ui.theme.SanwolfTextSecondary
import kotlinx.coroutines.delay

private val NOTE_NAMES = listOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")
val MUSICAL_KEYS: List<String> = NOTE_NAMES.map { "$it major" } + NOTE_NAMES.map { "$it minor" }
private val TIME_SIGNATURES = listOf(4 to 4, 3 to 4, 2 to 4, 5 to 4, 6 to 8, 7 to 8, 12 to 8)
private val GENRE_SUGGESTIONS = listOf(
    "Afro House", "Amapiano", "Gqom", "Deep House", "Afrobeats", "Hip Hop", "Trap",
    "R&B", "Gospel", "Maskandi", "Kwaito", "Techno", "Pop"
)

/**
 * SONG INFO section (BandLab-style song details): title, artist, tempo, key, time
 * signature, genre and notes. Edits are written straight into [project]; [onChanged] is
 * called (debounced) so the app can mark the project dirty and refresh dependent UI.
 */
@Composable
fun SongInfoSection(
    project: ProjectData,
    loadKey: Int,
    onBpmChange: (Int) -> Unit,
    onChanged: () -> Unit
) {
    // Local mirrors, re-seeded whenever a different project is loaded / created.
    var title by remember(loadKey) { mutableStateOf(project.title) }
    var artist by remember(loadKey) { mutableStateOf(project.artist) }
    var bpm by remember(loadKey) { mutableIntStateOf(project.bpm) }
    var bpmText by remember(loadKey) { mutableStateOf(project.bpm.toString()) }
    var key by remember(loadKey) { mutableStateOf(project.musicalKey) }
    var tsNum by remember(loadKey) { mutableIntStateOf(project.timeSignatureNumerator) }
    var tsDen by remember(loadKey) { mutableIntStateOf(project.timeSignatureDenominator) }
    var genre by remember(loadKey) { mutableStateOf(project.genre) }
    var notes by remember(loadKey) { mutableStateOf(project.songNotes) }
    var editCount by remember(loadKey) { mutableIntStateOf(0) }

    // Pick up BPM changes made elsewhere (tap tempo in the piano bar, AI, etc.).
    if (project.bpm != bpm) {
        bpm = project.bpm
        bpmText = project.bpm.toString()
    }

    LaunchedEffect(editCount) {
        if (editCount > 0) {
            delay(500)
            onChanged()
        }
    }

    fun applyBpm(newBpm: Int) {
        val clamped = newBpm.coerceIn(40, 300)
        bpm = clamped
        bpmText = clamped.toString()
        onBpmChange(clamped)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SidebarSectionHeader("SONG DETAILS")
        SanwolfTextField(
            value = title,
            onValueChange = {
                title = it.take(60)
                if (title.isNotBlank()) project.title = title
                editCount++
            },
            singleLine = true,
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth()
        )
        SanwolfTextField(
            value = artist,
            onValueChange = {
                artist = it.take(60)
                project.artist = artist
                editCount++
            },
            singleLine = true,
            label = { Text("Artist") },
            placeholder = { Text("e.g. SANWOLF") },
            modifier = Modifier.fillMaxWidth()
        )

        SidebarSectionHeader("TEMPO & TIME")
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { applyBpm(bpm - 1) },
                modifier = Modifier.size(48.dp).semantics { contentDescription = "Decrease tempo" }
            ) { Icon(Icons.Default.Remove, contentDescription = null, tint = SanwolfTextPrimary) }
            SanwolfTextField(
                value = bpmText,
                onValueChange = { raw ->
                    bpmText = raw.filter { it.isDigit() }.take(3)
                    val typed = bpmText.toIntOrNull()
                    if (typed != null && typed in 40..300) {
                        bpm = typed
                        onBpmChange(typed)
                    }
                },
                singleLine = true,
                label = { Text("BPM") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { applyBpm(bpm + 1) },
                modifier = Modifier.size(48.dp).semantics { contentDescription = "Increase tempo" }
            ) { Icon(Icons.Default.Add, contentDescription = null, tint = SanwolfTextPrimary) }
            Spacer(modifier = Modifier.width(4.dp))
            TapTempoButton(onBpm = { applyBpm(it) })
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SidebarDropdownField(
                label = "Key",
                value = key.ifBlank { "Not set" },
                options = listOf("Not set") + MUSICAL_KEYS,
                onSelect = { choice ->
                    key = if (choice == "Not set") "" else choice
                    project.musicalKey = key
                    editCount++
                },
                modifier = Modifier.weight(1f)
            )
            SidebarDropdownField(
                label = "Time sig.",
                value = "$tsNum/$tsDen",
                options = TIME_SIGNATURES.map { "${it.first}/${it.second}" },
                onSelect = { choice ->
                    val parts = choice.split("/")
                    val n = parts.getOrNull(0)?.toIntOrNull() ?: 4
                    val d = parts.getOrNull(1)?.toIntOrNull() ?: 4
                    tsNum = n
                    tsDen = d
                    project.timeSignatureNumerator = n
                    project.timeSignatureDenominator = d
                    editCount++
                },
                modifier = Modifier.weight(1f)
            )
        }

        SidebarSectionHeader("STYLE & NOTES")
        Row(verticalAlignment = Alignment.CenterVertically) {
            SanwolfTextField(
                value = genre,
                onValueChange = {
                    genre = it.take(40)
                    project.genre = genre
                    editCount++
                },
                singleLine = true,
                label = { Text("Genre") },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(4.dp))
            GenreSuggestionButton(onPick = {
                genre = it
                project.genre = it
                editCount++
            })
        }
        SanwolfTextField(
            value = notes,
            onValueChange = {
                notes = it.take(2000)
                project.songNotes = notes
                editCount++
            },
            label = { Text("Notes") },
            placeholder = { Text("Lyrics, ideas, mix to-dos…") },
            maxLines = 6,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp)
        )
        Text(
            text = "Song info is saved with the project.",
            fontSize = 12.sp,
            color = SanwolfTextMuted
        )
    }
}

/** Read-only field that opens a dropdown of [options]. */
@Composable
fun SidebarDropdownField(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, SanwolfPanelBorder, RoundedCornerShape(6.dp))
                .clickable(role = Role.Button, onClickLabel = "Choose $label") { open = true }
                .semantics { contentDescription = "$label: $value" }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(label, fontSize = 12.sp, color = SanwolfTextSecondary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    value,
                    fontSize = 14.sp,
                    color = SanwolfTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = SanwolfTextSecondary)
            }
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.background(SanwolfPanel)
        ) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt, color = if (opt == value) SanwolfGold else SanwolfTextPrimary) },
                    onClick = {
                        open = false
                        onSelect(opt)
                    }
                )
            }
        }
    }
}

@Composable
private fun GenreSuggestionButton(onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { open = true },
            modifier = Modifier.size(48.dp).semantics { contentDescription = "Pick a genre" }
        ) { Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = SanwolfGold) }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.background(SanwolfPanel)
        ) {
            GENRE_SUGGESTIONS.forEach { g ->
                DropdownMenuItem(
                    text = { Text(g, color = SanwolfTextPrimary) },
                    onClick = {
                        open = false
                        onPick(g)
                    }
                )
            }
        }
    }
}

/** Tap repeatedly in time to set the tempo (moved here from the old sidebar). */
@Composable
fun TapTempoButton(onBpm: (Int) -> Unit) {
    val tapTimes = remember { mutableStateListOf<Long>() }
    Box(
        modifier = Modifier
            .size(width = 56.dp, height = 48.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(SanwolfPanelElevated)
            .clickable(role = Role.Button) {
                val now = System.currentTimeMillis()
                if (tapTimes.isNotEmpty() && now - tapTimes.last() > 2000) tapTimes.clear()
                tapTimes.add(now)
                if (tapTimes.size > 5) tapTimes.removeAt(0)
                if (tapTimes.size >= 2) {
                    val intervals = (1 until tapTimes.size).map { tapTimes[it] - tapTimes[it - 1] }
                    val avg = intervals.average()
                    if (avg > 0) onBpm((60000 / avg).toInt().coerceIn(40, 300))
                }
            }
            .semantics { contentDescription = "Tap tempo: tap repeatedly in time to set the BPM" },
        contentAlignment = Alignment.Center
    ) {
        Text("TAP", color = SanwolfGold, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
    }
}

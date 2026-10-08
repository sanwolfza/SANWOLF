package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.audio.FactoryGenre
import com.example.audio.FactoryLibrary
import com.example.audio.FactorySound
import com.example.audio.SanwolfAudioEngine
import com.example.model.ProjectData
import com.example.model.TrackData
import com.example.model.TrackType
import com.example.ui.theme.SanwolfBlack
import com.example.ui.theme.SanwolfCyan
import com.example.ui.theme.SanwolfGold
import com.example.ui.theme.SanwolfLime
import com.example.ui.theme.SanwolfPanel
import com.example.ui.theme.SanwolfPanelBorder
import com.example.ui.theme.SanwolfPanelElevated
import com.example.ui.theme.SanwolfTextMuted
import com.example.ui.theme.SanwolfTextPrimary
import com.example.ui.theme.SanwolfTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.util.UUID

data class FactoryWavSample(
    val id: String,
    val name: String,
    val category: String, // "Drums", "Instruments", "Vocals & FX"
    val genre: String,
    val durationSec: Double,
    val description: String,
    val colorHex: Long
)

object FactorySampleCatalog {
    val samples = listOf(
        FactoryWavSample("kick_808", "808 Heavy Kick.wav", "Drums", "Trap / Hip Hop", 0.45, "Deep thunderous sub kick with high-end knock", 0xFFFF1744),
        FactoryWavSample("snare_trap", "Trap Snap Snare.wav", "Drums", "Trap / R&B", 0.35, "Crisp razor-sharp snare with long tail", 0xFFFF2A6D),
        FactoryWavSample("amapiano_log", "Amapiano Log Kick.wav", "Drums", "Amapiano / Afro", 0.50, "Woody resonant Amapiano log drum bass thump", 0xFFFFD700),
        FactoryWavSample("hihat_closed", "Tight Trap HiHat.wav", "Drums", "Trap / Pop", 0.08, "Short metallic closed hihat for fast rolls", 0xFF76FF03),
        FactoryWavSample("shaker_amapiano", "Amapiano Shaker Loop.wav", "Drums", "Amapiano / Afro", 0.30, "Swung acoustic shaker loop", 0xFF00E676),
        FactoryWavSample("gqom_clap", "Gqom Heavy Clap.wav", "Drums", "Gqom / Tribal", 0.28, "Raw industrial Durban clap hit", 0xFFD500F9),
        FactoryWavSample("rhodes_chord", "Vintage Rhodes Chime.wav", "Instruments", "R&B / Soul / Jazz", 1.20, "Warm electric piano chord hit", 0xFFFFC107),
        FactoryWavSample("maskandi_guitar", "Maskandi Acoustic.wav", "Instruments", "Maskandi / African", 0.80, "Traditional Zulu fingerstyle guitar pluck", 0xFFFF9100),
        FactoryWavSample("gospel_organ", "Hammond Gospel Chord.wav", "Instruments", "Gospel / Jazz", 1.50, "Full Leslie speaker gospel organ swell", 0xFF00B0FF),
        FactoryWavSample("synth_lead_kpop", "K-Pop SuperSaw Lead.wav", "Instruments", "K-Pop / Pop", 0.60, "Bright catchy synth lead hook", 0xFFFF007F),
        FactoryWavSample("vocal_chop_1", "Vocal Chant Hey!.wav", "Vocals & FX", "Hip Hop / Afro Pop", 0.25, "Pitch-shifted hype vocal chant", 0xFF00E5FF),
        FactoryWavSample("riser_whitenoise", "White Noise Riser.wav", "Vocals & FX", "EDM / Pop / Trap", 2.00, "Tension builder for drop transitions", 0xFFE040FB)
    )
}

@Composable
fun FactorySampleManagerDialog(
    audioEngine: SanwolfAudioEngine?,
    onLoadSampleAsTrack: (FactoryWavSample) -> Unit,
    onLoadSound: (FactorySound) -> Unit,
    onLoadSoundToSteps: (FactorySound) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sounds by produceState<List<FactorySound>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { FactoryLibrary.sounds(context.applicationContext) }
    }
    DisposableEffect(Unit) { onDispose { FactoryLibrary.stopPreview() } }
    val loaded = sounds
    if (loaded == null) return
    if (loaded.isNotEmpty()) {
        FactoryWavLibraryDialog(
            sounds = loaded,
            audioEngine = audioEngine,
            onLoadSound = onLoadSound,
            onLoadSoundToSteps = onLoadSoundToSteps,
            onDismiss = onDismiss
        )
    } else {
        // Fallback: bundled WAVs unreadable, offer the old synth placeholders.
        PlaceholderSampleDialog(audioEngine, onLoadSampleAsTrack, onDismiss)
    }
}

/** Genre filter value meaning "every genre". */
private const val ALL_GENRES_KEY = "all"

@Composable
private fun FactoryWavLibraryDialog(
    sounds: List<FactorySound>,
    audioEngine: SanwolfAudioEngine?,
    onLoadSound: (FactorySound) -> Unit,
    onLoadSoundToSteps: (FactorySound) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var genreKey by remember { mutableStateOf(FactoryGenre.AFRO_HOUSE.key) }
    val previewingId = FactoryLibrary.previewingId.value
    val genreOrder = remember { FactoryGenre.values().map { it.key } }

    val q = searchQuery.trim()
    val crossGenre = q.isNotEmpty() || genreKey == ALL_GENRES_KEY
    val grouped: List<Pair<String, List<FactorySound>>> = remember(sounds, q, genreKey) {
        sounds.filter { s ->
            if (q.isNotEmpty()) s.displayName.contains(q, ignoreCase = true)
            else genreKey == ALL_GENRES_KEY || s.genre == genreKey
        }.sortedWith(
            compareBy<FactorySound>(
                { FactoryLibrary.categoryRank(it.category) },
                { genreOrder.indexOf(it.genre).let { i -> if (i < 0) 99 else i } },
                { it.displayName }
            )
        ).groupBy { it.category }.toList()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
                .testTag("factory_sample_dialog"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SanwolfBlack),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, SanwolfGold.copy(alpha = 0.8f))
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SanwolfGold.copy(alpha = 0.2f))
                                .border(1.dp, SanwolfGold, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = SanwolfGold, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "FACTORY SAMPLES",
                                color = SanwolfGold,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = "${sounds.size} one-shots and loops • House first, every genre",
                                color = SanwolfTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = SanwolfTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search sounds (kick, log drum, rhodes…)", color = SanwolfTextMuted, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SanwolfTextSecondary, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SanwolfPanel,
                        unfocusedContainerColor = SanwolfPanel,
                        focusedBorderColor = SanwolfGold,
                        unfocusedBorderColor = SanwolfPanelBorder,
                        focusedTextColor = SanwolfTextPrimary,
                        unfocusedTextColor = SanwolfTextPrimary
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val chips = FactoryGenre.values().map { it.key to it.label } + (ALL_GENRES_KEY to "All genres")
                    chips.forEach { (key, label) ->
                        val isSelected = genreKey == key && q.isEmpty()
                        Box(
                            modifier = Modifier
                                .height(40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) SanwolfGold.copy(alpha = 0.25f) else SanwolfPanel)
                                .border(1.dp, if (isSelected) SanwolfGold else SanwolfPanelBorder, RoundedCornerShape(20.dp))
                                .clickable { genreKey = key; searchQuery = "" }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) SanwolfGold else SanwolfTextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (grouped.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("No sounds match '$searchQuery'", color = SanwolfTextMuted, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        grouped.forEach { (category, list) ->
                            item(key = "header_$category") {
                                Text(
                                    text = category.uppercase(),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp,
                                    color = SanwolfTextMuted,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                                )
                            }
                            items(list, key = { it.id }) { sound ->
                                FactorySoundCard(
                                    sound = sound,
                                    showGenre = crossGenre,
                                    isPreviewing = previewingId == sound.id,
                                    onPreview = {
                                        if (previewingId == sound.id) {
                                            FactoryLibrary.stopPreview()
                                        } else if (!FactoryLibrary.preview(context, sound)) {
                                            audioEngine?.triggerDrumSound(FactoryLibrary.placeholderSynthName(sound), 0.9f)
                                        }
                                    },
                                    onLoad = {
                                        FactoryLibrary.stopPreview()
                                        onLoadSound(sound)
                                        onDismiss()
                                    },
                                    onLoadToSteps = if (sound.isDrumOneShot) {
                                        {
                                            FactoryLibrary.stopPreview()
                                            onLoadSoundToSteps(sound)
                                            onDismiss()
                                        }
                                    } else null
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FactorySoundCard(
    sound: FactorySound,
    showGenre: Boolean,
    isPreviewing: Boolean,
    onPreview: () -> Unit,
    onLoad: () -> Unit,
    onLoadToSteps: (() -> Unit)?
) {
    val accent = Color(sound.color)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isPreviewing) accent.copy(alpha = 0.14f) else SanwolfPanel)
            .border(1.dp, if (isPreviewing) accent else SanwolfPanelBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPreview, modifier = Modifier.size(48.dp)) {
            Box(
                modifier = Modifier.size(34.dp).clip(CircleShape).background(accent.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPreviewing) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isPreviewing) "Stop preview of ${sound.displayName}" else "Preview ${sound.displayName}",
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(sound.displayName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SanwolfTextPrimary)
            Text(sound.detail(includeGenre = showGenre), fontSize = 12.sp, color = SanwolfTextSecondary)
        }
        if (onLoadToSteps != null) {
            IconButton(onClick = onLoadToSteps, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.GridOn, contentDescription = "Add ${sound.displayName} as a step-sequencer drum track", tint = SanwolfCyan)
            }
        }
        IconButton(onClick = onLoad, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Add, contentDescription = "Add ${sound.displayName} as a new audio track", tint = SanwolfLime)
        }
    }
}

/** The original synth-placeholder browser, only shown if the bundled WAV library can't be read. */
@Composable
private fun PlaceholderSampleDialog(
    audioEngine: SanwolfAudioEngine?,
    onLoadSampleAsTrack: (FactoryWavSample) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var previewingSampleId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val categories = listOf("All", "Drums", "Instruments", "Vocals & FX")
    val allSamples = remember { FactorySampleCatalog.samples }

    val filteredSamples = remember(searchQuery, selectedCategory) {
        allSamples.filter { sample ->
            val matchesCategory = selectedCategory == null || selectedCategory == "All" || sample.category.equals(selectedCategory, ignoreCase = true)
            val matchesQuery = searchQuery.isBlank() ||
                    sample.name.contains(searchQuery, ignoreCase = true) ||
                    sample.genre.contains(searchQuery, ignoreCase = true) ||
                    sample.description.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
                .testTag("factory_sample_dialog"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SanwolfBlack),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, SanwolfGold.copy(alpha = 0.8f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SanwolfGold.copy(alpha = 0.2f))
                                .border(1.dp, SanwolfGold, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = SanwolfGold,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "FACTORY .WAV SAMPLE LIBRARY",
                                color = SanwolfGold,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = "Load professional .wav loops & one-shots across all genres into your tracks",
                                color = SanwolfTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = SanwolfTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text("Search amapiano, trap, maskandi, rhodes, kicks...", color = SanwolfTextMuted, fontSize = 12.sp)
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = SanwolfTextSecondary, modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SanwolfPanel,
                        unfocusedContainerColor = SanwolfPanel,
                        focusedBorderColor = SanwolfGold,
                        unfocusedBorderColor = SanwolfPanelBorder,
                        focusedTextColor = SanwolfTextPrimary,
                        unfocusedTextColor = SanwolfTextPrimary
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Category Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { cat ->
                        val isSelected = (selectedCategory == cat) || (selectedCategory == null && cat == "All")
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) SanwolfGold.copy(alpha = 0.25f) else SanwolfPanel)
                                .border(1.dp, if (isSelected) SanwolfGold else SanwolfPanelBorder, RoundedCornerShape(6.dp))
                                .clickable { selectedCategory = if (cat == "All") null else cat }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = cat.uppercase(),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) SanwolfGold else SanwolfTextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Sample List
                if (filteredSamples.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("No samples found matching '$searchQuery'", color = SanwolfTextMuted, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredSamples, key = { it.id }) { sample ->
                            val isPreviewing = previewingSampleId == sample.id

                            SampleCard(
                                sample = sample,
                                isPreviewing = isPreviewing,
                                onPreview = {
                                    previewingSampleId = sample.id
                                    scope.launch {
                                        audioEngine?.triggerDrumSound(sample.name, 0.9f)
                                        delay(600)
                                        if (previewingSampleId == sample.id) previewingSampleId = null
                                    }
                                },
                                onLoad = {
                                    onLoadSampleAsTrack(sample)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SampleCard(
    sample: FactoryWavSample,
    isPreviewing: Boolean,
    onPreview: () -> Unit,
    onLoad: () -> Unit
) {
    val accentColor = Color(sample.colorHex)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = SanwolfPanel),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isPreviewing) SanwolfCyan else SanwolfPanelBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = sample.name,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = SanwolfTextPrimary
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(accentColor.copy(alpha = 0.2f))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = sample.category,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = accentColor
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${sample.genre} • ${sample.description}",
                        fontSize = 10.sp,
                        color = SanwolfTextSecondary
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Preview Button
                Button(
                    onClick = onPreview,
                    colors = ButtonDefaults.buttonColors(containerColor = if (isPreviewing) SanwolfCyan else SanwolfBlack),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SanwolfCyan),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(
                        imageVector = if (isPreviewing) Icons.Default.GraphicEq else Icons.Default.PlayArrow,
                        contentDescription = "Preview",
                        tint = if (isPreviewing) SanwolfBlack else SanwolfCyan,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (isPreviewing) "PLAY" else "PREVIEW",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPreviewing) SanwolfBlack else SanwolfCyan
                    )
                }

                // Load as Track Button
                Button(
                    onClick = onLoad,
                    colors = ButtonDefaults.buttonColors(containerColor = SanwolfPanelElevated),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SanwolfLime),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Load",
                        tint = SanwolfLime,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "LOAD .WAV",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = SanwolfLime
                    )
                }
            }
        }
    }
}

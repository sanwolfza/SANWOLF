package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ai.FullMusicGenerationResult
import com.example.ai.GeminiDawClient
import com.example.ai.LyriaMusicResult
import com.example.ai.TrackCloneResult
import com.example.ai.TrackFinishingResult
import com.example.ai.ArrangementResult
import com.example.model.ProjectData
import com.example.model.TrackData
import com.example.ui.theme.SanwolfBlack
import com.example.ui.theme.SanwolfCyan
import com.example.ui.theme.SanwolfGold
import com.example.ui.theme.SanwolfLime
import com.example.ui.theme.SanwolfMagenta
import com.example.ui.theme.SanwolfOrange
import com.example.ui.theme.SanwolfPanel
import com.example.ui.theme.SanwolfPanelBorder
import com.example.ui.theme.SanwolfPanelElevated
import com.example.ui.theme.SanwolfTextMuted
import com.example.ui.theme.SanwolfTextPrimary
import com.example.ui.theme.SanwolfTextSecondary
import kotlinx.coroutines.launch

enum class AiTab {
    MUSIC_GEN,
    FINISH_TRACK,
    LYRIA_MUSIC,
    CLONE_MASTER,
    MODULAR_PATCH,
    SOUND_DESIGN,
    CO_PRODUCER_ARRANGE
}

@Composable
fun AiStudioDialog(
    project: ProjectData,
    aiClient: GeminiDawClient,
    audioEngine: com.example.audio.SanwolfAudioEngine,
    onApplyFinishedTrack: (TrackFinishingResult) -> Unit,
    onApplyLyriaTrack: (LyriaMusicResult) -> Unit,
    onApplyClonedTrack: (TrackCloneResult) -> Unit,
    onApplyGeneratedMusic: (FullMusicGenerationResult) -> Unit,
    onApplyArrangement: (ArrangementResult) -> Unit,
    onDismiss: () -> Unit
) {
    var activeTab by remember { mutableStateOf(AiTab.MUSIC_GEN) }
    var isThinking by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Inputs
    var finishPrompt by remember { mutableStateOf("Analyze current stems and generate complementary 808 bassline and syncopated trap claps") }
    var lyriaPrompt by remember { mutableStateOf("Dark atmospheric techno beat 126 BPM with analog modular lead and sub bass") }
    var isLyriaPro by remember { mutableStateOf(false) }
    var cloneStyle by remember { mutableStateOf("Cyber Trap with punchy 808s and modern wide master") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(12.dp))
                .border(1.5.dp, SanwolfGold, RoundedCornerShape(12.dp)),
            color = SanwolfPanel
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
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = SanwolfGold, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "SANWOLF AI STUDIO ENGINE",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                color = SanwolfGold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Gemini 3.5 Flash & Pro (High Thinking) • Lyria-3 Music Gen • Modular Neural Synthesis",
                                fontSize = 10.sp,
                                color = SanwolfCyan
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = SanwolfTextPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // AI Tabs
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AiNavTab("AI MUSIC GEN (GENRE/BPM)", activeTab == AiTab.MUSIC_GEN, SanwolfGold) {
                        activeTab = AiTab.MUSIC_GEN
                    }
                    AiNavTab("FINISH TRACK (HIGH THINKING)", activeTab == AiTab.FINISH_TRACK, SanwolfGold) {
                        activeTab = AiTab.FINISH_TRACK
                    }
                    AiNavTab("LYRIA MUSIC GEN", activeTab == AiTab.LYRIA_MUSIC, SanwolfCyan) {
                        activeTab = AiTab.LYRIA_MUSIC
                    }
                    AiNavTab("CLONE & RE-MASTER", activeTab == AiTab.CLONE_MASTER, SanwolfLime) {
                        activeTab = AiTab.CLONE_MASTER
                    }
                    AiNavTab("MODULAR PATCH", activeTab == AiTab.MODULAR_PATCH, SanwolfMagenta) {
                        activeTab = AiTab.MODULAR_PATCH
                    }
                    AiNavTab("FX & TEXTURES", activeTab == AiTab.SOUND_DESIGN, SanwolfOrange) {
                        activeTab = AiTab.SOUND_DESIGN
                    }
                    AiNavTab("CO-PRODUCER ARRANGE", activeTab == AiTab.CO_PRODUCER_ARRANGE, SanwolfGold) {
                        activeTab = AiTab.CO_PRODUCER_ARRANGE
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Content Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SanwolfPanelElevated)
                        .border(1.dp, SanwolfPanelBorder, RoundedCornerShape(8.dp))
                        .padding(14.dp)
                ) {
                    when (activeTab) {
                        AiTab.MUSIC_GEN -> {
                            MusicGeneratorTabContent(
                                aiClient = aiClient,
                                onApplyGeneratedMusic = onApplyGeneratedMusic
                            )
                        }

                        AiTab.CO_PRODUCER_ARRANGE -> {
                            CoProducerArrangeTabContent(
                                project = project,
                                aiClient = aiClient,
                                onApplyArrangement = onApplyArrangement
                            )
                        }

                        AiTab.FINISH_TRACK -> {
                            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Psychology, contentDescription = null, tint = SanwolfGold, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Gemini 3.1 Pro Preview with HIGH Thinking Mode", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SanwolfGold)
                                }
                                Text(
                                    "Deeply analyzes your piano roll notes, active drum rhythms, BPM and scale to complete unfinished sections.",
                                    fontSize = 10.sp,
                                    color = SanwolfTextSecondary,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                                )

                                SanwolfTextField(
                                    value = finishPrompt,
                                    onValueChange = { finishPrompt = it },
                                    label = { Text("Producer Finishing Instructions", color = SanwolfTextSecondary) },
                                    modifier = Modifier.fillMaxWidth().height(100.dp),
                                    focusedBorderColor = SanwolfGold
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                Button(
                                    onClick = {
                                        isThinking = true
                                        statusMessage = "Gemini 3.1 Pro Thinking: Analyzing chord progression and harmonic frequencies..."
                                        scope.launch {
                                            val result = aiClient.finishTrackWithHighThinking(project, finishPrompt)
                                            onApplyFinishedTrack(result)
                                            isThinking = false
                                            statusMessage = "Added '${result.newTrack.name}' and updated mastering profile!"
                                        }
                                    },
                                    enabled = !isThinking,
                                    modifier = Modifier.fillMaxWidth().height(42.dp).testTag("finish_track_submit"),
                                    colors = ButtonDefaults.buttonColors(containerColor = SanwolfGold)
                                ) {
                                    if (isThinking) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Black, strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("THINKING (HIGH REASONING)...", color = Color.Black, fontWeight = FontWeight.Black)
                                    } else {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("EXECUTE TRACK FINISHING", color = Color.Black, fontWeight = FontWeight.Black)
                                    }
                                }
                            }
                        }

                        AiTab.LYRIA_MUSIC -> {
                            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                                Text("Lyria AI Music Audio Generator", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SanwolfCyan)
                                Text(
                                    "Generates complete studio stems using lyria-3-clip-preview (up to 30s) or lyria-3-pro-preview (full track).",
                                    fontSize = 10.sp,
                                    color = SanwolfTextSecondary,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                                )

                                SanwolfTextField(
                                    value = lyriaPrompt,
                                    onValueChange = { lyriaPrompt = it },
                                    label = { Text("Music Generation Prompt", color = SanwolfTextSecondary) },
                                    modifier = Modifier.fillMaxWidth().height(90.dp),
                                    focusedBorderColor = SanwolfCyan
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { isLyriaPro = false },
                                        colors = ButtonDefaults.buttonColors(containerColor = if (!isLyriaPro) SanwolfCyan else SanwolfPanel)
                                    ) {
                                        Text("30s Clip (lyria-3-clip-preview)", color = if (!isLyriaPro) Color.Black else SanwolfTextSecondary, fontSize = 11.sp)
                                    }
                                    Button(
                                        onClick = { isLyriaPro = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = if (isLyriaPro) SanwolfCyan else SanwolfPanel)
                                    ) {
                                        Text("Full Track (lyria-3-pro-preview)", color = if (isLyriaPro) Color.Black else SanwolfTextSecondary, fontSize = 11.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Button(
                                    onClick = {
                                        isThinking = true
                                        statusMessage = "Generating audio stem with Lyria..."
                                        scope.launch {
                                            val res = aiClient.generateMusicWithLyria(lyriaPrompt, isLyriaPro)
                                            onApplyLyriaTrack(res)
                                            isThinking = false
                                            statusMessage = "Successfully generated and added '${res.trackName}' to project!"
                                        }
                                    },
                                    enabled = !isThinking,
                                    modifier = Modifier.fillMaxWidth().height(42.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = SanwolfCyan)
                                ) {
                                    if (isThinking) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Black, strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("GENERATING AUDIO WITH LYRIA...", color = Color.Black, fontWeight = FontWeight.Bold)
                                    } else {
                                        Text("GENERATE LYRIA AUDIO STEM", color = Color.Black, fontWeight = FontWeight.Black)
                                    }
                                }
                            }
                        }

                        AiTab.CLONE_MASTER -> {
                            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                                Text("Clone Track & Re-Master Afresh", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SanwolfLime)
                                Text(
                                    "Clone imported WAV/MP3/FLAC songs, reconstruct dynamics, and inject customized drums or lead instruments.",
                                    fontSize = 10.sp,
                                    color = SanwolfTextSecondary,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                                )

                                SanwolfTextField(
                                    value = cloneStyle,
                                    onValueChange = { cloneStyle = it },
                                    label = { Text("Target Clone Style & Dynamics", color = SanwolfTextSecondary) },
                                    modifier = Modifier.fillMaxWidth().height(80.dp),
                                    focusedBorderColor = SanwolfLime
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                Button(
                                    onClick = {
                                        isThinking = true
                                        statusMessage = "Cloning track & synthesizing re-mastering profile..."
                                        scope.launch {
                                            val res = aiClient.cloneAndReMasterTrack(project.title, "Electronic/Trap", cloneStyle)
                                            onApplyClonedTrack(res)
                                            isThinking = false
                                            statusMessage = "Cloned track successfully with enhanced dynamics!"
                                        }
                                    },
                                    enabled = !isThinking,
                                    modifier = Modifier.fillMaxWidth().height(42.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = SanwolfLime)
                                ) {
                                    if (isThinking) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Black, strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("CLONING & MASTERING...", color = Color.Black, fontWeight = FontWeight.Bold)
                                    } else {
                                        Text("CLONE TRACK TO MASTER AFRESH", color = Color.Black, fontWeight = FontWeight.Black)
                                    }
                                }
                            }
                        }

                        AiTab.MODULAR_PATCH -> {
                            var modularPrompt by remember { mutableStateOf("Warm analog low-pass bass with slow LFO modulation") }
                            val modularTrack = remember(project) {
                                project.tracks.find { it.type == com.example.model.TrackType.MODULAR_SYNTH } ?: project.tracks.first()
                            }

                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = modularPrompt,
                                        onValueChange = { modularPrompt = it },
                                        placeholder = { Text("Describe custom synth (e.g. Reese sub bass)...", fontSize = 11.sp) },
                                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = SanwolfTextPrimary),
                                        modifier = Modifier.weight(1f).height(42.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = SanwolfMagenta,
                                            unfocusedBorderColor = SanwolfPanelBorder,
                                            focusedTextColor = SanwolfTextPrimary,
                                            unfocusedTextColor = SanwolfTextPrimary
                                        ),
                                        singleLine = true
                                    )

                                    Button(
                                        onClick = {
                                            isThinking = true
                                            statusMessage = "AI synthesizing modular node graph patch..."
                                            scope.launch {
                                                try {
                                                    val graph = aiClient.generateModularPatchWithFlash(modularPrompt)
                                                    modularTrack.modularGraph = graph
                                                    statusMessage = "Applied new modular patch: '$modularPrompt'!"
                                                } catch (e: Exception) {
                                                    statusMessage = "Error: ${e.message}"
                                                } finally {
                                                    isThinking = false
                                                }
                                            }
                                        },
                                        enabled = !isThinking,
                                        modifier = Modifier.height(42.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = SanwolfMagenta),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        if (isThinking) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                        } else {
                                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("AI GENERATE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SanwolfBlack)
                                        .border(1.dp, SanwolfPanelBorder, RoundedCornerShape(8.dp))
                                ) {
                                    ModularSynthView(
                                        graph = modularTrack.modularGraph ?: com.example.model.ModularGraph(),
                                        onTestSound = {
                                            audioEngine.triggerSynthNote(frequency = 220f, durationSec = 0.6f, waveform = com.example.model.SynthWaveform.SAWTOOTH, cutoff = 1800f)
                                        },
                                        onGenerateAiPatch = { desc ->
                                            modularPrompt = desc
                                            isThinking = true
                                            statusMessage = "AI synthesizing modular node graph patch..."
                                            scope.launch {
                                                try {
                                                    val graph = aiClient.generateModularPatchWithFlash(desc)
                                                    modularTrack.modularGraph = graph
                                                    statusMessage = "Applied new modular patch!"
                                                } catch (e: Exception) {
                                                    statusMessage = "Error: ${e.message}"
                                                } finally {
                                                    isThinking = false
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }

                        AiTab.SOUND_DESIGN -> {
                            SoundDesignToolsView(
                                onPreviewRiser = {
                                    audioEngine.triggerDrumSound("Riser Sweep", 0.9f)
                                },
                                onPreviewTexture = {
                                    audioEngine.triggerDrumSound("Ambient Drone", 0.85f)
                                },
                                onAddGeneratedStem = { stemName, isRiser ->
                                    val stemTrack = com.example.model.TrackData(
                                        name = stemName,
                                        type = if (isRiser) com.example.model.TrackType.DRUM_MACHINE else com.example.model.TrackType.SYNTH,
                                        colorHex = if (isRiser) 0xFFFF1744 else 0xFF00E5FF,
                                        steps = BooleanArray(16) { false },
                                        notes = mutableListOf()
                                    )
                                    project.tracks.add(stemTrack)
                                    statusMessage = "Added generator stem '$stemName' to Arranger!"
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                // Status Message Bar
                if (statusMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = statusMessage!!,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = SanwolfLime,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MusicGeneratorTabContent(
    aiClient: GeminiDawClient,
    onApplyGeneratedMusic: (FullMusicGenerationResult) -> Unit
) {
    val scope = rememberCoroutineScope()
    var isGenerating by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf<String?>(null) }

    // Genre selection with default BPM mapping
    val genres = remember {
        listOf(
            "Afro House" to 122,
            "AfroTech" to 125,
            "Amapiano" to 113,
            "Gqom" to 127,
            "Deep House" to 120,
            "House Music" to 124,
            "Jazz" to 95,
            "Trap 808" to 140,
            "Cyberpunk Synthwave" to 118,
            "Deep Techno" to 130,
            "Lo-Fi Chillhop" to 85,
            "UK Drill" to 142,
            "Afrobeat Groove" to 104,
            "Drum & Bass" to 174,
            "Cinematic Ambient" to 92
        )
    }
    var selectedGenre by remember { mutableStateOf(genres.first().first) }
    var bpm by remember { mutableIntStateOf(122) }

    // Drum styles and elements
    val drumStyles = remember {
        listOf("Four on the Floor", "Amapiano Log Groove", "Afro Shaker Roll", "Gqom Heavy Dark", "Jazz Swing", "Heavy 808 & Slides", "Polyrhythmic 12x")
    }
    var selectedDrumStyle by remember { mutableStateOf(drumStyles.first()) }

    val availableDrumElements = remember {
        listOf("808 Sub Kick", "Amapiano Log Drum", "Snare / Clap", "Afro Shakers & Congas", "Trap Hi-Hat Rolls", "Crash / Cymbals")
    }
    val selectedDrums = remember {
        mutableStateListOf("808 Sub Kick", "Snare / Clap", "Afro Shakers & Congas")
    }

    // Instruments
    val availableInstruments = remember {
        listOf(
            "Rhodes E-Piano",
            "Analog Saw Lead",
            "Reese Sub Bass",
            "Deep House Chord Stabs",
            "Jazz Walking Bass",
            "Plucked Chords",
            "Ambient Drone Pad",
            "Vocal Atmosphere",
            "Modular Arp"
        )
    }
    val selectedInstruments = remember {
        mutableStateListOf("Rhodes E-Piano", "Deep House Chord Stabs", "Reese Sub Bass")
    }

    // Key / Scale
    val scales = remember {
        listOf("C Minor", "D Dorian", "F Minor", "G Minor", "E Phrygian", "A Blues", "Bb Major")
    }
    var selectedScale by remember { mutableStateOf(scales.first()) }

    var includeLyriaAudio by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // 1. GENRE SELECTION
        Text(
            text = "1. SELECT GENRE",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = SanwolfGold,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            genres.forEach { (genreName, defaultBpm) ->
                val isSelected = selectedGenre == genreName
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) SanwolfGold else SanwolfPanelElevated)
                        .border(1.dp, if (isSelected) SanwolfGold else SanwolfPanelBorder, RoundedCornerShape(6.dp))
                        .clickable {
                            selectedGenre = genreName
                            bpm = defaultBpm
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("genre_$genreName"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = genreName,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                        color = if (isSelected) SanwolfBlack else SanwolfTextPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. BPM & KEY / SCALE
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // BPM Section
            Column(
                modifier = Modifier
                    .weight(1.5f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SanwolfPanelElevated)
                    .border(1.dp, SanwolfPanelBorder, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("2. TEMPO (BPM)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SanwolfCyan)
                    Text(
                        text = "$bpm BPM",
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        color = SanwolfGold
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = { if (bpm > 60) bpm-- },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "-1", tint = SanwolfTextPrimary, modifier = Modifier.size(14.dp))
                    }
                    Slider(
                        value = bpm.toFloat(),
                        onValueChange = { bpm = it.toInt() },
                        valueRange = 60f..180f,
                        modifier = Modifier.weight(1f).height(20.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = SanwolfGold,
                            activeTrackColor = SanwolfGold,
                            inactiveTrackColor = SanwolfBlack
                        )
                    )
                    IconButton(
                        onClick = { if (bpm < 180) bpm++ },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "+1", tint = SanwolfTextPrimary, modifier = Modifier.size(14.dp))
                    }
                }
            }

            // Scale Section
            Column(
                modifier = Modifier
                    .weight(2f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SanwolfPanelElevated)
                    .border(1.dp, SanwolfPanelBorder, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Text("HARMONIC KEY / SCALE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SanwolfLime)
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    scales.forEach { sc ->
                        val isSel = selectedScale == sc
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isSel) SanwolfLime else SanwolfBlack)
                                .clickable { selectedScale = sc }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = sc,
                                fontSize = 9.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSel) Color.Black else SanwolfTextSecondary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. DRUMS CONFIGURATION
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SanwolfPanelElevated)
                .border(1.dp, SanwolfPanelBorder, RoundedCornerShape(8.dp))
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("3. DRUMS GROOVE & ELEMENTS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SanwolfOrange)
                Text("Style: $selectedDrumStyle", fontSize = 10.sp, color = SanwolfCyan)
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Drum Groove Style Chips
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                drumStyles.forEach { style ->
                    val isSel = selectedDrumStyle == style
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSel) SanwolfOrange else SanwolfBlack)
                            .clickable { selectedDrumStyle = style }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = style,
                            fontSize = 10.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSel) Color.Black else SanwolfTextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Toggleable Drum Elements
            Text("Active Drum Stems:", fontSize = 9.sp, color = SanwolfTextMuted)
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                availableDrumElements.forEach { element ->
                    val isChecked = selectedDrums.contains(element)
                    FilterChip(
                        selected = isChecked,
                        onClick = {
                            if (isChecked) {
                                if (selectedDrums.size > 1) selectedDrums.remove(element)
                            } else {
                                selectedDrums.add(element)
                            }
                        },
                        label = { Text(element, fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SanwolfOrange,
                            selectedLabelColor = Color.Black,
                            containerColor = SanwolfBlack,
                            labelColor = SanwolfTextPrimary
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 4. INSTRUMENTS CONFIGURATION
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SanwolfPanelElevated)
                .border(1.dp, SanwolfPanelBorder, RoundedCornerShape(8.dp))
                .padding(10.dp)
        ) {
            Text("4. INSTRUMENT VOICES", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SanwolfCyan)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                availableInstruments.forEach { inst ->
                    val isChecked = selectedInstruments.contains(inst)
                    FilterChip(
                        selected = isChecked,
                        onClick = {
                            if (isChecked) {
                                if (selectedInstruments.size > 1) selectedInstruments.remove(inst)
                            } else {
                                selectedInstruments.add(inst)
                            }
                        },
                        label = { Text(inst, fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SanwolfCyan,
                            selectedLabelColor = Color.Black,
                            containerColor = SanwolfBlack,
                            labelColor = SanwolfTextPrimary
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 5. LYRIA AUDIO SYNTHESIS TOGGLE
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SanwolfPanelElevated)
                .border(1.dp, SanwolfPanelBorder, RoundedCornerShape(8.dp))
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Waves, contentDescription = null, tint = SanwolfGold, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Generate Audio with Lyria", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SanwolfTextPrimary)
                    Text("Generates realistic audio stem (`lyria-3-clip-preview`) matching these parameters", fontSize = 9.sp, color = SanwolfTextSecondary)
                }
            }

            Switch(
                checked = includeLyriaAudio,
                onCheckedChange = { includeLyriaAudio = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = SanwolfGold,
                    checkedTrackColor = SanwolfGold.copy(alpha = 0.4f)
                )
            )
        }

        if (statusText != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = statusText!!,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = SanwolfLime
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Submit Button
        Button(
            onClick = {
                isGenerating = true
                statusText = "AI Studio composing $selectedGenre at $bpm BPM with $selectedDrumStyle drums and ${selectedInstruments.size} instruments..."
                scope.launch {
                    val result = aiClient.generateMusicByParameters(
                        genre = selectedGenre,
                        bpm = bpm,
                        drumElements = selectedDrums.toList(),
                        drumStyle = selectedDrumStyle,
                        instruments = selectedInstruments.toList(),
                        keyScale = selectedScale,
                        includeLyriaAudio = includeLyriaAudio,
                        isProAudio = false
                    )
                    onApplyGeneratedMusic(result)
                    isGenerating = false
                    statusText = "Generated '${result.title}' with ${result.tracks.size} tracks!"
                }
            },
            enabled = !isGenerating,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .testTag("generate_music_submit"),
            colors = ButtonDefaults.buttonColors(containerColor = SanwolfGold)
        ) {
            if (isGenerating) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Black, strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AI COMPOSING ($selectedGenre @ $bpm BPM)...",
                    color = Color.Black,
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp
                )
            } else {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "GENERATE AI MUSIC ($selectedGenre @ $bpm BPM)",
                    color = Color.Black,
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun AiNavTab(label: String, isSelected: Boolean, activeColor: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) activeColor else SanwolfPanelElevated)
            .border(1.dp, if (isSelected) activeColor else SanwolfPanelBorder, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
            color = if (isSelected) Color.Black else SanwolfTextSecondary
        )
    }
}

@Composable
private fun CoProducerArrangeTabContent(
    project: ProjectData,
    aiClient: GeminiDawClient,
    onApplyArrangement: (ArrangementResult) -> Unit
) {
    var isArranging by remember { mutableStateOf(false) }
    var arrangeInstruction by remember { mutableStateOf("Co-producer arrange: establish atmospheric starting point at beat 0, explosive drop climax at beat 16, and cinematic outro finish point at beat 64.") }
    var resultStatus by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.GraphicEq, contentDescription = null, tint = SanwolfGold, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("AI Co-Producer Arranger (Intro, Drop & Finish)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SanwolfGold)
        }
        Text(
            "Your Co-Producer AI automatically analyzes your project tracks and arranges the timeline into a professional electronic/hip-hop structure: establishing the best starting point (intro), explosive drop climax, and clean finish point (outro).",
            fontSize = 10.sp,
            color = SanwolfTextSecondary,
            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
        )

        SanwolfTextField(
            value = arrangeInstruction,
            onValueChange = { arrangeInstruction = it },
            label = { Text("Arrangement Instructions", color = SanwolfTextSecondary) },
            modifier = Modifier.fillMaxWidth().height(90.dp),
            focusedBorderColor = SanwolfGold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                scope.launch {
                    isArranging = true
                    resultStatus = null
                    try {
                        val arrangement = aiClient.coProducerAutoArrange(project, arrangeInstruction)
                        resultStatus = arrangement.explanation
                        onApplyArrangement(arrangement)
                    } catch (e: Exception) {
                        resultStatus = "Arrangement failed: ${e.message}"
                    }
                    isArranging = false
                }
            },
            enabled = !isArranging,
            modifier = Modifier.fillMaxWidth().height(46.dp).testTag("co_producer_arrange_button"),
            colors = ButtonDefaults.buttonColors(containerColor = SanwolfGold)
        ) {
            if (isArranging) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Black, strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("CO-PRODUCER AI ARRANGING SONG...", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 12.sp)
            } else {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("RUN CO-PRODUCER AI AUTO-ARRANGE", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 12.sp)
            }
        }

        if (resultStatus != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = SanwolfPanel,
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SanwolfGold)
            ) {
                Text(
                    text = resultStatus!!,
                    fontSize = 11.sp,
                    color = SanwolfGold,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
    }
}

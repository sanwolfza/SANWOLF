package com.example.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.example.studio.RecordingEngine
import com.example.ai.ArrangementResult
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ai.GeminiDawClient
import com.example.audio.SanwolfAudioEngine
import com.example.firebase.FirebaseDawManager
import com.example.model.InstrumentCatalog
import com.example.model.InstrumentPreset
import com.example.model.MasteringConfig
import com.example.model.MidiNote
import com.example.model.ModularGraph
import com.example.model.ModularNode
import com.example.model.ModularNodeType
import com.example.model.NodeCable
import com.example.model.ProjectData
import com.example.model.SynthWaveform
import com.example.model.TrackData
import com.example.model.TrackType
import com.example.model.UndoRedoManager
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

@Composable
fun SanwolfDawApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val audioEngine = remember { SanwolfAudioEngine(context) }
    val recordingEngine = remember { RecordingEngine(context.applicationContext) }
    val aiClient = remember { GeminiDawClient() }
    val firebaseManager = remember { FirebaseDawManager(context) }
    val undoRedoManager = remember { UndoRedoManager {} }
    var projectVersion by remember { mutableIntStateOf(0) }

    // Initial default project
    val project = remember {
        val p = ProjectData(
            title = "WOLF_EXP_SESSION_01",
            bpm = 126,
            swing = 0.15f
        )
        // 1. Kick Drum
        p.tracks.add(
            TrackData(
                name = "808 Sub Kick",
                type = TrackType.DRUM_MACHINE,
                colorHex = 0xFFFF5722,
                synthPresetName = "808 Sub Kick",
                synthPresetCategory = "Drum Kits",
                stepCount = 32,
                steps = booleanArrayOf(
                    true, false, false, false,  false, false, true, false,
                    true, false, false, false,  false, false, false, true,
                    true, false, false, false,  false, false, true, false,
                    true, false, false, false,  false, false, true, false
                )
            )
        )
        // 2. Snare / Clap
        p.tracks.add(
            TrackData(
                name = "Snare Trap Crack",
                type = TrackType.DRUM_MACHINE,
                colorHex = 0xFFFF2A6D,
                synthPresetName = "Snare Trap Crack",
                synthPresetCategory = "Drum Kits",
                stepCount = 32,
                steps = booleanArrayOf(
                    false, false, false, false, true, false, false, false,
                    false, false, false, false, true, false, false, false,
                    false, false, false, false, true, false, false, false,
                    false, false, false, false, true, false, false, true
                )
            )
        )
        // 3. Polyrhythmic HiHats (24 steps polyrhythm!)
        p.tracks.add(
            TrackData(
                name = "Poly HiHats",
                type = TrackType.DRUM_MACHINE,
                colorHex = 0xFF76FF03,
                synthPresetName = "Closed Hi-Hat",
                synthPresetCategory = "Drum Kits",
                stepCount = 24,
                steps = booleanArrayOf(
                    true, false, true, false, true, false, true, false,
                    true, true, true, false,  true, false, true, false,
                    true, false, true, false, true, true, true, false
                )
            )
        )
        // 4. Concert Grand Piano
        p.tracks.add(
            TrackData(
                name = "Concert Grand Piano",
                type = TrackType.SYNTH,
                colorHex = 0xFFFFD700,
                synthPresetName = "Grand Piano",
                synthPresetCategory = "Keys & Piano",
                synthWaveform = SynthWaveform.TRIANGLE,
                filterCutoffHz = 6500f,
                notes = mutableListOf(
                    MidiNote(pitch = 60, startBeat = 0.0, lengthBeats = 1.0, velocity = 0.85f),
                    MidiNote(pitch = 64, startBeat = 0.0, lengthBeats = 1.0, velocity = 0.80f),
                    MidiNote(pitch = 67, startBeat = 0.0, lengthBeats = 1.0, velocity = 0.82f),
                    MidiNote(pitch = 57, startBeat = 2.0, lengthBeats = 1.5, velocity = 0.85f),
                    MidiNote(pitch = 60, startBeat = 2.0, lengthBeats = 1.5, velocity = 0.80f),
                    MidiNote(pitch = 65, startBeat = 2.0, lengthBeats = 1.5, velocity = 0.82f)
                )
            )
        )
        // 5. 808 Sub Bass
        p.tracks.add(
            TrackData(
                name = "808 Sub Boom",
                type = TrackType.SYNTH,
                colorHex = 0xFFFFAB00,
                synthPresetName = "808 Sub Boom",
                synthPresetCategory = "Basses & 808s",
                synthWaveform = SynthWaveform.SINE,
                filterCutoffHz = 500f,
                notes = mutableListOf(
                    MidiNote(pitch = 36, startBeat = 0.0, lengthBeats = 1.5, velocity = 0.95f),
                    MidiNote(pitch = 36, startBeat = 1.5, lengthBeats = 0.5, velocity = 0.85f),
                    MidiNote(pitch = 33, startBeat = 2.0, lengthBeats = 1.5, velocity = 0.90f),
                    MidiNote(pitch = 41, startBeat = 3.5, lengthBeats = 0.5, velocity = 0.90f)
                )
            )
        )
        // 6. Expressive Violin Solo
        p.tracks.add(
            TrackData(
                name = "Violin Solo",
                type = TrackType.SYNTH,
                colorHex = 0xFF69F0AE,
                synthPresetName = "Violin Solo",
                synthPresetCategory = "Strings & Orchestral",
                synthWaveform = SynthWaveform.SAWTOOTH,
                filterCutoffHz = 4800f,
                notes = mutableListOf(
                    MidiNote(pitch = 72, startBeat = 0.0, lengthBeats = 2.0, velocity = 0.88f),
                    MidiNote(pitch = 76, startBeat = 2.0, lengthBeats = 1.5, velocity = 0.85f),
                    MidiNote(pitch = 79, startBeat = 3.5, lengthBeats = 0.5, velocity = 0.92f)
                )
            )
        )
        // 7. Punchy Cyber Saw Lead
        p.tracks.add(
            TrackData(
                name = "Cyber Lead",
                type = TrackType.SYNTH,
                colorHex = 0xFF00E5FF,
                synthPresetName = "Cyber Lead",
                synthPresetCategory = "Leads & Synths",
                synthWaveform = SynthWaveform.SAWTOOTH,
                filterCutoffHz = 4000f,
                notes = mutableListOf(
                    MidiNote(pitch = 60, startBeat = 0.0, lengthBeats = 1.0, velocity = 0.85f),
                    MidiNote(pitch = 63, startBeat = 1.0, lengthBeats = 1.0, velocity = 0.80f),
                    MidiNote(pitch = 67, startBeat = 2.0, lengthBeats = 1.5, velocity = 0.90f),
                    MidiNote(pitch = 70, startBeat = 3.5, lengthBeats = 0.5, velocity = 0.85f)
                )
            )
        )
        // 8. Modular Synth Rack Track
        p.tracks.add(
            TrackData(
                name = "Modular Acid Voice",
                type = TrackType.MODULAR_SYNTH,
                colorHex = 0xFFD9A441,
                modularGraph = ModularGraph(
                    nodes = mutableListOf(
                        ModularNode(name = "VCO 1 (Saw)", type = ModularNodeType.OSCILLATOR, x = 60f, y = 80f),
                        ModularNode(name = "Ladder Filter", type = ModularNodeType.FILTER, x = 280f, y = 80f),
                        ModularNode(name = "Envelope", type = ModularNodeType.ADSR_ENV, x = 160f, y = 260f),
                        ModularNode(name = "Wolf Saturation", type = ModularNodeType.WOLF_DRIVE, x = 500f, y = 80f),
                        ModularNode(name = "Master Out", type = ModularNodeType.OUTPUT, x = 720f, y = 80f)
                    ),
                    cables = mutableListOf(
                        NodeCable(fromNodeId = "1", fromPort = "Out", toNodeId = "2", toPort = "In", colorHex = 0xFFD9A441)
                    )
                )
            )
        )
        p
    }

    LaunchedEffect(projectVersion) {
        undoRedoManager.saveState(project)
        audioEngine.currentProject = project
    }

    DisposableEffect(Unit) {
        onDispose {
            recordingEngine.release()
            audioEngine.release()
        }
    }

    // Navigation & View States
    var viewMode by remember { mutableStateOf(DawViewMode.ARRANGER) }
    var isMetronomeEnabled by remember { mutableStateOf(false) }
    var metronomeVolume by remember { mutableFloatStateOf(0.5f) }
    var isFocusMode by remember { mutableStateOf(false) } // Disappearing windows immersion toggle
    var selectedTrackIndex by remember { mutableIntStateOf(0) }
    var isRecordingAudio by remember { mutableStateOf(false) }
    var isSidebarExpanded by remember { mutableStateOf(true) }
    var isPianoKeyboardExpanded by remember { mutableStateOf(true) }
    var isRightMixerExpanded by remember { mutableStateOf(false) }

    // Dialog Modals
    val sharedPrefs = remember { context.getSharedPreferences("sanwolf_daw_prefs", android.content.Context.MODE_PRIVATE) }
    var showTutorialDialog by remember {
        mutableStateOf(!sharedPrefs.getBoolean("tutorial_completed", false))
    }
    var showAiStudioDialog by remember { mutableStateOf(false) }
    var showCoProducerArranger by remember { mutableStateOf(false) }
    var showAiPatternAnalyzer by remember { mutableStateOf(false) }
    var showMasteringDialog by remember { mutableStateOf(false) }
    var showCloudSyncDialog by remember { mutableStateOf(false) }
    var showProjectManagerDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showMasterMixer by remember { mutableStateOf(false) }
    var showInstrumentSelectorForTrack by remember { mutableStateOf<TrackData?>(null) }
    var showInstrumentBrowserForNewTrack by remember { mutableStateOf(false) }
    var showFactorySampleManager by remember { mutableStateOf(false) }

    // Document Picker for Audio Import
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val trackName = uri.lastPathSegment?.substringAfterLast('/') ?: "Imported Stem"
            val newTrack = TrackData(
                name = trackName,
                type = TrackType.AUDIO_IMPORT,
                colorHex = 0xFFFF2A6D,
                audioUri = uri.toString(),
                audioFileName = trackName
            )
            project.tracks.add(newTrack)
            audioEngine.addStemAudio(newTrack.id, uri)
            projectVersion++
            Toast.makeText(context, "Imported '$trackName' into project!", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Microphone Recording (real PCM -> WAV capture) ---
    var recordingStartMs by remember { mutableLongStateOf(0L) }
    var recordingElapsedMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isRecordingAudio) {
        while (isRecordingAudio) {
            recordingElapsedMs = SystemClock.elapsedRealtime() - recordingStartMs
            delay(200)
        }
    }

    val startMicRecording: () -> Unit = {
        recordingEngine.start()
            .onSuccess {
                recordingStartMs = SystemClock.elapsedRealtime()
                recordingElapsedMs = 0L
                isRecordingAudio = true
                Toast.makeText(context, "Recording… tap Record again to stop", Toast.LENGTH_SHORT).show()
            }
            .onFailure { err ->
                isRecordingAudio = false
                Toast.makeText(context, "Couldn't start recording: ${err.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
            }
    }

    val stopMicRecording: () -> Unit = {
        isRecordingAudio = false
        recordingEngine.stop()
            .onSuccess { file ->
                val takeNumber = project.tracks.count { it.name.startsWith("Mic Take") } + 1
                val trackName = "Mic Take $takeNumber"
                val fileUri = Uri.fromFile(file)
                // Same path as an imported audio file: AUDIO_IMPORT track + stem player
                val newTrack = TrackData(
                    name = trackName,
                    type = TrackType.AUDIO_IMPORT,
                    colorHex = 0xFFFF2A6D,
                    audioUri = fileUri.toString(),
                    audioFileName = file.name
                )
                project.tracks.add(newTrack)
                audioEngine.addStemAudio(newTrack.id, fileUri)
                projectVersion++
                val seconds = recordingEngine.durationMs(file) / 1000.0
                Toast.makeText(
                    context,
                    "Saved ${String.format("%.1f", seconds)}s recording as '$trackName'",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .onFailure { err ->
                Toast.makeText(context, "Recording failed: ${err.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
            }
    }

    // Audio Record Permission Launcher
    val recordPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startMicRecording()
        } else {
            Toast.makeText(context, "Microphone permission is needed to record. You can enable it in Settings.", Toast.LENGTH_LONG).show()
        }
    }

    val onRecordPressed: () -> Unit = {
        if (isRecordingAudio) {
            stopMicRecording()
        } else if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startMicRecording()
        } else {
            recordPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    val selectedTrack: TrackData? = project.tracks.getOrNull(selectedTrackIndex) ?: project.tracks.firstOrNull()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = SanwolfBlack,
        // Immersive full screen: status/navigation bars are hidden, so only keep controls
        // clear of a display cutout (notch / punch-hole) on the short edges.
        contentWindowInsets = WindowInsets.displayCutout
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // --- Top Station Toolbar ---
            TopToolbar(
                project = project,
                isPlaying = audioEngine.isPlaying,
                currentBeat = audioEngine.currentBeat,
                peakLeft = audioEngine.currentPeakLeft,
                peakRight = audioEngine.currentPeakRight,
                isSidebarExpanded = isSidebarExpanded,
                onToggleSidebar = { isSidebarExpanded = !isSidebarExpanded },
                isRecording = isRecordingAudio,
                recordingElapsedMs = recordingElapsedMs,
                onRecordToggle = onRecordPressed
            )

            // --- Main Workspace Area: Sidebar & Content View ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Collapsible Sidebar Panel
                androidx.compose.animation.AnimatedVisibility(
                    visible = isSidebarExpanded && !isFocusMode,
                    enter = androidx.compose.animation.slideInHorizontally(initialOffsetX = { -it }) + androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.slideOutHorizontally(targetOffsetX = { -it }) + androidx.compose.animation.fadeOut()
                ) {
                    DawSidebarPanel(
                        project = project,
                        isPlaying = audioEngine.isPlaying,
                        currentBeat = audioEngine.currentBeat,
                        isRecording = isRecordingAudio,
                        isFocusMode = isFocusMode,
                        isMetronomeEnabled = isMetronomeEnabled,
                        metronomeVolume = metronomeVolume,
                        canUndo = undoRedoManager.canUndo(),
                        canRedo = undoRedoManager.canRedo(),
                        onPlayToggle = {
                            if (audioEngine.isPlaying) audioEngine.pause() else audioEngine.play()
                        },
                        onStop = { audioEngine.stop() },
                        onRecordToggle = onRecordPressed,
                        onBpmChange = { project.bpm = it },
                        onMasterVolumeChange = {
                            project.masterVolume = it
                            audioEngine.masterVolume = it
                        },
                        onToggleFocusMode = { isFocusMode = !isFocusMode },
                        onMetronomeToggle = { isMetronomeEnabled = !isMetronomeEnabled },
                        onMetronomeVolumeChange = { metronomeVolume = it },
                        onUndo = {
                            undoRedoManager.undo(project)?.let { restored ->
                                project.tracks.clear()
                                project.tracks.addAll(restored.tracks)
                                project.bpm = restored.bpm
                                project.title = restored.title
                                project.masterVolume = restored.masterVolume
                                project.masteringConfig = restored.masteringConfig
                                projectVersion++
                            }
                        },
                        onRedo = {
                            undoRedoManager.redo(project)?.let { restored ->
                                project.tracks.clear()
                                project.tracks.addAll(restored.tracks)
                                project.bpm = restored.bpm
                                project.title = restored.title
                                project.masterVolume = restored.masterVolume
                                project.masteringConfig = restored.masteringConfig
                                projectVersion++
                            }
                        },
                        onOpenAiStudio = { showAiStudioDialog = true },
                        onOpenCoProducerArranger = { showCoProducerArranger = true },
                        onOpenAiAnalyzer = { showAiPatternAnalyzer = true },
                        onOpenMastering = { showMasteringDialog = true },
                        onOpenMixer = { showMasterMixer = true },
                        onOpenProjectManager = { showProjectManagerDialog = true },
                        onNewProject = {
                            val newTitle = "SESSION_${(1000..9999).random()}"
                            val newProj = com.example.model.ProjectData(
                                title = newTitle,
                                bpm = 120,
                                tracks = mutableStateListOf(
                                    com.example.model.TrackData(name = "Kick 808", type = com.example.model.TrackType.DRUM_MACHINE, colorHex = 0xFFFF1744),
                                    com.example.model.TrackData(name = "Synth Lead", type = com.example.model.TrackType.SYNTH, colorHex = 0xFF00E5FF)
                                )
                            )
                            project.tracks.clear()
                            project.tracks.addAll(newProj.tracks)
                            project.title = newProj.title
                            project.bpm = newProj.bpm
                            project.isCloudSynced = false
                            projectVersion++
                            Toast.makeText(context, "Created new project '$newTitle'", Toast.LENGTH_SHORT).show()
                        },
                        onSaveProject = {
                            scope.launch {
                                val res = firebaseManager.saveProjectToCloud(project)
                                res.onSuccess {
                                    project.isCloudSynced = true
                                    projectVersion++
                                    Toast.makeText(context, "Saved '${project.title}' to Cloud!", Toast.LENGTH_SHORT).show()
                                }.onFailure {
                                    project.isCloudSynced = true
                                    projectVersion++
                                    Toast.makeText(context, "Saved to local cache", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onOpenCloudSync = { showCloudSyncDialog = true },
                        onOpenExport = { showExportDialog = true },
                        onOpenSampleManager = { showFactorySampleManager = true },
                        onOpenTutorial = { showTutorialDialog = true },
                        onCloseSidebar = { isSidebarExpanded = false }
                    )
                }

                // --- Main Workstation Body ---
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        when (viewMode) {
                    DawViewMode.ARRANGER -> {
                        ArrangerView(
                            project = project,
                            currentBeat = audioEngine.currentBeat,
                            selectedTrackIndex = selectedTrackIndex,
                            onSelectTrack = { selectedTrackIndex = it },
                            onEditInPianoRoll = { idx ->
                                selectedTrackIndex = idx
                                val tr = project.tracks.getOrNull(idx)
                                if (tr != null && tr.type == com.example.model.TrackType.DRUM_MACHINE) {
                                    viewMode = DawViewMode.STEP_SEQUENCER
                                } else {
                                    viewMode = DawViewMode.PIANO_ROLL
                                }
                            },
                            onTrackVolumeChange = { idx, vol ->
                                project.tracks.getOrNull(idx)?.volume = vol
                            },
                            onTrackPanChange = { idx, pan ->
                                project.tracks.getOrNull(idx)?.pan = pan
                            },
                            onToggleMute = { idx ->
                                project.tracks.getOrNull(idx)?.let {
                                    it.muted = !it.muted
                                    audioEngine.updateStemMuteSolo(project)
                                    val status = if (it.muted) "Muted" else "Unmuted"
                                    Toast.makeText(context, "$status '${it.name}'", Toast.LENGTH_SHORT).show()
                                }
                                projectVersion++
                            },
                            onToggleSolo = { idx ->
                                project.tracks.getOrNull(idx)?.let {
                                    it.solo = !it.solo
                                    audioEngine.updateStemMuteSolo(project)
                                    val status = if (it.solo) "Soloed" else "Unsoloed"
                                    Toast.makeText(context, "$status '${it.name}'", Toast.LENGTH_SHORT).show()
                                }
                                projectVersion++
                            },
                            onAddTrack = { type ->
                                when (type) {
                                    TrackType.AUDIO_IMPORT -> audioPickerLauncher.launch(arrayOf("audio/*"))
                                    TrackType.SYNTH -> {
                                        val synthInstruments = listOf(
                                            Pair("Concert Grand Piano", "Keys & Piano"),
                                            Pair("Rhodes Mk1 Tines", "Keys & Piano"),
                                            Pair("808 Sub Boom", "Basses & 808s"),
                                            Pair("Violin Solo", "Strings & Orchestral"),
                                            Pair("Trumpet Solo", "Brass & Winds"),
                                            Pair("Moog Model D Bass", "Basses & 808s"),
                                            Pair("Cyber Lead", "Leads & Synths"),
                                            Pair("Warm Analog Pad", "Keys & Piano")
                                        )
                                        val chosen = synthInstruments[(project.tracks.size) % synthInstruments.size]
                                        project.tracks.add(
                                            TrackData(
                                                name = chosen.first,
                                                type = TrackType.SYNTH,
                                                colorHex = 0xFF00E5FF,
                                                synthPresetName = chosen.first,
                                                synthPresetCategory = chosen.second
                                            )
                                        )
                                    }
                                    TrackType.DRUM_MACHINE -> {
                                        val drumSounds = listOf("808 Sub Kick", "Snare Trap Crack", "Closed Hi-Hat", "Clean Hand Clap", "Djembe African Drum", "Congas & Bongos")
                                        val chosen = drumSounds[(project.tracks.size) % drumSounds.size]
                                        project.tracks.add(
                                            TrackData(
                                                name = chosen,
                                                type = TrackType.DRUM_MACHINE,
                                                colorHex = 0xFFFF9100,
                                                synthPresetName = chosen,
                                                synthPresetCategory = "Drum Kits"
                                            )
                                        )
                                    }
                                    TrackType.MODULAR_SYNTH -> {
                                        project.tracks.add(
                                            TrackData(name = "Modular Sub ${project.tracks.size + 1}", type = TrackType.MODULAR_SYNTH, colorHex = 0xFFD9A441)
                                        )
                                    }
                                    TrackType.RISER_TEXTURE -> {
                                        project.tracks.add(
                                            TrackData(name = "Atmosphere ${project.tracks.size + 1}", type = TrackType.RISER_TEXTURE, colorHex = 0xFF76FF03)
                                        )
                                    }
                                }
                            },
                            onDeleteTrack = { idx ->
                                if (project.tracks.size > 1 && idx in project.tracks.indices) {
                                    project.tracks.removeAt(idx)
                                    if (selectedTrackIndex >= project.tracks.size) {
                                        selectedTrackIndex = project.tracks.size - 1
                                    }
                                }
                            },
                            onTimelineScrub = { beat ->
                                audioEngine.seekToBeat(beat)
                            },
                            onSelectInstrumentForTrack = { trackIdx ->
                                showInstrumentSelectorForTrack = project.tracks.getOrNull(trackIdx)
                            },
                            onOpenInstrumentBrowser = {
                                showInstrumentBrowserForNewTrack = true
                            },
                            firebaseManager = firebaseManager,
                            onLoadProject = { loadedProj ->
                                project.tracks.clear()
                                project.tracks.addAll(loadedProj.tracks)
                                project.title = loadedProj.title
                                project.bpm = loadedProj.bpm
                                project.swing = loadedProj.swing
                                project.masterVolume = loadedProj.masterVolume
                                project.masteringConfig = loadedProj.masteringConfig
                                project.collaborationRoomId = loadedProj.collaborationRoomId
                                project.isCloudSynced = loadedProj.isCloudSynced
                                project.lastSavedTimestamp = loadedProj.lastSavedTimestamp
                                selectedTrackIndex = 0
                                audioEngine.stop()
                                audioEngine.currentProject = project
                                projectVersion++
                            },
                            onProjectSaved = {
                                project.isCloudSynced = true
                                projectVersion++
                            },
                            onMoveTrackUp = { idx ->
                                if (idx > 0) {
                                    val item = project.tracks.removeAt(idx)
                                    project.tracks.add(idx - 1, item)
                                    selectedTrackIndex = idx - 1
                                    projectVersion++
                                }
                            },
                            onMoveTrackDown = { idx ->
                                if (idx < project.tracks.size - 1) {
                                    val item = project.tracks.removeAt(idx)
                                    project.tracks.add(idx + 1, item)
                                    selectedTrackIndex = idx + 1
                                    projectVersion++
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    DawViewMode.STEP_SEQUENCER -> {
                        StepSequencerView(
                            project = project,
                            currentStep = audioEngine.currentStep,
                            isPlaying = audioEngine.isPlaying,
                            onStepToggle = { tIdx, sIdx ->
                                project.tracks.getOrNull(tIdx)?.let { tr ->
                                    if (sIdx in tr.steps.indices) {
                                        tr.steps[sIdx] = !tr.steps[sIdx]
                                        if (tr.steps[sIdx]) {
                                            if (tr.type == TrackType.DRUM_MACHINE) {
                                                audioEngine.triggerDrumSound("${tr.synthPresetName} ${tr.name}", tr.volume, tr.pan)
                                            } else {
                                                audioEngine.triggerTrackNote(tr, 60, 0.25f, tr.volume)
                                            }
                                        }
                                    }
                                }
                            },
                            onStepVelocityChange = { tIdx, sIdx, vel ->
                                project.tracks.getOrNull(tIdx)?.let { tr ->
                                    if (sIdx in tr.stepVelocities.indices) tr.stepVelocities[sIdx] = vel
                                }
                            },
                            onPolyStepCountChange = { tIdx, count ->
                                project.tracks.getOrNull(tIdx)?.stepCount = count
                            },
                            onPreviewTrack = { tIdx ->
                                project.tracks.getOrNull(tIdx)?.let { tr ->
                                    if (tr.type == TrackType.DRUM_MACHINE) {
                                        audioEngine.triggerDrumSound("${tr.synthPresetName} ${tr.name}", tr.volume, tr.pan)
                                    } else {
                                        audioEngine.triggerTrackNote(tr, 60, 0.4f, tr.volume)
                                    }
                                }
                            },
                            onApplyPresetPattern = { preset ->
                                when (preset) {
                                    "Trap 808", "Trap 808 (32s)" -> {
                                        project.tracks.forEach { it.stepCount = 32 }
                                        project.tracks.firstOrNull { it.type == TrackType.DRUM_MACHINE }?.let {
                                            it.steps.fill(false)
                                            it.steps[0] = true; it.steps[6] = true; it.steps[8] = true; it.steps[14] = true
                                            it.steps[16] = true; it.steps[22] = true; it.steps[24] = true; it.steps[30] = true
                                        }
                                        project.tracks.getOrNull(1)?.let {
                                            it.steps.fill(false)
                                            it.steps[4] = true; it.steps[12] = true; it.steps[20] = true; it.steps[28] = true
                                        }
                                    }
                                    "Amapiano", "Amapiano (32s)" -> {
                                        project.tracks.forEach { it.stepCount = 32 }
                                        project.tracks.firstOrNull { it.type == TrackType.DRUM_MACHINE }?.let {
                                            it.steps.fill(false)
                                            it.steps[0] = true; it.steps[3] = true; it.steps[6] = true; it.steps[8] = true
                                            it.steps[11] = true; it.steps[14] = true; it.steps[16] = true; it.steps[22] = true
                                            it.steps[24] = true; it.steps[30] = true
                                        }
                                    }
                                    "Polyrhythm 3:4", "Polyrhythm (24s)" -> {
                                        val drumTracks = project.tracks.filter { it.type == TrackType.DRUM_MACHINE }
                                        drumTracks.getOrNull(0)?.stepCount = 24
                                        drumTracks.getOrNull(1)?.stepCount = 18
                                        drumTracks.getOrNull(2)?.stepCount = 12
                                    }
                                    "Afrobeat", "Afrobeat (24s)" -> {
                                        project.tracks.forEach { it.stepCount = 24 }
                                        project.tracks.firstOrNull { it.type == TrackType.DRUM_MACHINE }?.let {
                                            it.steps.fill(false)
                                            it.steps[0] = true; it.steps[4] = true; it.steps[6] = true; it.steps[10] = true
                                            it.steps[12] = true; it.steps[16] = true; it.steps[18] = true; it.steps[22] = true
                                        }
                                    }
                                    "Techno", "Techno (32s)" -> {
                                        project.tracks.firstOrNull { it.type == TrackType.DRUM_MACHINE }?.let {
                                            it.stepCount = 32
                                            it.steps.fill(false)
                                            for (s in 0 until 32 step 4) it.steps[s] = true
                                        }
                                    }
                                    "Boom Bap", "Boom Bap (16s)" -> {
                                        project.tracks.firstOrNull { it.type == TrackType.DRUM_MACHINE }?.let {
                                            it.stepCount = 16
                                            it.steps.fill(false)
                                            it.steps[0] = true; it.steps[4] = true; it.steps[8] = true; it.steps[12] = true
                                        }
                                    }
                                }
                                projectVersion++
                            },
                            onSwingChange = { project.swing = it },
                            onTriggerPad = { padName, velocity ->
                                audioEngine.triggerDrumSound(padName, velocity)
                            },
                            onAddTrack = { type, name ->
                                val color = when (type) {
                                    TrackType.DRUM_MACHINE -> if (name.contains("Kick")) 0xFFFF5722 else if (name.contains("Snare")) 0xFFFF2A6D else 0xFFFF9100
                                    else -> if (name.contains("Bass")) 0xFFFFAB00 else 0xFF00E5FF
                                }
                                project.tracks.add(
                                    TrackData(
                                        name = name,
                                        type = type,
                                        colorHex = color,
                                        synthPresetName = name,
                                        synthPresetCategory = if (type == TrackType.DRUM_MACHINE) "Drum Kits" else if (name.contains("Bass")) "Basses & 808s" else if (name.contains("Violin")) "Strings & Orchestral" else if (name.contains("Trumpet")) "Brass & Winds" else "Keys & Piano",
                                        stepCount = 32
                                    )
                                )
                                projectVersion++
                            }
                        )
                    }

                    DawViewMode.PIANO_ROLL -> {
                        selectedTrack?.let { tr ->
                            PianoRollView(
                                track = tr,
                                currentBeat = audioEngine.currentBeat,
                                isPlaying = audioEngine.isPlaying,
                                onAddOrRemoveNote = { pitch, startBeat ->
                                    val existing = tr.notes.find { it.pitch == pitch && Math.abs(it.startBeat - startBeat) < 0.25 }
                                    if (existing != null) tr.notes.remove(existing)
                                    else tr.notes.add(com.example.model.MidiNote(pitch = pitch, startBeat = startBeat, lengthBeats = 1.0))
                                    projectVersion++
                                },
                                onClearNotes = {
                                    tr.notes.clear()
                                    projectVersion++
                                },
                                onPreviewPitch = { pitch ->
                                    audioEngine.triggerMidiNote(pitch, tr)
                                },
                                onQuantizeNotes = { _ -> },
                                onStampChord = { _ -> },
                                projectVersion = projectVersion,
                                bpm = project.bpm,
                                initialSwing = project.swing,
                                onSwingChange = { project.swing = it },
                                onOpenInstrumentSelector = {
                                    showInstrumentSelectorForTrack = tr
                                }
                            )
                        }
                    }

                    DawViewMode.DRUM_PADS -> {
                        DrumPadView(
                            onTriggerPad = { padName, velocity ->
                                audioEngine.triggerDrumSound(padName, velocity)
                            }
                        )
                    }

                    DawViewMode.MODULAR_SYNTH -> {
                        // Merged inside AI Studio dialog
                    }

                    DawViewMode.SOUND_TOOLS -> {
                        // Merged inside AI Studio dialog
                    }
                }
            }

            // --- Collapsible Live Piano Keyboard Bottom Dock ---
            LivePianoBar(
                selectedTrack = selectedTrack,
                currentBeat = audioEngine.currentBeat,
                isPlaying = audioEngine.isPlaying,
                audioEngine = audioEngine,
                isExpanded = isPianoKeyboardExpanded,
                onToggleExpanded = { isPianoKeyboardExpanded = !isPianoKeyboardExpanded },
                isRecording = isRecordingAudio,
                canUndo = undoRedoManager.canUndo(),
                canRedo = undoRedoManager.canRedo(),
                bpm = project.bpm,
                onBpmChange = { project.bpm = it; projectVersion++ },
                onPlayToggle = {
                    if (audioEngine.isPlaying) audioEngine.pause() else audioEngine.play()
                },
                onStop = { audioEngine.stop() },
                onRecordToggle = onRecordPressed,
                onUndo = {
                    undoRedoManager.undo(project)
                    projectVersion++
                },
                onRedo = {
                    undoRedoManager.redo(project)
                    projectVersion++
                },
                onKeyTriggered = { pitch ->
                    // Live record / insert note into active track if in Piano Roll mode
                    selectedTrack?.let { tr ->
                        if (viewMode == DawViewMode.PIANO_ROLL) {
                            val currentSnapBeat = (Math.round(audioEngine.currentBeat * 4.0) / 4.0)
                            val existingNote = tr.notes.find { it.pitch == pitch && Math.abs(it.startBeat - currentSnapBeat) < 0.25 }
                            if (existingNote == null) {
                                tr.notes.add(com.example.model.MidiNote(pitch = pitch, startBeat = currentSnapBeat, lengthBeats = 0.5))
                                projectVersion++
                            }
                        }
                    }
                }
            )
        }

        // Right Collapsible Mixer Panel
        RightMixerPanel(
            project = project,
            audioEngine = audioEngine,
            isExpanded = isRightMixerExpanded,
            onToggleExpand = { isRightMixerExpanded = !isRightMixerExpanded },
            onTrackVolumeChange = { idx, vol ->
                project.tracks.getOrNull(idx)?.volume = vol
                projectVersion++
            },
            onTrackPanChange = { idx, pan ->
                project.tracks.getOrNull(idx)?.pan = pan
                projectVersion++
            },
            onToggleMute = { idx ->
                project.tracks.getOrNull(idx)?.let {
                    it.muted = !it.muted
                    audioEngine.updateStemMuteSolo(project)
                }
                projectVersion++
            },
            onToggleSolo = { idx ->
                project.tracks.getOrNull(idx)?.let {
                    it.solo = !it.solo
                    audioEngine.updateStemMuteSolo(project)
                }
                projectVersion++
            }
        )
    }
}

        // --- Dialog Modals ---
        if (showAiStudioDialog) {
            AiStudioDialog(
                project = project,
                aiClient = aiClient,
                audioEngine = audioEngine,
                onApplyFinishedTrack = { finishing ->
                    project.tracks.add(finishing.newTrack)
                    project.masteringConfig = finishing.masteringConfig
                    Toast.makeText(context, "AI Studio: Added ${finishing.newTrack.name}", Toast.LENGTH_LONG).show()
                },
                onApplyLyriaTrack = { lyria ->
                    val newLyriaTrack = TrackData(
                        name = lyria.trackName,
                        type = TrackType.SYNTH,
                        colorHex = 0xFF00E5FF,
                        notes = lyria.notes.toMutableList()
                    )
                    project.tracks.add(newLyriaTrack)
                    Toast.makeText(context, "Lyria Audio Track added!", Toast.LENGTH_SHORT).show()
                },
                onApplyClonedTrack = { clone ->
                    val newCloneTrack = TrackData(
                        name = clone.addedDrumName,
                        type = TrackType.DRUM_MACHINE,
                        colorHex = 0xFFFF9100,
                        steps = clone.addedDrumSteps,
                        notes = clone.addedLeadNotes.toMutableList()
                    )
                    project.tracks.add(newCloneTrack)
                    project.masteringConfig.targetLufs = clone.targetLufs
                    project.masteringConfig.lowGainDb = clone.eqLowGain
                    project.masteringConfig.highGainDb = clone.eqHighGain
                    Toast.makeText(context, "Cloned & Re-Mastered track added!", Toast.LENGTH_SHORT).show()
                },
                onApplyGeneratedMusic = { result ->
                    project.title = result.title
                    project.bpm = result.bpm
                    project.tracks.clear()
                    project.tracks.addAll(result.tracks)
                    project.masteringConfig = result.masteringConfig
                    selectedTrackIndex = 0
                    viewMode = DawViewMode.ARRANGER

                    // Restart audio engine playback with new stems
                    audioEngine.stop()
                    audioEngine.play()

                    showAiStudioDialog = false
                    Toast.makeText(
                        context,
                        "AI Composed: ${result.title} (${result.genre} @ ${result.bpm} BPM)!",
                        Toast.LENGTH_LONG
                    ).show()
                },
                onApplyArrangement = { arrangement ->
                    project.tracks.clear()
                    project.tracks.addAll(arrangement.arrangedTracks)
                    showAiStudioDialog = false
                    Toast.makeText(context, arrangement.explanation, Toast.LENGTH_LONG).show()
                },
                onDismiss = { showAiStudioDialog = false }
            )
        }

        if (showCoProducerArranger) {
            CoProducerArrangerOverlay(
                project = project,
                onApplyArrangement = { updatedProj ->
                    project.tracks.clear()
                    project.tracks.addAll(updatedProj.tracks)
                    showCoProducerArranger = false
                    projectVersion++
                    Toast.makeText(context, "System AI Co-Producer Arrangement Applied!", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showCoProducerArranger = false }
            )
        }

        if (showAiPatternAnalyzer) {
            AiPatternSuggestionsDialog(
                project = project,
                onApplySuggestion = { suggestion ->
                    val targetTrack = project.tracks.getOrNull(selectedTrackIndex) ?: project.tracks.firstOrNull()
                    if (targetTrack != null) {
                        targetTrack.stepCount = suggestion.recommendedStepCount
                        for (i in 0 until 64) {
                            targetTrack.steps[i] = if (i < suggestion.steps.size) suggestion.steps[i] else false
                        }
                        targetTrack.notes.clear()
                        targetTrack.notes.addAll(suggestion.notes)
                        projectVersion++
                        showAiPatternAnalyzer = false
                        Toast.makeText(context, "Applied pattern '${suggestion.title}' to track '${targetTrack.name}'!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Please create a track first!", Toast.LENGTH_SHORT).show()
                    }
                },
                onDismiss = { showAiPatternAnalyzer = false }
            )
        }

        if (showMasteringDialog) {
            MasteringSuiteDialog(
                config = project.masteringConfig,
                onConfigChange = { project.masteringConfig = it },
                onDismiss = { showMasteringDialog = false }
            )
        }

        if (showMasterMixer) {
            Dialog(
                onDismissRequest = { showMasterMixer = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                MasterMixerView(
                    project = project,
                    audioEngine = audioEngine,
                    onTrackVolumeChange = { idx, vol -> project.tracks.getOrNull(idx)?.volume = vol; projectVersion++ },
                    onTrackInputGainChange = { idx, gain -> project.tracks.getOrNull(idx)?.inputGain = gain; projectVersion++ },
                    onTrackPanChange = { idx, pan -> project.tracks.getOrNull(idx)?.pan = pan; projectVersion++ },
                    onMasterVolumeChange = {
                        project.masterVolume = it
                        audioEngine.masterVolume = it
                    }
                )
            }
        }

        if (showCloudSyncDialog) {
            CloudSyncDialog(
                project = project,
                firebaseManager = firebaseManager,
                onProjectSaved = {
                    Toast.makeText(context, "Saved to Cloud!", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showCloudSyncDialog = false }
            )
        }

        if (showProjectManagerDialog) {
            ArrangerProjectManagerPanel(
                currentProject = project,
                firebaseManager = firebaseManager,
                onLoadProject = { loadedProj ->
                    project.tracks.clear()
                    project.tracks.addAll(loadedProj.tracks)
                    project.bpm = loadedProj.bpm
                    project.title = loadedProj.title
                    project.masterVolume = loadedProj.masterVolume
                    project.masteringConfig = loadedProj.masteringConfig
                    project.isCloudSynced = loadedProj.isCloudSynced
                    projectVersion++
                    audioEngine.updateStemMuteSolo(project)
                    showProjectManagerDialog = false
                    Toast.makeText(context, "Loaded project '${loadedProj.title}'", Toast.LENGTH_SHORT).show()
                },
                onProjectSaved = {
                    projectVersion++
                },
                onClose = { showProjectManagerDialog = false }
            )
        }

        if (showExportDialog) {
            ExportDialog(
                project = project,
                selectedTrack = selectedTrack,
                onDismiss = { showExportDialog = false }
            )
        }

        if (showInstrumentSelectorForTrack != null) {
            val targetTrack = showInstrumentSelectorForTrack!!
            InstrumentSelectorDialog(
                currentTrack = targetTrack,
                audioEngine = audioEngine,
                onPresetSelected = { preset ->
                    InstrumentCatalog.applyPresetToTrack(targetTrack, preset)
                    Toast.makeText(context, "Loaded '${preset.name}' onto '${targetTrack.name}'", Toast.LENGTH_SHORT).show()
                    projectVersion++
                    showInstrumentSelectorForTrack = null
                },
                onDismiss = { showInstrumentSelectorForTrack = null }
            )
        }

        if (showInstrumentBrowserForNewTrack) {
            InstrumentSelectorDialog(
                currentTrack = null,
                audioEngine = audioEngine,
                onPresetSelected = { preset ->
                    val newTrack = TrackData(
                        name = preset.name,
                        type = preset.defaultTrackType,
                        colorHex = preset.colorHex,
                        synthPresetName = preset.name,
                        synthPresetCategory = preset.category.displayName,
                        synthWaveform = preset.waveform,
                        filterCutoffHz = preset.cutoffHz,
                        filterResonance = preset.resonance,
                        attackMs = preset.attackMs,
                        decayMs = preset.decayMs,
                        sustainLevel = preset.sustainLevel,
                        releaseMs = preset.releaseMs
                    )
                    project.tracks.add(newTrack)
                    selectedTrackIndex = project.tracks.size - 1
                    Toast.makeText(context, "Added track '${preset.name}'", Toast.LENGTH_SHORT).show()
                    projectVersion++
                    showInstrumentBrowserForNewTrack = false
                },
                onDismiss = { showInstrumentBrowserForNewTrack = false }
            )
        }

        if (showTutorialDialog) {
            FirstTimeTutorialDialog(
                onDismiss = { showTutorialDialog = false }
            )
        }

        if (showFactorySampleManager) {
            FactorySampleManagerDialog(
                audioEngine = audioEngine,
                onLoadSampleAsTrack = { sample ->
                    val newTrack = TrackData(
                        name = sample.name,
                        type = TrackType.AUDIO_IMPORT,
                        colorHex = sample.colorHex,
                        audioFileName = sample.name,
                        synthPresetName = sample.name
                    )
                    project.tracks.add(newTrack)
                    selectedTrackIndex = project.tracks.size - 1
                    Toast.makeText(context, "Loaded factory sample '${sample.name}' as new track!", Toast.LENGTH_SHORT).show()
                    projectVersion++
                },
                onDismiss = { showFactorySampleManager = false }
            )
        }
    }
}


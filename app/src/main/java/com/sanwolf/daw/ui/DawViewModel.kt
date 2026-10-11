package com.sanwolf.daw.ui

import android.app.Application
import android.util.Log
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import com.sanwolf.daw.audio.SanwolfAudioEngine
import com.sanwolf.daw.model.MidiNote
import com.sanwolf.daw.model.ModularGraph
import com.sanwolf.daw.model.ModularNode
import com.sanwolf.daw.model.ModularNodeType
import com.sanwolf.daw.model.NodeCable
import com.sanwolf.daw.model.ProjectData
import com.sanwolf.daw.model.SynthWaveform
import com.sanwolf.daw.model.TrackData
import com.sanwolf.daw.model.TrackType
import com.sanwolf.daw.model.UndoRedoManager
import com.sanwolf.daw.studio.RecordingEngine

/**
 * Holds the DAW session: the working project, the audio/recording engines and the project-file
 * state. It outlives configuration changes (activity recreation) instead of being rebuilt by the
 * composable, and releases the engines in [onCleared].
 *
 * State is exposed as Compose snapshot state so SanwolfDawApp can keep using
 * `var x by vm.xState` without a big-bang rewrite; more logic can move here over time.
 */
class DawViewModel(application: Application) : AndroidViewModel(application) {

    val audioEngine = SanwolfAudioEngine(application)
    val recordingEngine = RecordingEngine(application)
    val undoRedoManager = UndoRedoManager {}

    /** The working project. Mutated in place (the engine reads the same instance). */
    val project: ProjectData = createDefaultProject()

    /** Bumped after edits: drives undo snapshots and redraws. */
    val projectVersion = mutableIntStateOf(0)
    val selectedTrackIndex = mutableIntStateOf(0)

    // --- Local project file state ---
    // The open project's file id is remembered by ProjectSession (SharedPreferences), and
    // ProjectLifecycleEffects reopens it on launch before autosave starts, so a fresh session
    // never overwrites a saved file with the default project.
    val currentProjectFileId = mutableStateOf<String?>(null)
    val savedProjectJson = mutableStateOf<String?>(null)
    val isProjectDirty = mutableStateOf(false)
    val projectListVersion = mutableIntStateOf(0)
    val projectLoadKey = mutableIntStateOf(0)

    /** True once the last project was (tried to be) reopened in this ViewModel's lifetime. */
    var sessionRestoreAttempted = false

    init {
        audioEngine.currentProject = project
    }

    override fun onCleared() {
        super.onCleared()
        // Last chance to keep unsaved edits (the composable's ON_STOP flush normally did it already)
        runCatching {
            val pm = com.sanwolf.daw.audio.ProjectManager(getApplication<Application>())
            val json = pm.toJson(project)
            if (json != savedProjectJson.value) {
                val id = currentProjectFileId.value ?: pm.newFileId()
                ProjectSession.writeBlocking(pm, id, json).getOrThrow()
                ProjectSession.setLastProjectId(getApplication<Application>(), id)
            }
        }.onFailure { Log.e(TAG, "Final project save failed", it) }
        runCatching { recordingEngine.release() }.onFailure { Log.e(TAG, "Recording engine release failed", it) }
        runCatching { audioEngine.release() }.onFailure { Log.e(TAG, "Audio engine release failed", it) }
    }

    private fun createDefaultProject(): ProjectData {
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
                name = "Closed Hi-Hat",
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
                name = "Grand Piano",
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
        return p
    }

    private companion object {
        const val TAG = "DawViewModel"
    }
}

package com.example.model

import java.util.UUID

enum class TrackType {
    SYNTH,
    DRUM_MACHINE,
    MODULAR_SYNTH,
    AUDIO_IMPORT,
    RISER_TEXTURE
}

enum class SynthWaveform {
    SINE,
    SAWTOOTH,
    SQUARE,
    TRIANGLE,
    NOISE
}

data class MidiNote(
    val id: String = UUID.randomUUID().toString(),
    var pitch: Int = 60, // MIDI note number 0-127 (60 = C4)
    var startBeat: Double = 0.0,
    var lengthBeats: Double = 1.0,
    var velocity: Float = 0.8f
)

data class AutomationPoint(
    var beat: Double,
    var value: Float // Normalized 0f..1f
)

data class AutomationLane(
    val id: String = UUID.randomUUID().toString(),
    var targetParam: String = "Filter Cutoff",
    val points: MutableList<AutomationPoint> = mutableListOf(
        AutomationPoint(0.0, 0.25f),
        AutomationPoint(4.0, 0.85f),
        AutomationPoint(8.0, 0.40f),
        AutomationPoint(12.0, 0.95f),
        AutomationPoint(16.0, 0.30f)
    )
) {
    fun getValueAtBeat(beat: Double, defaultValue: Float = 0.5f): Float {
        if (points.isEmpty()) return defaultValue
        if (points.size == 1) return points[0].value.coerceIn(0f, 1f)

        val sorted = points.sortedBy { it.beat }
        if (beat <= sorted.first().beat) return sorted.first().value.coerceIn(0f, 1f)
        if (beat >= sorted.last().beat) return sorted.last().value.coerceIn(0f, 1f)

        for (i in 0 until sorted.size - 1) {
            val p1 = sorted[i]
            val p2 = sorted[i + 1]
            if (beat >= p1.beat && beat <= p2.beat) {
                val span = p2.beat - p1.beat
                if (span <= 0.0001) return p1.value.coerceIn(0f, 1f)
                val t = ((beat - p1.beat) / span).toFloat().coerceIn(0f, 1f)
                return (p1.value + (p2.value - p1.value) * t).coerceIn(0f, 1f)
            }
        }
        return defaultValue
    }

    fun toDisplayValue(normalized: Float): String {
        val norm = normalized.coerceIn(0f, 1f)
        return when {
            targetParam.contains("Cutoff", ignoreCase = true) -> {
                val hz = 100f + norm * 11900f
                if (hz >= 1000f) String.format("%.1f kHz", hz / 1000f) else "${hz.toInt()} Hz"
            }
            targetParam.contains("Resonance", ignoreCase = true) -> {
                String.format("%.2f Q", 0.5f + norm * 3.5f)
            }
            targetParam.contains("Volume", ignoreCase = true) -> {
                String.format("%.0f%%", norm * 100f)
            }
            targetParam.contains("Pan", ignoreCase = true) -> {
                when {
                    norm < 0.47f -> "L ${((0.5f - norm) * 200).toInt()}%"
                    norm > 0.53f -> "R ${((norm - 0.5f) * 200).toInt()}%"
                    else -> "CENTER"
                }
            }
            targetParam.contains("Attack", ignoreCase = true) -> {
                "${(2f + norm * 498f).toInt()} ms"
            }
            targetParam.contains("Decay", ignoreCase = true) -> {
                "${(10f + norm * 990f).toInt()} ms"
            }
            targetParam.contains("Release", ignoreCase = true) -> {
                "${(20f + norm * 1480f).toInt()} ms"
            }
            else -> "${(norm * 100).toInt()}%"
        }
    }

    fun applyPresetShape(shapeName: String, totalBeats: Int = 16) {
        points.clear()
        when (shapeName) {
            "Ramp Up" -> {
                points.add(AutomationPoint(0.0, 0.1f))
                points.add(AutomationPoint(totalBeats.toDouble(), 0.95f))
            }
            "Ramp Down" -> {
                points.add(AutomationPoint(0.0, 0.95f))
                points.add(AutomationPoint(totalBeats.toDouble(), 0.1f))
            }
            "Sine Wave" -> {
                val step = 1.0
                var b = 0.0
                while (b <= totalBeats) {
                    val angle = (b / 4.0) * 2.0 * Math.PI
                    val v = (0.5 + 0.4 * Math.sin(angle)).toFloat()
                    points.add(AutomationPoint(b, v))
                    b += step
                }
            }
            "Sidechain Duck" -> {
                var b = 0.0
                while (b < totalBeats) {
                    points.add(AutomationPoint(b, 0.15f))
                    points.add(AutomationPoint(b + 0.6, 0.9f))
                    points.add(AutomationPoint(b + 0.95, 0.95f))
                    b += 1.0
                }
                points.add(AutomationPoint(totalBeats.toDouble(), 0.95f))
            }
            "Build-up Sweep" -> {
                points.add(AutomationPoint(0.0, 0.15f))
                points.add(AutomationPoint(totalBeats * 0.5, 0.35f))
                points.add(AutomationPoint(totalBeats * 0.75, 0.65f))
                points.add(AutomationPoint(totalBeats * 0.9, 0.85f))
                points.add(AutomationPoint(totalBeats.toDouble(), 1.0f))
            }
            "Flat Center" -> {
                points.add(AutomationPoint(0.0, 0.5f))
                points.add(AutomationPoint(totalBeats.toDouble(), 0.5f))
            }
            else -> {
                points.add(AutomationPoint(0.0, 0.3f))
                points.add(AutomationPoint(totalBeats * 0.5, 0.8f))
                points.add(AutomationPoint(totalBeats.toDouble(), 0.4f))
            }
        }
    }
}

enum class ModularNodeType {
    OSCILLATOR,
    FILTER,
    ADSR_ENV,
    LFO,
    STEREO_DELAY,
    SPACE_REVERB,
    WOLF_DRIVE,
    OUTPUT
}

data class ModularNode(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: ModularNodeType,
    var x: Float,
    var y: Float,
    val params: MutableMap<String, Float> = mutableMapOf()
)

data class NodeCable(
    val id: String = UUID.randomUUID().toString(),
    val fromNodeId: String,
    val fromPort: String,
    val toNodeId: String,
    val toPort: String,
    val colorHex: Long = 0xFFD9A441
)

data class ModularGraph(
    val nodes: MutableList<ModularNode> = mutableListOf(),
    val cables: MutableList<NodeCable> = mutableListOf()
)

data class VstPlugin(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val category: String, // "Filter", "Dynamics", "Space", "Saturation"
    var enabled: Boolean = true,
    var wetDry: Float = 0.5f,
    val params: MutableMap<String, Float> = mutableMapOf(),
    var isSidechainEnabled: Boolean = false,
    var sidechainSourceTrackId: String? = null
)

data class TrackData(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var type: TrackType,
    var colorHex: Long,
    var volume: Float = 0.8f,
    var inputGain: Float = 1.0f,
    var pan: Float = 0f,
    var muted: Boolean = false,
    var solo: Boolean = false,
    var stepCount: Int = 16, // Supports 16, 20, 24, 32, 48, 64 steps and polyrhythms
    val steps: BooleanArray = BooleanArray(64),
    val stepVelocities: FloatArray = FloatArray(64) { 0.8f },
    val notes: MutableList<MidiNote> = mutableListOf(),
    val fxChain: MutableList<VstPlugin> = mutableListOf(),
    var modularGraph: ModularGraph? = null,
    val automationLanes: MutableList<AutomationLane> = mutableListOf(),
    var audioUri: String? = null,
    var audioFileName: String? = null,
    var waveformPeaks: FloatArray? = null,
    var synthPresetName: String = "Grand Piano",
    var synthPresetCategory: String = "Keys & Piano",
    var synthWaveform: SynthWaveform = SynthWaveform.TRIANGLE,
    var filterCutoffHz: Float = 6000f,
    var filterResonance: Float = 1.0f,
    var attackMs: Float = 5f,
    var decayMs: Float = 300f,
    var sustainLevel: Float = 0.4f,
    var releaseMs: Float = 400f
)

data class MasteringConfig(
    var enabled: Boolean = true,
    var targetLufs: Float = -14f,
    var lowGainDb: Float = 1.5f,
    var midLowGainDb: Float = -0.5f,
    var midGainDb: Float = 0.0f,
    var highMidGainDb: Float = 1.0f,
    var highGainDb: Float = 2.0f,
    var stereoWidth: Float = 1.25f,
    var compressorThresholdDb: Float = -12f,
    var limiterCeilingDb: Float = -0.3f,
    var profileName: String = "Modern EDM / Trap Punch"
)

data class MidiMapping(
    val id: String = UUID.randomUUID().toString(),
    var midiCc: Int,
    var targetParam: String, // e.g., "Filter Cutoff", "Track Volume"
    var trackId: String? = null // Null if global parameter
)

data class ProjectData(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "WOLF_NIGHT_SESSION_01",
    var bpm: Int = 128,
    var swing: Float = 0.15f,
    var timeSignatureNumerator: Int = 4,
    var timeSignatureDenominator: Int = 4,
    var masterVolume: Float = 0.85f,
    val tracks: MutableList<TrackData> = mutableListOf(),
    val midiMappings: MutableList<MidiMapping> = mutableListOf(),
    var masteringConfig: MasteringConfig = MasteringConfig(),
    var collaborationRoomId: String? = null,
    var isCloudSynced: Boolean = false,
    var lastSavedTimestamp: Long = System.currentTimeMillis(),
    // Song info / metadata (added later: every field has a default so older saved
    // projects and undo snapshots without these keys still deserialize cleanly).
    var artist: String = "",
    var musicalKey: String = "",
    var genre: String = "",
    var songNotes: String = ""
)

enum class InstrumentCategory(val displayName: String) {
    KEYS_PAD("Keys & Piano"),
    ORCHESTRAL_STRINGS("Strings & Orchestral"),
    BRASS_WINDS("Brass & Winds"),
    BASS_808("Basses & 808s"),
    SYNTH_LEAD("Leads & Synths"),
    DRUM_KIT("Drum Kits"),
    WORLD_PERCUSSION("World Percussion"),
    ATMOSPHERE_FX("FX & Textures")
}

data class InstrumentPreset(
    val id: String,
    val name: String,
    val category: InstrumentCategory,
    val waveform: SynthWaveform,
    val cutoffHz: Float = 2500f,
    val resonance: Float = 1.2f,
    val attackMs: Float = 15f,
    val decayMs: Float = 120f,
    val sustainLevel: Float = 0.65f,
    val releaseMs: Float = 180f,
    val description: String,
    val defaultTrackType: TrackType = TrackType.SYNTH,
    val colorHex: Long = 0xFF00E5FF
)

object InstrumentCatalog {
    val presets: List<InstrumentPreset> = InstrumentCollection.allPresets
    private val legacyPresets: List<InstrumentPreset> = listOf(
        // Leads & Synths
        InstrumentPreset(
            id = "cyber_lead",
            name = "Cyber Lead",
            category = InstrumentCategory.SYNTH_LEAD,
            waveform = SynthWaveform.SAWTOOTH,
            cutoffHz = 3800f,
            resonance = 1.3f,
            attackMs = 10f,
            decayMs = 120f,
            sustainLevel = 0.8f,
            releaseMs = 150f,
            description = "Punchy cutting saw lead for aggressive hooks and EDM drops",
            colorHex = 0xFF00E5FF
        ),
        InstrumentPreset(
            id = "analog_pluck",
            name = "Neon Pluck",
            category = InstrumentCategory.SYNTH_LEAD,
            waveform = SynthWaveform.SQUARE,
            cutoffHz = 2200f,
            resonance = 1.6f,
            attackMs = 5f,
            decayMs = 90f,
            sustainLevel = 0.15f,
            releaseMs = 100f,
            description = "Snappy percussive square pluck ideal for arpeggios and fast runs",
            colorHex = 0xFFFFD700
        ),
        InstrumentPreset(
            id = "acid_303",
            name = "Acid 303 Resonator",
            category = InstrumentCategory.SYNTH_LEAD,
            waveform = SynthWaveform.SAWTOOTH,
            cutoffHz = 1600f,
            resonance = 2.4f,
            attackMs = 8f,
            decayMs = 140f,
            sustainLevel = 0.7f,
            releaseMs = 160f,
            description = "Iconic resonant acid lead with squelchy filter sweeps",
            colorHex = 0xFF76FF03
        ),
        InstrumentPreset(
            id = "pure_sine_flute",
            name = "Sine Solo Flute",
            category = InstrumentCategory.SYNTH_LEAD,
            waveform = SynthWaveform.SINE,
            cutoffHz = 5000f,
            resonance = 1.0f,
            attackMs = 35f,
            decayMs = 100f,
            sustainLevel = 0.9f,
            releaseMs = 220f,
            description = "Silky pure sine wave lead for emotional melodies and top lines",
            colorHex = 0xFF00B0FF
        ),
        InstrumentPreset(
            id = "supersaw_arena",
            name = "SuperSaw Anthem",
            category = InstrumentCategory.SYNTH_LEAD,
            waveform = SynthWaveform.SAWTOOTH,
            cutoffHz = 4200f,
            resonance = 1.4f,
            attackMs = 12f,
            decayMs = 180f,
            sustainLevel = 0.85f,
            releaseMs = 250f,
            description = "Massive bright stadium supersaw with full stereo presence",
            colorHex = 0xFFFF007F
        ),
        InstrumentPreset(
            id = "nostalgic_8bit_chip",
            name = "Nostalgic 8-Bit Chip",
            category = InstrumentCategory.SYNTH_LEAD,
            waveform = SynthWaveform.SQUARE,
            cutoffHz = 4000f,
            resonance = 1.0f,
            attackMs = 2f,
            decayMs = 80f,
            sustainLevel = 0.7f,
            releaseMs = 80f,
            description = "Retro gaming sound with snappy transients and clean pulse modulation",
            colorHex = 0xFF00E676
        ),
        InstrumentPreset(
            id = "afro_house_triad",
            name = "Afro House Pluck Lead",
            category = InstrumentCategory.SYNTH_LEAD,
            waveform = SynthWaveform.SAWTOOTH,
            cutoffHz = 2800f,
            resonance = 1.5f,
            attackMs = 6f,
            decayMs = 130f,
            sustainLevel = 0.3f,
            releaseMs = 140f,
            description = "Punchy resonant synth pluck designed for Afro House syncopated melodic loops",
            colorHex = 0xFFFFD700
        ),

        // Basses & 808s
        InstrumentPreset(
            id = "deep_808",
            name = "808 Sub Boom",
            category = InstrumentCategory.BASS_808,
            waveform = SynthWaveform.SINE,
            cutoffHz = 450f,
            resonance = 1.1f,
            attackMs = 5f,
            decayMs = 250f,
            sustainLevel = 0.75f,
            releaseMs = 300f,
            description = "Ground-shaking sine sub bass with clean harmonic punch",
            colorHex = 0xFFFF5722
        ),
        InstrumentPreset(
            id = "reese_dark",
            name = "Reese Dark Sub",
            category = InstrumentCategory.BASS_808,
            waveform = SynthWaveform.SAWTOOTH,
            cutoffHz = 750f,
            resonance = 1.8f,
            attackMs = 20f,
            decayMs = 200f,
            sustainLevel = 0.9f,
            releaseMs = 350f,
            description = "Menacing, thick detuned saw bass for DnB, Trap, and Darkwave",
            colorHex = 0xFFD500F9
        ),
        InstrumentPreset(
            id = "slap_synth_bass",
            name = "Funk Slap Bass",
            category = InstrumentCategory.BASS_808,
            waveform = SynthWaveform.SQUARE,
            cutoffHz = 1200f,
            resonance = 1.9f,
            attackMs = 5f,
            decayMs = 110f,
            sustainLevel = 0.3f,
            releaseMs = 120f,
            description = "Snappy funk synth bass with fast attack and tight transient",
            colorHex = 0xFFFFAB00
        ),
        InstrumentPreset(
            id = "sub_zero_tri",
            name = "Sub Zero Triangle",
            category = InstrumentCategory.BASS_808,
            waveform = SynthWaveform.TRIANGLE,
            cutoffHz = 650f,
            resonance = 1.2f,
            attackMs = 10f,
            decayMs = 180f,
            sustainLevel = 0.85f,
            releaseMs = 220f,
            description = "Warm triangle low-end weight that sits cleanly below any mix",
            colorHex = 0xFF2979FF
        ),
        InstrumentPreset(
            id = "amapiano_log_bass",
            name = "Amapiano Log Bass",
            category = InstrumentCategory.BASS_808,
            waveform = SynthWaveform.SINE,
            cutoffHz = 1200f,
            resonance = 1.4f,
            attackMs = 8f,
            decayMs = 120f,
            sustainLevel = 0.5f,
            releaseMs = 150f,
            description = "Resonant woody FM log bass that delivers iconic Amapiano and Afro House punch",
            colorHex = 0xFFFFAB00
        ),
        InstrumentPreset(
            id = "gqom_heavy_sub",
            name = "Gqom Heavy Sub",
            category = InstrumentCategory.BASS_808,
            waveform = SynthWaveform.SAWTOOTH,
            cutoffHz = 800f,
            resonance = 1.8f,
            attackMs = 12f,
            decayMs = 200f,
            sustainLevel = 0.8f,
            releaseMs = 250f,
            description = "Aggressive detuned and saturated bass with heavy Durban Gqom punch",
            colorHex = 0xFFD500F9
        ),

        // Keys & Pads
        InstrumentPreset(
            id = "dreamy_rhodes_tines",
            name = "Dreamy Rhodes Tines",
            category = InstrumentCategory.KEYS_PAD,
            waveform = SynthWaveform.TRIANGLE,
            cutoffHz = 3200f,
            resonance = 1.3f,
            attackMs = 15f,
            decayMs = 250f,
            sustainLevel = 0.6f,
            releaseMs = 300f,
            description = "Vintage FM electric piano with shimmering metallic tine chime attack",
            colorHex = 0xFF80D8FF
        ),
        InstrumentPreset(
            id = "retro_brass_pad",
            name = "Classic Retro Brass",
            category = InstrumentCategory.KEYS_PAD,
            waveform = SynthWaveform.SAWTOOTH,
            cutoffHz = 1800f,
            resonance = 1.2f,
            attackMs = 110f,
            decayMs = 200f,
            sustainLevel = 0.75f,
            releaseMs = 280f,
            description = "Warm analog synth brass swell for nostalgic chords and synthwave backings",
            colorHex = 0xFFFF5722
        ),
        InstrumentPreset(
            id = "vintage_rhodes",
            name = "Vintage Rhodes E-Piano",
            category = InstrumentCategory.KEYS_PAD,
            waveform = SynthWaveform.TRIANGLE,
            cutoffHz = 2400f,
            resonance = 1.1f,
            attackMs = 10f,
            decayMs = 220f,
            sustainLevel = 0.55f,
            releaseMs = 200f,
            description = "Warm neo-soul and lofi electric piano chime with mellow bell attack",
            colorHex = 0xFFFFC107
        ),
        InstrumentPreset(
            id = "lush_pad",
            name = "Lush Ambient Pad",
            category = InstrumentCategory.KEYS_PAD,
            waveform = SynthWaveform.SINE,
            cutoffHz = 1900f,
            resonance = 1.2f,
            attackMs = 220f,
            decayMs = 300f,
            sustainLevel = 0.9f,
            releaseMs = 600f,
            description = "Cinematic slow-swell pad with warm analog drift and lush tail",
            colorHex = 0xFF1DE9B6
        ),
        InstrumentPreset(
            id = "glass_bell",
            name = "Glass Crystal Bell",
            category = InstrumentCategory.KEYS_PAD,
            waveform = SynthWaveform.TRIANGLE,
            cutoffHz = 3800f,
            resonance = 1.5f,
            attackMs = 5f,
            decayMs = 300f,
            sustainLevel = 0.4f,
            releaseMs = 450f,
            description = "Chamber bell sound with shimmering crystalline decay",
            colorHex = 0xFF80D8FF
        ),
        InstrumentPreset(
            id = "tape_organ",
            name = "Lo-Fi Tape Organ",
            category = InstrumentCategory.KEYS_PAD,
            waveform = SynthWaveform.SQUARE,
            cutoffHz = 1600f,
            resonance = 1.1f,
            attackMs = 25f,
            decayMs = 150f,
            sustainLevel = 0.9f,
            releaseMs = 180f,
            description = "Vintage combo organ with mellow saturation and nostalgic warmth",
            colorHex = 0xFFFF9100
        ),

        // Drum Kits
        InstrumentPreset(
            id = "trap_808_kit",
            name = "Trap 808 Heavy Kit",
            category = InstrumentCategory.DRUM_KIT,
            waveform = SynthWaveform.SAWTOOTH,
            defaultTrackType = TrackType.DRUM_MACHINE,
            description = "Heavy 808 sub kick, snappy snare, clap, and rolled hi-hats",
            colorHex = 0xFFFF1744
        ),
        InstrumentPreset(
            id = "boom_bap_kit",
            name = "Boom Bap Vintage Kit",
            category = InstrumentCategory.DRUM_KIT,
            waveform = SynthWaveform.SQUARE,
            defaultTrackType = TrackType.DRUM_MACHINE,
            description = "Classic 90s sampled acoustic drums with dusty punch and warmth",
            colorHex = 0xFFFF9100
        ),
        InstrumentPreset(
            id = "amapiano_kit",
            name = "Amapiano Log & Shaker",
            category = InstrumentCategory.DRUM_KIT,
            waveform = SynthWaveform.TRIANGLE,
            defaultTrackType = TrackType.DRUM_MACHINE,
            description = "South African log drum bass hits, shakers, and rim percussions",
            colorHex = 0xFFFFD700
        ),
        InstrumentPreset(
            id = "house_909_kit",
            name = "Electro 909 Dance Kit",
            category = InstrumentCategory.DRUM_KIT,
            waveform = SynthWaveform.SAWTOOTH,
            defaultTrackType = TrackType.DRUM_MACHINE,
            description = "Punchy 909 4-on-the-floor kick, open hat, and crisp handclap",
            colorHex = 0xFF00E676
        ),

        // FX & Atmosphere
        InstrumentPreset(
            id = "cyber_texture",
            name = "Cyberpunk Texture",
            category = InstrumentCategory.ATMOSPHERE_FX,
            waveform = SynthWaveform.NOISE,
            cutoffHz = 1200f,
            resonance = 1.5f,
            attackMs = 120f,
            sustainLevel = 0.8f,
            releaseMs = 500f,
            defaultTrackType = TrackType.RISER_TEXTURE,
            description = "Evolving industrial noise texture and dystopian sci-fi drones",
            colorHex = 0xFF76FF03
        ),
        InstrumentPreset(
            id = "noise_riser",
            name = "White Noise Riser",
            category = InstrumentCategory.ATMOSPHERE_FX,
            waveform = SynthWaveform.NOISE,
            cutoffHz = 3500f,
            resonance = 1.8f,
            attackMs = 400f,
            sustainLevel = 0.95f,
            releaseMs = 200f,
            defaultTrackType = TrackType.RISER_TEXTURE,
            description = "Rising white noise tension builder for energetic drop transitions",
            colorHex = 0xFFE040FB
        )
    )

    fun findPresetById(id: String): InstrumentPreset? = presets.find { it.id == id }

    fun findPresetByName(name: String): InstrumentPreset? = presets.find {
        it.name.equals(name, ignoreCase = true) || it.id.equals(name, ignoreCase = true)
    }

    fun applyPresetToTrack(track: TrackData, preset: InstrumentPreset, renameTrack: Boolean = true) {
        track.synthPresetName = preset.name
        track.synthPresetCategory = preset.category.displayName
        track.synthWaveform = preset.waveform
        track.filterCutoffHz = preset.cutoffHz
        track.filterResonance = preset.resonance
        track.attackMs = preset.attackMs
        track.decayMs = preset.decayMs
        track.sustainLevel = preset.sustainLevel
        track.releaseMs = preset.releaseMs
        track.colorHex = preset.colorHex
        if (preset.category == InstrumentCategory.DRUM_KIT || preset.category == InstrumentCategory.WORLD_PERCUSSION) {
            track.type = TrackType.DRUM_MACHINE
        } else if (track.type == TrackType.DRUM_MACHINE && preset.defaultTrackType == TrackType.SYNTH) {
            track.type = TrackType.SYNTH
        }
        if (renameTrack) {
            track.name = preset.name
        }
    }
}

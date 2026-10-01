package com.example.audio

import com.example.drum.DrumEngine
import com.example.melody.MelodyEngine
import com.example.mixer.MixerEngine
import com.example.mixer.MixerRenderer
import com.example.piano.PianoEngine

/**
 * Executes the authoritative SANWOLF Audio Routing Architecture:
 *
 * USER SELECTS INSTRUMENT
 *         ↓
 *   InstrumentDefinition
 *         ↓
 *    instrument ID
 *         ↓
 *   InstrumentLibrary
 *         ↓
 * correct InstrumentSample
 *         ↓
 *   appropriate engine (DrumEngine, PianoEngine, MelodyEngine)
 *         ↓
 *    MixerAudioTrack
 *         ↓
 *     MixerChannel
 *         ↓
 *     MixerEngine
 *         ↓
 *    MixerRenderer
 *         ↓
 *     AudioEngine
 *         ↓
 *       OUTPUT
 */
class InstrumentAudioRoutingController(
    val audioEngine: SanwolfAudioEngine,
    val mixerEngine: MixerEngine,
    val mixerRenderer: MixerRenderer
) {
    val drumEngine = DrumEngine(audioEngine, mixerEngine)
    val pianoEngine = PianoEngine(audioEngine, mixerEngine)
    val melodyEngine = MelodyEngine(audioEngine, mixerEngine)

    fun routeAndPlayInstrument(
        instrumentId: String,
        pitch: Int = 60,
        velocity: Float = 0.8f,
        durationSec: Float = 0.4f
    ): RoutedAudioResult {
        // 1. Retrieve InstrumentDefinition by ID from authoritative InstrumentLibrary
        val definition: InstrumentDefinition = InstrumentLibrary.getById(instrumentId)

        // 2. Resolve correct InstrumentSample mapping
        val instrumentSample = definition.sample ?: InstrumentSample(
            sampleId = definition.id,
            name = definition.name,
            rootPitch = pitch,
            samplePath = "samples/${definition.id}_${pitch}.wav",
            volume = velocity,
            durationSec = durationSec
        )

        // 3. Register or fetch MixerAudioTrack in MixerAudioTrackManager
        val mixerAudioTrack = mixerEngine.trackManager.getOrCreateTrack(
            id = "track_${definition.id}",
            name = definition.name,
            instrumentId = definition.id,
            category = definition.category.displayName,
            volume = velocity
        )

        // 4. Connect to MixerChannel in MixerEngine
        val mixerChannel = mixerEngine.getOrCreateChannel(
            instrumentId = definition.id,
            trackName = definition.name,
            category = definition.category.displayName,
            sampleSource = "synthesized"
        )
        mixerChannel.volume = velocity

        // 5. Route to appropriate dedicated engine based on instrument category & properties
        val routeSuccess = when {
            definition.isDrum || definition.category == InstrumentCategory.DRUMS || definition.category == InstrumentCategory.WORLD_PERCUSSION || definition.category == InstrumentCategory.SOUND_EFFECTS_TEXTURES -> {
                drumEngine.playDrum(definition.id, velocity)
                true
            }
            definition.category == InstrumentCategory.KEYS_PIANO || definition.id.startsWith("keys.") -> {
                val res = pianoEngine.playNote(pitch, definition.id, velocity, durationSec)
                res.success
            }
            else -> {
                val res = melodyEngine.playPitch(pitch, definition.id, velocity, durationSec.toDouble())
                res.success
            }
        }

        return RoutedAudioResult(
            success = routeSuccess,
            instrumentName = definition.name,
            routedTrackId = mixerAudioTrack.id,
            channelId = mixerChannel.channelId
        )
    }
}

data class RoutedAudioResult(
    val success: Boolean,
    val instrumentName: String,
    val routedTrackId: String,
    val channelId: String
)

package com.sanwolf.daw.piano

import com.sanwolf.daw.audio.InstrumentLibrary
import com.sanwolf.daw.audio.InstrumentSample
import com.sanwolf.daw.audio.SanwolfAudioEngine
import com.sanwolf.daw.mixer.MixerEngine
import com.sanwolf.daw.model.TrackData

/**
 * Enterprise PianoEngine in SANWOLF Audio Studio.
 * Follows authoritative routing:
 * PianoActivity -> PianoEngine -> InstrumentDefinition -> PianoRenderer -> PianoMixerBridge -> MixerEngine
 */
class PianoEngine(
    val audioEngine: SanwolfAudioEngine,
    val mixerEngine: MixerEngine? = null
) {
    val pianoRenderer = PianoRenderer()
    val mixerBridge = PianoMixerBridge(audioEngine, mixerEngine ?: MixerEngine(audioEngine))

    fun playNote(
        pitch: Int = 60,
        instrumentId: String = "keys.grand.piano",
        velocity: Float = 0.8f,
        durationSec: Float = 0.5f,
        track: TrackData? = null
    ): PianoMixerResult {
        val definition = InstrumentLibrary.getById(instrumentId)
        val sample = definition.sample ?: InstrumentSample(
            sampleId = definition.id,
            name = definition.name,
            rootPitch = pitch,
            volume = velocity
        )
        return mixerBridge.sendTrack(definition.id, track, pitch, velocity, durationSec)
    }

    // Overload for backward compatibility
    fun playNote(pitch: Int, track: TrackData?) {
        val instrumentId = track?.synthPresetName ?: "keys.grand.piano"
        playNote(pitch, instrumentId, track?.volume ?: 0.8f, 0.5f, track)
    }
}

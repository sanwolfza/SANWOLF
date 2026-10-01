package com.example.melody

import com.example.audio.InstrumentLibrary
import com.example.audio.InstrumentSample
import com.example.audio.SanwolfAudioEngine
import com.example.mixer.MixerEngine
import com.example.model.TrackData

/**
 * Enterprise MelodyEngine in SANWOLF Audio Studio.
 * Follows authoritative routing:
 * MelodyActivity -> MelodyEngine -> MelodyNote -> InstrumentSelection -> MelodyRenderer -> MelodyMixerBridge -> MixerEngine
 */
class MelodyEngine(
    val audioEngine: SanwolfAudioEngine,
    val mixerEngine: MixerEngine? = null
) {
    val melodyRenderer = MelodyRenderer()
    val mixerBridge = MelodyMixerBridge(audioEngine, mixerEngine ?: MixerEngine(audioEngine))

    fun playNote(
        note: MelodyNote,
        instrumentId: String = "synth.analog.lead",
        track: TrackData? = null
    ): MelodyMixerResult {
        val definition = InstrumentLibrary.getById(instrumentId)
        val sample = definition.sample ?: InstrumentSample(
            sampleId = definition.id,
            name = definition.name,
            rootPitch = note.pitch,
            volume = note.velocity
        )
        return mixerBridge.routeMelody(definition.id, note, track)
    }

    fun playPitch(
        pitch: Int,
        instrumentId: String = "synth.analog.lead",
        velocity: Float = 0.8f,
        durationBeats: Double = 1.0,
        track: TrackData? = null
    ): MelodyMixerResult {
        val note = MelodyNote(pitch = pitch, velocity = velocity, lengthBeats = durationBeats)
        return playNote(note, instrumentId, track)
    }

    // Backward compatibility overload
    fun playMelodyNote(pitch: Int) {
        playPitch(pitch)
    }
}

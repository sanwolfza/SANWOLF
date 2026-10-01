package com.example.drum

import com.example.audio.InstrumentLibrary
import com.example.audio.InstrumentSample
import com.example.audio.SanwolfAudioEngine
import com.example.mixer.MixerEngine

/**
 * Enterprise DrumEngine in SANWOLF Audio Studio.
 * Follows authoritative routing:
 * DrumActivity / Sequencer -> DrumEngine -> instrument ID -> InstrumentLibrary -> drum sample -> DrumRenderer -> DrumMixerBridge -> MixerEngine
 */
class DrumEngine(
    val audioEngine: SanwolfAudioEngine,
    val mixerEngine: MixerEngine? = null
) {
    val drumRenderer = DrumRenderer()
    val mixerBridge = DrumMixerBridge(audioEngine, mixerEngine ?: MixerEngine(audioEngine))

    fun playDrum(instrumentId: String, velocity: Float = 0.8f, pan: Float = 0f) {
        val definition = InstrumentLibrary.getById(instrumentId)
        val sample = definition.sample ?: InstrumentSample(
            sampleId = definition.id,
            name = definition.name,
            samplePath = "samples/${definition.id}.wav",
            volume = velocity
        )
        // Connect to mixer bridge and trigger playback
        mixerBridge.sendToMixer(definition.id, velocity, pan)
    }
}

package com.sanwolf.daw.drum

import com.sanwolf.daw.audio.InstrumentLibrary
import com.sanwolf.daw.audio.InstrumentSample
import com.sanwolf.daw.audio.SanwolfAudioEngine
import com.sanwolf.daw.mixer.MixerEngine

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

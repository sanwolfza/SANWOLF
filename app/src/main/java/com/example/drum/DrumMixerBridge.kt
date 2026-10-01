package com.example.drum

import com.example.audio.InstrumentLibrary
import com.example.audio.SanwolfAudioEngine
import com.example.mixer.MixerEngine
import com.example.model.TrackData

/**
 * Bridges DrumEngine into MixerEngine for per-instrument channel management and real-time playback.
 */
class DrumMixerBridge(
    private val audioEngine: SanwolfAudioEngine,
    private val mixerEngine: MixerEngine
) {
    fun sendToMixer(instrumentId: String, velocity: Float = 0.8f, pan: Float = 0f): DrumMixerResult {
        val definition = InstrumentLibrary.getById(instrumentId)
        val channel = mixerEngine.getOrCreateChannel(
            instrumentId = definition.id,
            trackName = definition.name,
            category = definition.category.displayName,
            sampleSource = "synthesized"
        )
        channel.volume = velocity
        channel.pan = pan

        val effectiveVol = mixerEngine.getEffectiveVolume(channel.channelId)
        val effectivePan = mixerEngine.getEffectivePan(channel.channelId)

        if (mixerEngine.isChannelAudible(channel.channelId)) {
            audioEngine.triggerDrumSound(
                drumName = definition.id,
                velocity = effectiveVol,
                pan = effectivePan,
                definition = definition
            )
        }
        return DrumMixerResult(success = true, message = "Routed ${definition.name} to mixer")
    }

    fun sendToMixer(instrumentId: String, track: TrackData) {
        sendToMixer(instrumentId, track.volume, track.pan)
    }
}

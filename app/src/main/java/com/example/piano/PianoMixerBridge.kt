package com.example.piano

import com.example.audio.InstrumentLibrary
import com.example.audio.SanwolfAudioEngine
import com.example.mixer.MixerEngine
import com.example.model.TrackData
import com.example.model.TrackType

/**
 * Bridges PianoEngine into MixerEngine for per-instrument channel routing and real-time playback.
 */
class PianoMixerBridge(
    private val audioEngine: SanwolfAudioEngine,
    private val mixerEngine: MixerEngine
) {
    fun sendTrack(
        instrumentId: String,
        track: TrackData? = null,
        pitch: Int = 60,
        velocity: Float = 0.8f,
        durationSec: Float = 0.5f
    ): PianoMixerResult {
        val definition = InstrumentLibrary.getById(instrumentId)
        val channel = mixerEngine.getOrCreateChannel(
            instrumentId = definition.id,
            trackName = definition.name,
            category = definition.category.displayName,
            sampleSource = "synthesized"
        )

        val effVol = mixerEngine.getEffectiveVolume(channel.channelId) * velocity
        val effPan = mixerEngine.getEffectivePan(channel.channelId)

        val targetTrack = track ?: TrackData(
            name = definition.name,
            type = TrackType.SYNTH,
            colorHex = 0xFFFFB300,
            synthPresetName = definition.name,
            synthPresetCategory = definition.category.displayName,
            synthWaveform = definition.defaultWaveform,
            filterCutoffHz = definition.filterCutoffHz,
            filterResonance = definition.filterResonance,
            attackMs = definition.attackMs,
            decayMs = definition.decayMs,
            sustainLevel = definition.sustainLevel,
            releaseMs = definition.releaseMs
        )

        if (mixerEngine.isChannelAudible(channel.channelId)) {
            audioEngine.triggerTrackNote(
                track = targetTrack,
                pitch = pitch,
                durationSec = durationSec,
                velocity = effVol,
                pan = effPan,
                cutoff = definition.filterCutoffHz,
                resonance = definition.filterResonance,
                attackMs = definition.attackMs,
                releaseMs = definition.releaseMs
            )
        }

        return PianoMixerResult(success = true, routedTrackId = channel.channelId)
    }
}

package com.example.melody

import com.example.audio.InstrumentLibrary
import com.example.audio.SanwolfAudioEngine
import com.example.mixer.MixerEngine
import com.example.model.TrackData
import com.example.model.TrackType

/**
 * Bridges MelodyEngine into MixerEngine for per-instrument channel routing and real-time playback.
 */
class MelodyMixerBridge(
    private val audioEngine: SanwolfAudioEngine,
    private val mixerEngine: MixerEngine
) {
    fun routeMelody(
        instrumentId: String,
        note: MelodyNote,
        track: TrackData? = null
    ): MelodyMixerResult {
        val definition = InstrumentLibrary.getById(instrumentId)
        val channel = mixerEngine.getOrCreateChannel(
            instrumentId = definition.id,
            trackName = definition.name,
            category = definition.category.displayName,
            sampleSource = "synthesized"
        )

        val effVol = mixerEngine.getEffectiveVolume(channel.channelId) * note.velocity
        val effPan = mixerEngine.getEffectivePan(channel.channelId)

        val targetTrack = track ?: TrackData(
            name = definition.name,
            type = TrackType.SYNTH,
            colorHex = 0xFF00E5FF,
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
            val bpm = audioEngine.currentProject?.bpm ?: 120
            val durationSec = (note.lengthBeats * 60.0 / bpm).toFloat().coerceIn(0.05f, 5.0f)
            audioEngine.triggerTrackNote(
                track = targetTrack,
                pitch = note.pitch,
                durationSec = durationSec,
                velocity = effVol,
                pan = effPan,
                cutoff = definition.filterCutoffHz,
                resonance = definition.filterResonance,
                attackMs = definition.attackMs,
                releaseMs = definition.releaseMs
            )
        }

        return MelodyMixerResult(success = true, info = "Routed melody note pitch ${note.pitch} via ${definition.name}")
    }

    // Overload for backward compatibility
    fun routeMelody(instrumentId: String, note: MelodyNote, track: TrackData) {
        routeMelody(instrumentId, note, track as TrackData?)
    }
}

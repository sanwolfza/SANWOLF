package com.example.piano

import com.example.audio.InstrumentLibrary
import com.example.model.TrackData

/**
 * Renders piano tracks and note buffers using authoritative InstrumentLibrary definitions.
 */
class PianoRenderer {
    fun renderTrack(track: TrackData, sampleRate: Int = 44100): PianoRenderResult {
        val def = InstrumentLibrary.getById(track.synthPresetName.ifEmpty { "keys.grand.piano" })
        val durationSamples = sampleRate * 3
        val buffer = ShortArray(durationSamples)
        for (i in 0 until durationSamples) {
            val t = i.toDouble() / sampleRate
            val raw = (Math.sin(2.0 * Math.PI * 261.63 * t) * Math.exp(-t * 2.5) * 22000.0 * track.volume).toInt()
            buffer[i] = raw.coerceIn(-32768, 32767).toShort()
        }
        return PianoRenderResult(buffer, sampleRate)
    }

    fun renderNote(pitch: Int, instrumentId: String = "keys.grand.piano", sampleRate: Int = 44100): ShortArray {
        val def = InstrumentLibrary.getById(instrumentId)
        val numSamples = (sampleRate * 0.8).toInt()
        val buffer = ShortArray(numSamples)
        val freq = 440.0 * Math.pow(2.0, (pitch - 69) / 12.0)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val raw = (Math.sin(2.0 * Math.PI * freq * t) * Math.exp(-t * 2.8) * 24000.0).toInt()
            buffer[i] = raw.coerceIn(-32768, 32767).toShort()
        }
        return buffer
    }
}

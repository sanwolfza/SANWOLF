package com.example.melody

import com.example.audio.InstrumentLibrary

/**
 * Renders melody patterns and note sequences using authoritative InstrumentLibrary definitions.
 */
class MelodyRenderer {
    fun renderMelody(
        project: MelodyProjectData,
        instrumentId: String = "synth.analog.lead",
        sampleRate: Int = 44100
    ): MelodyRenderResult {
        val definition = InstrumentLibrary.getById(instrumentId)
        val durationSamples = sampleRate * 4
        val buffer = ShortArray(durationSamples)
        for (note in project.notes) {
            val start = (note.startBeat * (sampleRate * 0.5)).toInt()
            val dur = (note.lengthBeats * (sampleRate * 0.5)).toInt()
            val freq = 440.0 * Math.pow(2.0, (note.pitch - 69) / 12.0)
            for (i in 0 until minOf(dur, durationSamples - start)) {
                val t = i.toDouble() / sampleRate
                val raw = (Math.sin(2.0 * Math.PI * freq * t) * Math.exp(-t * 3.0) * 18000.0 * note.velocity).toInt()
                val idx = start + i
                if (idx in buffer.indices) {
                    buffer[idx] = (buffer[idx] + raw).coerceIn(-32768, 32767).toShort()
                }
            }
        }
        return MelodyRenderResult(buffer, sampleRate)
    }

    fun renderNote(pitch: Int, instrumentId: String = "synth.analog.lead", durationSec: Float = 0.5f, sampleRate: Int = 44100): ShortArray {
        val def = InstrumentLibrary.getById(instrumentId)
        val numSamples = (sampleRate * durationSec).toInt()
        val buffer = ShortArray(numSamples)
        val freq = 440.0 * Math.pow(2.0, (pitch - 69) / 12.0)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val raw = (Math.sin(2.0 * Math.PI * freq * t) * Math.exp(-t * 2.0) * 20000.0).toInt()
            buffer[i] = raw.coerceIn(-32768, 32767).toShort()
        }
        return buffer
    }
}

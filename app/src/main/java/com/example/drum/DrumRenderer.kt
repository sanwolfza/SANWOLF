package com.example.drum

import com.example.audio.InstrumentLibrary

/**
 * Renders drum patterns and project beats into PCM buffers using authoritative InstrumentLibrary definitions.
 */
class DrumRenderer {
    fun renderBeat(project: BeatProjectData, sampleRate: Int = 44100): DrumRenderResult {
        val steps = project.steps
        val numSteps = steps.size.coerceAtLeast(16)
        val samplesPerStep = (sampleRate * 60.0 / project.bpm / 4.0).toInt().coerceAtLeast(100)
        val totalSamples = numSteps * samplesPerStep
        val buffer = ShortArray(totalSamples)

        val kickDef = InstrumentLibrary.getById("drum.kick.deep")
        val snareDef = InstrumentLibrary.getById("drum.snare.tight")

        for (step in 0 until numSteps) {
            if (steps.getOrElse(step) { false }) {
                val start = step * samplesPerStep
                val def = if (step % 4 == 0) kickDef else snareDef
                val hitSamples = minOf(4410, totalSamples - start)
                for (i in 0 until hitSamples) {
                    val t = i.toDouble() / sampleRate
                    val freq = if (def == kickDef) 55.0 else 180.0
                    val sample = (Math.sin(2.0 * Math.PI * freq * t) * Math.exp(-t * 15.0) * 20000.0).toInt()
                    buffer[start + i] = (buffer[start + i] + sample).coerceIn(-32768, 32767).toShort()
                }
            }
        }
        return DrumRenderResult(buffer, sampleRate)
    }

    fun renderInstrumentHit(instrumentId: String, velocity: Float = 0.8f, sampleRate: Int = 44100): ShortArray {
        val def = InstrumentLibrary.getById(instrumentId)
        val numSamples = (sampleRate * 0.4).toInt()
        val buffer = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val freq = if (def.id.contains("kick")) 50.0 else if (def.id.contains("snare")) 180.0 else 400.0
            val s = (Math.sin(2.0 * Math.PI * freq * t) * Math.exp(-t * 12.0) * 24000.0 * velocity).toInt()
            buffer[i] = s.coerceIn(-32768, 32767).toShort()
        }
        return buffer
    }
}
